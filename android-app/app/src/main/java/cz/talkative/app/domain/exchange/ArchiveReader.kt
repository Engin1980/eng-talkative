package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.audio.AudioLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** Odkud se archiv čte; [open] jde volat opakovaně, pokaždé vrací nový proud od začátku. */
interface ArchiveSource {
    fun open(): InputStream
}

/** Vyrobí zdroj ze souboru, který uživatel vybral (odkaz [uri] dává systémový výběr souboru). */
fun interface ArchiveSourceFactory {
    fun forUri(uri: String): ArchiveSource
}

/** Proč se import nepodařil. Při žádné z chyb se nezměnila data. */
enum class ImportError {
    /** Není to archiv exportovaný touto aplikací (nebo je poškozený ZIP). */
    NOT_AN_ARCHIVE,

    /** Archiv vytvořila novější verze aplikace. */
    NEWER_VERSION,

    /** Archiv je poškozený: odkazy si odporují, chybí soubor média, médium nejde přečíst nebo je příliš velké. */
    INVALID_ARCHIVE,

    /** Soubor se nepodařilo přečíst. */
    UNREADABLE,

    /** Na zařízení není dost místa. */
    STORAGE_FULL,

    /** Cílová složka mezitím zmizela. */
    TARGET_MISSING,

    /** Jiná neočekávaná chyba. */
    FAILED,
}

sealed interface ReadResult {
    data class Success(val manifest: ArchiveManifest) : ReadResult

    data class Failure(val error: ImportError) : ReadResult
}

/** Meze velikosti položek archivu; chrání před archivem, který by po rozbalení zabral celé úložiště. */
object ArchiveLimits {
    const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    const val MAX_AUDIO_BYTES = AudioLimits.MAX_BYTES
    const val MAX_ENTRIES = 50_000

    /** Největší povolená velikost položky [entryName], nebo `null`, když položka do archivu nepatří. */
    fun maxBytes(entryName: String): Long? = when {
        entryName == ArchiveLayout.MANIFEST -> MAX_MANIFEST_BYTES
        entryName.startsWith(ArchiveLayout.IMAGES_DIR) -> MAX_IMAGE_BYTES
        entryName.startsWith(ArchiveLayout.AUDIO_DIR) -> MAX_AUDIO_BYTES
        else -> null
    }
}

/**
 * Přečte a zkontroluje archiv, aniž by cokoli zapsal: manifest, verzi schématu, vazby uvnitř manifestu a to, že každé
 * médium, na které slovíčka odkazují, je v archivu a nepřesahuje povolenou velikost.
 */
class ArchiveReader @Inject constructor() {

    suspend fun read(source: ArchiveSource): ReadResult = withContext(Dispatchers.IO) {
        try {
            scan(source)
        } catch (e: ZipException) {
            ReadResult.Failure(ImportError.NOT_AN_ARCHIVE)
        } catch (e: IOException) {
            ReadResult.Failure(ImportError.UNREADABLE)
        } catch (e: SecurityException) {
            ReadResult.Failure(ImportError.UNREADABLE)
        }
    }

    private fun scan(source: ArchiveSource): ReadResult {
        var manifestText: String? = null
        val entrySizes = mutableMapOf<String, Long>()
        ZipInputStream(source.open()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > ArchiveLimits.MAX_ENTRIES) return ReadResult.Failure(ImportError.INVALID_ARCHIVE)
                val limit = ArchiveLimits.maxBytes(entry.name)
                if (entry.isDirectory || limit == null) continue
                val bytes = readLimited(zip, limit) ?: return ReadResult.Failure(ImportError.INVALID_ARCHIVE)
                if (entry.name == ArchiveLayout.MANIFEST) manifestText = bytes.toString(Charsets.UTF_8) else entrySizes[entry.name] = bytes.size.toLong()
            }
        }

        val text = manifestText ?: return ReadResult.Failure(ImportError.NOT_AN_ARCHIVE)
        val manifest = when (val decoded = ManifestCodec.decode(text)) {
            is ManifestResult.Success -> decoded.manifest
            is ManifestResult.Failure -> return ReadResult.Failure(
                when (decoded.error) {
                    ArchiveError.NOT_AN_ARCHIVE -> ImportError.NOT_AN_ARCHIVE
                    ArchiveError.NEWER_VERSION -> ImportError.NEWER_VERSION
                    ArchiveError.INVALID_VERSION, ArchiveError.INCONSISTENT -> ImportError.INVALID_ARCHIVE
                },
            )
        }
        val allPresent = manifest.words.all { word ->
            (word.imageFile == null || (ArchiveLayout.IMAGES_DIR + word.imageFile) in entrySizes) &&
                (word.audioFile == null || (ArchiveLayout.AUDIO_DIR + word.audioFile) in entrySizes)
        }
        return if (allPresent) ReadResult.Success(manifest) else ReadResult.Failure(ImportError.INVALID_ARCHIVE)
    }
}

/** Přečte celou položku, nebo vrátí `null`, když je větší než [limit]. */
internal fun readLimited(input: InputStream, limit: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
