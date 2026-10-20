package cz.talkative.app.data.pictogram

import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class OkHttpPictogramDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var cacheDir: File
    private lateinit var downloader: OkHttpPictogramDownloader

    @Before
    fun setUp() {
        server.start()
        cacheDir = temp.newFolder("cache")
        downloader = downloaderWith(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun downloaderWith(client: OkHttpClient) =
        OkHttpPictogramDownloader(client, cacheDir, ArasaacEndpoints(staticBase = server.url("/").toString()))

    private val pictogramsDir get() = File(cacheDir, "pictograms")

    private fun filesLeft() = pictogramsDir.listFiles().orEmpty().map { it.name }

    private fun download(id: Int = 6964) = runBlocking { downloader.download(id) }

    private fun png(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    private fun enqueueBytes(bytes: ByteArray, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(Buffer().write(bytes)))
    }

    @Test
    fun success_savesTheFileUnchanged_inCachePictograms() {
        val bytes = png(5_000)
        enqueueBytes(bytes)

        val result = download() as PictogramDownloadResult.Downloaded

        assertEquals("pictogram-6964.png", result.file.name)
        assertEquals(pictogramsDir, result.file.parentFile)
        assertArrayEquals(bytes, result.file.readBytes()) // žádná konverze, průhlednost zůstává
        assertEquals(listOf("pictogram-6964.png"), filesLeft())
    }

    @Test
    fun requestsTheFiveHundredPixelVersion() {
        enqueueBytes(png(10))

        download(2317)

        assertEquals("/pictograms/2317/2317_500.png", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun newDownload_removesThePreviousFile() {
        enqueueBytes(png(10))
        enqueueBytes(png(20))

        download(1)
        download(2)

        assertEquals(listOf("pictogram-2.png"), filesLeft())
    }

    @Test
    fun httpError_leavesNothingBehind() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(PictogramDownloadResult.Failed(PictogramError.SERVER_ERROR), download())

        server.enqueue(MockResponse().setResponseCode(404).setBody("nenalezeno"))
        assertEquals(PictogramDownloadResult.Failed(PictogramError.SERVER_ERROR), download())

        assertFalse(pictogramsDir.exists() && filesLeft().isNotEmpty())
    }

    @Test
    fun responseLargerThanTheLimit_isRejected_andNothingIsLeft() {
        enqueueBytes(ByteArray((OkHttpPictogramDownloader.MAX_BYTES + 1).toInt()))

        assertEquals(PictogramDownloadResult.Failed(PictogramError.BAD_RESPONSE), download())
        assertTrue(filesLeft().isEmpty())
    }

    @Test
    fun oversizedChunkedResponse_withoutLength_isRejectedWhileReading() {
        val body = Buffer().write(ByteArray((OkHttpPictogramDownloader.MAX_BYTES + 100).toInt()))
        server.enqueue(MockResponse().setChunkedBody(body, 8 * 1024))

        assertEquals(PictogramDownloadResult.Failed(PictogramError.BAD_RESPONSE), download())
        assertTrue(filesLeft().isEmpty())
    }

    @Test
    fun connectionDroppedHalfWay_leavesNoPartialFile() {
        server.enqueue(
            MockResponse().setBody(Buffer().write(png(100_000))).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = download()

        assertTrue(result is PictogramDownloadResult.Failed)
        assertTrue(filesLeft().isEmpty())
    }

    @Test
    fun unreachableServer_isNoNetwork_andLeavesNothing() {
        val closed = downloaderWith(OkHttpClient())
        server.shutdown()

        val result = runBlocking { closed.download(1) }

        assertEquals(PictogramDownloadResult.Failed(PictogramError.NO_NETWORK), result)
        assertTrue(filesLeft().isEmpty())
    }

    @Test
    fun slowServer_isTimeout() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val impatient = downloaderWith(OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build())

        val result = runBlocking { impatient.download(1) }

        assertEquals(PictogramDownloadResult.Failed(PictogramError.TIMEOUT), result)
        assertTrue(filesLeft().isEmpty())
    }
}
