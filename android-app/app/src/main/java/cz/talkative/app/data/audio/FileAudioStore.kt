package cz.talkative.app.data.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioLimits
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** MP3 v `filesDir/audio`. Kontroluje se dočasná kopie, teprve potom se přejmenuje; odmítnutý soubor nezůstane. */
class FileAudioStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioStore {

    private val directory = File(context.filesDir, DIRECTORY_NAME)

    override suspend fun saveFromUri(uri: String): AudioSaveResult = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val name = UUID.randomUUID().toString() + ".mp3"
        val temp = File(directory, "$name.tmp")
        try {
            if (!copyWithinLimit(Uri.parse(uri), temp)) return@withContext AudioSaveResult.Failed(AudioError.TOO_LARGE)
            val (mimeType, durationMillis) = readMetadata(temp)
                ?: return@withContext AudioSaveResult.Failed(AudioError.UNREADABLE)
            AudioLimits.check(mimeType, durationMillis)?.let { return@withContext AudioSaveResult.Failed(it) }
            if (!temp.renameTo(File(directory, name))) throw IOException("Soubor nelze přejmenovat")
            AudioSaveResult.Saved(name)
        } catch (e: IOException) {
            AudioSaveResult.Failed(if (isStorageFull(e)) AudioError.STORAGE_FULL else AudioError.UNREADABLE)
        } catch (e: SecurityException) {
            AudioSaveResult.Failed(AudioError.UNREADABLE)
        } finally {
            temp.delete() // po úspěšném přejmenování už neexistuje
        }
    }

    override fun resolve(fileName: String): File? =
        safeFile(fileName)?.takeIf { it.isFile }

    override suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { safeFile(fileName)?.delete() }
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        withContext(Dispatchers.IO) {
            directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    /** Zkopíruje [source] do [target]; vrátí `false`, když je zdroj větší než povolená velikost (kopie se pak zahodí). */
    private fun copyWithinLimit(source: Uri, target: File): Boolean {
        val input = context.contentResolver.openInputStream(source) ?: throw IOException("Nelze otevřít $source")
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > AudioLimits.MAX_BYTES) return false
                    out.write(buffer, 0, read)
                }
            }
        }
        return true
    }

    /** MIME typ a délka zvuku, nebo `null`, když soubor systém nedokáže přečíst. */
    private fun readMetadata(file: File): Pair<String?, Long?>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) to
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) {
            null // poškozený soubor nebo ne zvuk
        } finally {
            retriever.release()
        }
    }

    /** Název souboru bez cesty; cokoli s lomítkem nebo `..` se odmítne. */
    private fun safeFile(fileName: String): File? =
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName == "..") null
        else File(directory, fileName)

    private fun isStorageFull(e: IOException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.any {
            (it is ErrnoException && it.errno == OsConstants.ENOSPC) || it.message?.contains("ENOSPC") == true
        }

    private companion object {
        const val DIRECTORY_NAME = "audio"
        const val BUFFER_SIZE = 16 * 1024
    }
}
