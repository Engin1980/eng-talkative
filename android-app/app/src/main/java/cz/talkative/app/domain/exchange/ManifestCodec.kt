package cz.talkative.app.domain.exchange

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Proč se manifest nepodařilo přečíst. Žádná z chyb nic nezměnila, čtení je jen v paměti. */
enum class ArchiveError {
    /** Není to JSON v očekávaném tvaru, tedy nejspíš cizí soubor nebo poškozený archiv. */
    NOT_AN_ARCHIVE,

    /** Archiv vytvořila novější verze aplikace. */
    NEWER_VERSION,

    /** Verze schématu je neplatná (menší než 1). */
    INVALID_VERSION,

    /** Odkazy v manifestu si odporují: duplicitní ID, neexistující rodič, cyklus, prázdný titulek, nebezpečný název souboru. */
    INCONSISTENT,
}

sealed interface ManifestResult {
    data class Success(val manifest: ArchiveManifest) : ManifestResult

    data class Failure(val error: ArchiveError) : ManifestResult
}

/** Převod [ArchiveManifest] na JSON a zpět, včetně kontroly, že načtený manifest drží pohromadě. */
object ManifestCodec {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false // chybějící hodnota = null, soubor je kratší
        ignoreUnknownKeys = true // nová pole stejné verze schématu starší aplikaci nevadí
        coerceInputValues = true // neznámá hodnota výčtu se nahradí výchozí
    }

    fun encode(manifest: ArchiveManifest): String = json.encodeToString(ArchiveManifest.serializer(), manifest)

    fun decode(text: String): ManifestResult {
        val manifest = try {
            json.decodeFromString(ArchiveManifest.serializer(), text)
        } catch (e: SerializationException) {
            return ManifestResult.Failure(ArchiveError.NOT_AN_ARCHIVE)
        } catch (e: IllegalArgumentException) {
            return ManifestResult.Failure(ArchiveError.NOT_AN_ARCHIVE)
        }
        return when {
            manifest.schemaVersion > ARCHIVE_SCHEMA_VERSION -> ManifestResult.Failure(ArchiveError.NEWER_VERSION)
            manifest.schemaVersion < 1 -> ManifestResult.Failure(ArchiveError.INVALID_VERSION)
            !isConsistent(manifest) -> ManifestResult.Failure(ArchiveError.INCONSISTENT)
            else -> ManifestResult.Success(manifest)
        }
    }

    private fun isConsistent(manifest: ArchiveManifest): Boolean {
        val ids = manifest.folders.map { it.id } + manifest.words.map { it.id }
        if (ids.any { it.isBlank() } || ids.toSet().size != ids.size) return false
        if ((manifest.folders.map { it.title } + manifest.words.map { it.title }).any { it.isBlank() }) return false

        val folderParents = manifest.folders.associate { it.id to it.parentId }
        val parents = manifest.folders.mapNotNull { it.parentId } + manifest.words.mapNotNull { it.parentId }
        if (parents.any { it !in folderParents }) return false
        if (manifest.folders.any { hasCycle(it.id, folderParents) }) return false

        val files = manifest.words.flatMap { listOfNotNull(it.imageFile, it.audioFile) }
        return files.all { isSafeFileName(it) }
    }

    private fun hasCycle(start: String, parents: Map<String, String?>): Boolean {
        val seen = mutableSetOf<String>()
        var id: String? = start
        while (id != null) {
            if (!seen.add(id)) return true
            id = parents[id]
        }
        return false
    }

    /** Název souboru bez cesty; chrání před zápisem mimo adresář aplikace (zip slip). */
    fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
}
