package cz.talkative.app.data.pictogram

import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramDownloader
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.domain.pictogram.PictogramUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Stáhne `{id}_500.png` do `cache/pictograms`. V adresáři je vždy nejvýše jeden soubor (starší se před stažením smažou),
 * zbytek uklízí start aplikace. Odpověď nad [MAX_BYTES] se odmítne, aby podvržený server nezaplnil úložiště.
 */
class OkHttpPictogramDownloader(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val endpoints: ArasaacEndpoints = ArasaacEndpoints(),
) : PictogramDownloader {

    private val directory get() = File(cacheDir, DIRECTORY_NAME)

    override suspend fun download(id: Int): PictogramDownloadResult = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, "pictogram-$id.png")
        val temp = File(directory, "pictogram-$id.png.tmp")
        try {
            val url = PictogramUrls.image(id, PictogramUrls.DOWNLOAD_SIZE, endpoints.staticBase)
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext PictogramDownloadResult.Failed(PictogramError.SERVER_ERROR)
                val body = response.body ?: return@withContext PictogramDownloadResult.Failed(PictogramError.BAD_RESPONSE)
                if (body.contentLength() > MAX_BYTES) return@withContext PictogramDownloadResult.Failed(PictogramError.BAD_RESPONSE)
                if (!copyWithinLimit(body.byteStream(), temp)) return@withContext PictogramDownloadResult.Failed(PictogramError.BAD_RESPONSE)
            }
            if (!temp.renameTo(target)) throw IOException("Soubor nelze přejmenovat")
            PictogramDownloadResult.Downloaded(target)
        } catch (e: SocketTimeoutException) {
            PictogramDownloadResult.Failed(PictogramError.TIMEOUT)
        } catch (e: InterruptedIOException) {
            PictogramDownloadResult.Failed(PictogramError.TIMEOUT)
        } catch (e: IOException) {
            PictogramDownloadResult.Failed(PictogramError.NO_NETWORK)
        } finally {
            temp.delete() // po úspěšném přejmenování už neexistuje
            if (!target.exists()) directory.delete() // prázdný adresář po neúspěchu nezůstane
        }
    }

    /** Zkopíruje [input] do [target]; vrátí `false`, když je dat víc než [MAX_BYTES] (kopie se pak zahodí). */
    private fun copyWithinLimit(input: java.io.InputStream, target: File): Boolean {
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return false
                    out.write(buffer, 0, read)
                }
            }
        }
        return true
    }

    companion object {
        const val DIRECTORY_NAME = "pictograms"
        const val MAX_BYTES = 2L * 1024 * 1024
        private const val BUFFER_SIZE = 16 * 1024
    }
}
