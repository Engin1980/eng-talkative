package cz.talkative.app.data.pictogram

import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.domain.pictogram.PictogramSearchResult
import cz.talkative.app.domain.pictogram.PictogramUrls
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ArasaacPictogramSearchTest {

    private val server = MockWebServer()
    private lateinit var search: ArasaacPictogramSearch

    @Before
    fun setUp() {
        server.start()
        search = searchWith(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun searchWith(client: OkHttpClient) =
        ArasaacPictogramSearch(client, ArasaacEndpoints(apiBase = server.url("/").toString(), staticBase = "http://static.test"))

    private fun run(text: String) = runBlocking { search.search(text) }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun failure(text: String = "dům") = (run(text) as PictogramSearchResult.Failed).error

    private fun found(text: String = "dům") = (run(text) as PictogramSearchResult.Found).pictograms

    @Test
    fun success_returnsIdsTitlesAndPreviewUrls() {
        enqueue(
            """[{"_id":6964,"keywords":[{"keyword":"dům","plural":"domy"},{"keyword":"domov"}],"tags":["place"]},
                {"_id":2317,"keywords":[{"keyword":"rodinný dům"}],"desc":"neznámé pole"}]""",
        )

        val result = found()

        assertEquals(
            listOf(
                Pictogram(6964, "dům", "http://static.test/pictograms/6964/6964_300.png"),
                Pictogram(2317, "rodinný dům", "http://static.test/pictograms/2317/2317_300.png"),
            ),
            result,
        )
    }

    @Test
    fun query_isSentAsOnePathSegment_withDiacriticsEncoded() {
        enqueue("[]")

        run("dům")

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/v1/pictograms/cs/search/d%C5%AFm", request.path)
    }

    @Test
    fun query_withSpacesAndSlash_cannotChangeThePath() {
        enqueue("[]")

        run("  rodinný dům / auto?x=1 ")

        val path = server.takeRequest(1, TimeUnit.SECONDS)!!.path!!
        assertEquals("/v1/pictograms/cs/search/rodinn%C3%BD%20d%C5%AFm%20%2F%20auto%3Fx=1", path)
    }

    @Test
    fun emptyOrBlankQuery_isNotSearched() {
        assertEquals(emptyList<Pictogram>(), found(""))
        assertEquals(emptyList<Pictogram>(), found("   "))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptyArray_meansNothingFound() {
        enqueue("[]")

        assertEquals(emptyList<Pictogram>(), found())
    }

    @Test
    fun http404_isNothingFound_becauseThatIsHowArasaacAnswers() {
        enqueue("[]", code = 404)

        assertEquals(emptyList<Pictogram>(), found("xqzvwkjh"))
    }

    @Test
    fun itemsWithoutKeywordOrValidId_areSkipped() {
        enqueue(
            """[{"_id":1,"keywords":[]},
                {"_id":0,"keywords":[{"keyword":"nula"}]},
                {"_id":"abc","keywords":[{"keyword":"text"}]},
                {"keywords":[{"keyword":"bez id"}]},
                {"_id":2,"keywords":[{"keyword":"  "},{"keyword":"druhé slovo"}]},
                "nesmysl",
                {"_id":3,"keywords":[{"keyword":"ok"}]}]""",
        )

        val result = found()

        assertEquals(listOf("druhé slovo", "ok"), result.map { it.title })
        assertEquals(listOf(2, 3), result.map { it.id })
    }

    @Test
    fun resultsAreLimited() {
        val items = (1..250).joinToString(",", "[", "]") { """{"_id":$it,"keywords":[{"keyword":"slovo$it"}]}""" }
        enqueue(items)

        val result = found()

        assertEquals(ArasaacPictogramSearch.MAX_RESULTS, result.size)
        assertEquals(1, result.first().id)
    }

    @Test
    fun brokenJson_isBadResponse() {
        enqueue("""[{"_id":1,"keywords":[""")
        assertEquals(PictogramError.BAD_RESPONSE, failure())
    }

    @Test
    fun jsonThatIsNotAnArray_isBadResponse() {
        enqueue("""{"error":"neco"}""")
        assertEquals(PictogramError.BAD_RESPONSE, failure())

        enqueue("<html>Bad gateway</html>")
        assertEquals(PictogramError.BAD_RESPONSE, failure())
    }

    @Test
    fun http500_isServerError() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(PictogramError.SERVER_ERROR, failure())

        server.enqueue(MockResponse().setResponseCode(503).setBody("[]"))
        assertEquals(PictogramError.SERVER_ERROR, failure())
    }

    @Test
    fun unreachableServer_isNoNetwork() {
        val closed = searchWith(OkHttpClient())
        server.shutdown()

        val result = runBlocking { closed.search("dům") }

        assertEquals(PictogramSearchResult.Failed(PictogramError.NO_NETWORK), result)
    }

    @Test
    fun slowServer_isTimeout() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val impatient = searchWith(OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build())

        val result = runBlocking { impatient.search("dům") }

        assertEquals(PictogramSearchResult.Failed(PictogramError.TIMEOUT), result)
    }

    @Test
    fun defaultClient_hasTenSecondLimits() {
        val client = ArasaacPictogramSearch.defaultClient()

        assertEquals(10_000, client.readTimeoutMillis)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.callTimeoutMillis)
    }

    @Test
    fun imageUrl_hasThePreviewAndDownloadForm() {
        assertEquals("https://static.arasaac.org/pictograms/6964/6964_300.png", PictogramUrls.image(6964, PictogramUrls.PREVIEW_SIZE))
        assertEquals("https://static.arasaac.org/pictograms/6964/6964_500.png", PictogramUrls.image(6964, PictogramUrls.DOWNLOAD_SIZE))
        assertEquals("http://x/pictograms/5/5_500.png", PictogramUrls.image(5, 500, "http://x/"))
        assertTrue(PictogramUrls.PREVIEW_SIZE < PictogramUrls.DOWNLOAD_SIZE)
    }
}
