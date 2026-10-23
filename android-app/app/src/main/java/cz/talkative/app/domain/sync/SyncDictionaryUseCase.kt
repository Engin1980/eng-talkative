package cz.talkative.app.domain.sync

import cz.talkative.app.domain.util.Clock
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Co synchronizace udělala; [missingMedia] jsou soubory, na které se odkazují slovíčka, ale nejsou nikde (řeší krok 10). */
data class SyncOutcome(
    val itemsChangedLocally: Int,
    val metadataUploaded: Boolean,
    val mediaUploaded: Int,
    val mediaDownloaded: Int,
    val missingMedia: Int,
)

/**
 * Jedna synchronizace: stáhnout vzdálená metadata, sloučit s místními (last write wins), přenést média, zapsat
 * sloučený stav na obě strany. Pořadí kroků je zvolené tak, aby výpadek sítě kdykoli před posledním krokem nechal
 * místní data beze změny:
 * 1. stáhnou se chybějící média do dočasné složky,
 * 2. nahrají se média, která chybí na Drive,
 * 3. zapíšou se sloučená metadata na Drive,
 * 4. teprve pak se média přesunou do úložišť a v jedné transakci zapíše databáze.
 * Opakování po chybě je bezpečné, protože slučování je idempotentní.
 */
class SyncDictionaryUseCase @Inject constructor(
    private val local: SyncLocalStore,
    private val media: SyncMediaStore,
    private val remote: RemoteStore,
    private val clock: Clock,
    private val staging: SyncStagingDir,
) {
    suspend operator fun invoke(): SyncOutcome {
        val directory = staging.directory
        directory.deleteRecursively()
        directory.mkdirs()
        try {
            return synchronize(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun synchronize(directory: File): SyncOutcome {
        val now = clock.nowMillis()
        val localItems = local.readAll()
        val remoteItems = remote.readMetadata()?.let { SyncCodec.decode(it) }.orEmpty()
        val result = SyncMerge.merge(localItems, remoteItems, now)

        val referenced = referencedMedia(result.merged)
        val remoteMedia = remote.listMedia()
        val localMedia = referenced.filter { media.file(it) != null }.toSet()
        val plan = SyncMerge.planMedia(referenced, localMedia, remoteMedia.map { it.ref }.toSet())

        // 1. stažení do dočasné složky: dokud nejsou všechna, místní data se nemění
        val downloaded = linkedMapOf<MediaRef, File>()
        for (ref in plan.toDownload) {
            val target = File(directory, "${ref.kind}-${ref.name}")
            remote.downloadMedia(ref, target)
            downloaded[ref] = target
        }
        // 2. nahrání médií před metadaty: jiné zařízení pak nikdy neuvidí odkaz na soubor, který na Drive ještě není
        for (ref in plan.toUpload) {
            val file = media.file(ref) ?: continue
            remote.uploadMedia(ref, file)
        }
        // 3. sloučená metadata na Drive
        if (result.remoteOutdated) remote.writeMetadata(SyncCodec.encode(result.merged))

        // 4. místní změny
        applyLocally(result, downloaded)
        // Smažou se jen soubory, které tahle synchronizace opustila. Plošný úklid osiřelých souborů by smazal i obrázek,
        // zvuk nebo kresbu, kterou administrátor právě vybral v otevřené editaci a ještě neuložil.
        val stillReferenced = referencedMedia(local.readAll())
        for (ref in referencedMedia(localItems) - stillReferenced) media.delete(ref)

        // úklid nepoužitých médií na Drive je jen doplněk: jeho selhání synchronizaci nepokazí
        for (ref in SyncMerge.remoteGarbage(remoteMedia, referenced, now)) {
            try {
                remote.deleteMedia(ref)
            } catch (e: SyncException) {
                break
            }
        }

        return SyncOutcome(
            itemsChangedLocally = result.changedLocally.size,
            metadataUploaded = result.remoteOutdated,
            mediaUploaded = plan.toUpload.size,
            mediaDownloaded = plan.toDownload.size,
            missingMedia = plan.missing.size,
        )
    }

    private suspend fun applyLocally(result: MergeResult, downloaded: Map<MediaRef, File>) {
        val restored = mutableListOf<MediaRef>()
        try {
            for ((ref, file) in downloaded) {
                if (!media.restore(ref, file)) throw SyncException(SyncError.LOCAL_STORAGE, "Soubor ${ref.name} nelze uložit")
                restored += ref
            }
            local.apply(result.changedLocally, result.purgeIds)
        } catch (e: SyncException) {
            restored.forEach { media.delete(it) }
            throw e
        } catch (e: IOException) {
            restored.forEach { media.delete(it) }
            throw SyncException(SyncError.LOCAL_STORAGE, "Místní zápis selhal", e)
        }
    }
}

/** Dočasná složka synchronizace (`cache/sync`), jejíž obsah se po každém běhu i při startu aplikace maže. */
class SyncStagingDir(val directory: File)
