package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.image.ImageStore
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.repository.DictionaryRepository
import cz.talkative.app.domain.repository.DictionarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/** Kam se archiv zapisuje (v aplikaci soubor vybraný přes systémový výběr, v testech paměť). */
interface ExportDestination {
    /** Otevře výstup pro zápis; zavírá ho volající. */
    fun open(): OutputStream

    /** Smaže nedokončený soubor, aby po neúspěšném exportu nezůstal poškozený archiv. Nesmí selhat. */
    suspend fun discard()
}

/** Vyrobí cíl exportu ze souboru, který uživatel vybral (odkaz [uri] dává systémový výběr souboru). */
fun interface ExportDestinationFactory {
    fun forUri(uri: String): ExportDestination
}

enum class ExportError {
    /** Na zařízení není dost místa. */
    STORAGE_FULL,

    /** Cíl nejde otevřít nebo se do něj nepodařilo zapsat. */
    WRITE_FAILED,

    /** Vybraná složka mezitím zmizela. */
    FOLDER_MISSING,
}

sealed interface ExportResult {
    /** [missingMedia] je počet obrázků a MP3, které slovíčka měla, ale soubor chyběl; v archivu bez nich jsou slovíčka bez média. */
    data class Success(val folders: Int, val words: Int, val missingMedia: Int) : ExportResult

    data class Failed(val error: ExportError) : ExportResult
}

/**
 * Zapíše slovník (celý, nebo složku [rootFolderId] i s podstromem) jako ZIP do [ExportDestination].
 * V archivu je `manifest.json`, `LICENSE-ARASAAC.txt` a média. Při jakékoli chybě i při zrušení se cíl smaže, takže po neúspěchu nezůstane poškozený částečný soubor.
 */
class ExportDictionaryUseCase @Inject constructor(
    private val repository: DictionaryRepository,
    private val imageStore: ImageStore,
    private val audioStore: AudioStore,
    private val configRepository: ConfigRepository,
    private val notice: ArchiveNotice,
) {
    /** [onProgress] dostává počet už zapsaných souborů médií a jejich celkový počet. */
    suspend operator fun invoke(
        rootFolderId: String?,
        includeConfig: Boolean,
        destination: ExportDestination,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportResult {
        val (folders, words) = selectForExport(repository.snapshot(), rootFolderId)
            ?: return ExportResult.Failed(ExportError.FOLDER_MISSING)

        var missing = 0
        val media = LinkedHashMap<String, File>() // název položky v ZIPu -> soubor
        val archiveWords = words.map { word ->
            val image = word.imageFile?.let { name -> findFile(imageStore.resolve(name), ArchiveLayout.IMAGES_DIR + name, media) }
            val audio = word.audioFile?.let { name -> findFile(audioStore.resolve(name), ArchiveLayout.AUDIO_DIR + name, media) }
            missing += listOf(word.imageFile to image, word.audioFile to audio).count { (wanted, found) -> wanted != null && found == null }
            ArchiveWord(
                word.id, word.title, word.parentId, word.sortOrder,
                imageFile = image, alternateText = word.alternateText, audioFile = audio,
            )
        }
        val manifest = ArchiveManifest(
            schemaVersion = ARCHIVE_SCHEMA_VERSION,
            folders = folders.map { ArchiveFolder(it.id, it.title, it.parentId, it.sortOrder) },
            words = archiveWords,
            config = if (includeConfig) ArchiveConfig.from(configRepository.config.first()) else null,
        )

        var finished = false
        try {
            withContext(Dispatchers.IO) { write(destination, manifest, media, onProgress) }
            finished = true
            return ExportResult.Success(folders.size, words.size, missing)
        } catch (e: IOException) {
            return ExportResult.Failed(if (isStorageFull(e)) ExportError.STORAGE_FULL else ExportError.WRITE_FAILED)
        } catch (e: SecurityException) {
            return ExportResult.Failed(ExportError.WRITE_FAILED)
        } finally {
            if (!finished) withContext(NonCancellable) { destination.discard() }
        }
    }

    /** Vrátí název souboru pro manifest a zařadí soubor do archivu; `null`, když soubor v úložišti chybí. */
    private fun findFile(file: File?, entryName: String, media: MutableMap<String, File>): String? {
        if (file == null) return null
        media[entryName] = file
        return file.name
    }

    private fun write(
        destination: ExportDestination,
        manifest: ArchiveManifest,
        media: Map<String, File>,
        onProgress: (Int, Int) -> Unit,
    ) {
        ZipOutputStream(destination.open()).use { zip ->
            zip.putNextEntry(ZipEntry(ArchiveLayout.MANIFEST))
            zip.write(ManifestCodec.encode(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // Uvedení autora a licence ARASAAC je v každém exportu (aplikace neeviduje, která slovíčka jsou z ARASAAC).
            zip.putNextEntry(ZipEntry(ArchiveLayout.LICENSE))
            zip.write(notice.arasaacLicenseText().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            onProgress(0, media.size)
            var done = 0
            for ((entryName, file) in media) {
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                onProgress(++done, media.size)
            }
        }
    }
}

/** Složky a slovíčka, které se exportují. */
internal data class ExportSelection(val folders: List<Folder>, val words: List<Word>)

/**
 * Vybere z [snapshot] obsah složky [rootFolderId] i s ní samou (`null` = celý slovník). Vybraná složka se v archivu
 * stane položkou v kořeni. Bere se jen to, co je ze startu dosažitelné stromem, takže osiřelé položky a cykly v poškozených
 * datech archiv nerozbijí. Neexistující složka vrací `null`.
 */
internal fun selectForExport(snapshot: DictionarySnapshot, rootFolderId: String?): ExportSelection? {
    val childFolders = snapshot.folders.groupBy { it.parentId }
    val childWords = snapshot.words.groupBy { it.parentId }
    val folders = mutableListOf<Folder>()
    val words = mutableListOf<Word>()
    val visited = mutableSetOf<String>()

    fun visit(parentId: String?) {
        childFolders[parentId].orEmpty().forEach { folder ->
            if (visited.add(folder.id)) {
                folders += folder
                visit(folder.id)
            }
        }
        words += childWords[parentId].orEmpty()
    }

    if (rootFolderId == null) {
        visit(null)
    } else {
        val root = snapshot.folders.firstOrNull { it.id == rootFolderId } ?: return null
        visited.add(root.id)
        folders += root.copy(parentId = null, sortOrder = 0)
        visit(root.id)
    }
    return ExportSelection(folders, words)
}

internal fun isStorageFull(e: IOException): Boolean =
    generateSequence<Throwable>(e) { it.cause }.any {
        it.message?.contains("ENOSPC") == true || it.message?.contains("No space left", ignoreCase = true) == true
    }
