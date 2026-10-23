package cz.talkative.app.data.sync

import cz.talkative.app.domain.sync.AccessTokenProvider
import cz.talkative.app.domain.sync.MediaKind
import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.SyncCodec
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncException
import cz.talkative.app.domain.sync.SyncItem
import cz.talkative.app.domain.sync.SyncItemKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class DriveRemoteStoreTest {

    private val server = MockWebServer()
    private val tokenCalls = mutableListOf<Boolean>()
    private var tokenFailure: SyncException? = null
    private val tokens = object : AccessTokenProvider {
        override suspend fun accessToken(forceRefresh: Boolean): String {
            tokenCalls += forceRefresh
            tokenFailure?.let { throw it }
            return if (forceRefresh) "novy-token" else "token"
        }
    }
    private lateinit var store: DriveRemoteStore

    @Before
    fun setUp() {
        server.start()
        store = DriveRemoteStore(
            client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
            tokens = tokens,
            endpoints = DriveEndpoints(apiBase = server.url("/drive/v3").toString().trimEnd('/'), uploadBase = server.url("/upload/drive/v3").toString().trimEnd('/')),
            nowMillis = { 10L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun listing(vararg files: Triple<String, String, String>, next: String? = null): MockResponse {
        val items = files.joinToString(",") { (id, name, time) -> """{"id":"$id","name":"$name","modifiedTime":"$time"}""" }
        val token = if (next != null) ""","nextPageToken":"$next"""" else ""
        return json("""{"files":[$items]$token}""")
    }

    private fun item(id: String, title: String, updatedAt: Long) =
        SyncItem(id, SyncItemKind.WORD, "default", title, null, 0, updatedAt)

    private fun take(): RecordedRequest = server.takeRequest(2, TimeUnit.SECONDS)!!

    private fun failure(block: () -> Unit): SyncError {
        try {
            block()
        } catch (e: SyncException) {
            return e.error
        }
        fail("Mělo selhat")
        error("nedosažitelné")
    }

    // ---- metadata

    @Test
    fun readMetadata_withoutFile_isNull_andQueriesTheAppDataFolderWithBearerToken() {
        server.enqueue(listing())

        assertNull(runBlocking { store.readMetadata() })

        val request = take()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("appDataFolder", request.requestUrl!!.queryParameter("spaces"))
        assertTrue(request.requestUrl!!.queryParameter("q")!!.contains("name = 'dictionary.json'"))
    }

    @Test
    fun readMetadata_returnsTheFileContent() {
        val text = SyncCodec.encode(listOf(item("a", "Čaj", 5)))
        server.enqueue(listing(Triple("id1", "dictionary.json", "2026-10-01T10:00:00.000Z")))
        server.enqueue(MockResponse().setBody(text))

        assertEquals(text, runBlocking { store.readMetadata() })

        take()
        val download = take()
        assertEquals("/drive/v3/files/id1", download.requestUrl!!.encodedPath)
        assertEquals("media", download.requestUrl!!.queryParameter("alt"))
    }

    @Test
    fun readMetadata_withDuplicateFiles_mergesThemIntoOne() {
        server.enqueue(listing(Triple("id1", "dictionary.json", "2026-10-01T10:00:00Z"), Triple("id2", "dictionary.json", "2026-10-02T10:00:00Z")))
        server.enqueue(MockResponse().setBody(SyncCodec.encode(listOf(item("b", "z druhého", 7)))))
        server.enqueue(MockResponse().setBody(SyncCodec.encode(listOf(item("a", "z prvního", 5)))))

        val merged = SyncCodec.decode(runBlocking { store.readMetadata() }!!)

        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun writeMetadata_withoutFile_createsItInTheAppDataFolder() {
        server.enqueue(listing())
        server.enqueue(json("""{"id":"novy"}"""))

        runBlocking { store.writeMetadata("""{"schemaVersion":1,"items":[]}""") }

        take()
        val create = take()
        assertEquals("POST", create.method)
        assertEquals("/upload/drive/v3/files", create.requestUrl!!.encodedPath)
        assertEquals("multipart", create.requestUrl!!.queryParameter("uploadType"))
        val body = create.body.readUtf8()
        assertTrue(body.contains(""""name":"dictionary.json""""))
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains("""{"schemaVersion":1,"items":[]}"""))
        assertTrue(create.getHeader("Content-Type")!!.startsWith("multipart/related"))
    }

    @Test
    fun writeMetadata_withExistingFiles_updatesTheNewestAndDeletesTheRest() {
        server.enqueue(listing(Triple("stary", "dictionary.json", "2026-10-01T10:00:00Z"), Triple("novy", "dictionary.json", "2026-10-05T10:00:00Z")))
        server.enqueue(json("""{"id":"novy"}"""))
        server.enqueue(MockResponse().setResponseCode(204))

        runBlocking { store.writeMetadata("OBSAH") }

        take()
        val update = take()
        assertEquals("PATCH", update.method)
        assertEquals("/upload/drive/v3/files/novy", update.requestUrl!!.encodedPath)
        assertEquals("OBSAH", update.body.readUtf8())
        val delete = take()
        assertEquals("DELETE", delete.method)
        assertEquals("/drive/v3/files/stary", delete.requestUrl!!.encodedPath)
    }

    // ---- média

    @Test
    fun listMedia_followsPages_andIgnoresFilesThatAreNotMedia() {
        server.enqueue(listing(Triple("1", "img_a.png", "2026-10-01T10:00:00Z"), Triple("2", "dictionary.json", "2026-10-01T10:00:00Z"), next = "strana2"))
        server.enqueue(listing(Triple("3", "aud_b.m4a", "2026-10-02T00:00:00Z")))

        val media = runBlocking { store.listMedia() }

        assertEquals(listOf(MediaRef(MediaKind.IMAGE, "a.png"), MediaRef(MediaKind.AUDIO, "b.m4a")), media.map { it.ref })
        assertEquals(java.time.Instant.parse("2026-10-02T00:00:00Z").toEpochMilli(), media[1].modifiedMillis)
        take()
        assertEquals("strana2", take().requestUrl!!.queryParameter("pageToken"))
    }

    @Test
    fun uploadMedia_sendsNameParentAndContent() {
        server.enqueue(json("""{"id":"x"}"""))
        val file = File.createTempFile("obrazek", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        runBlocking { store.uploadMedia(MediaRef(MediaKind.IMAGE, "abc.png"), file) }
        file.delete()

        val request = take()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""name":"img_abc.png""""))
        assertTrue(body.contains(""""parents":["appDataFolder"]"""))
        assertTrue(body.contains("Content-Type: image/png"))
    }

    @Test
    fun downloadMedia_writesTheFile() {
        server.enqueue(listing(Triple("fid", "aud_x.m4a", "2026-10-01T10:00:00Z")))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(9, 8, 7))))
        val target = File.createTempFile("stazeno", ".m4a")

        runBlocking { store.downloadMedia(MediaRef(MediaKind.AUDIO, "x.m4a"), target) }

        assertEquals(listOf<Byte>(9, 8, 7), target.readBytes().toList())
        target.delete()
    }

    @Test
    fun downloadMedia_missingFile_isBadResponse_andFailedDownloadLeavesNoFile() {
        server.enqueue(listing())
        val target = File.createTempFile("stazeno", ".png")
        target.delete()
        assertEquals(SyncError.BAD_RESPONSE, failure { runBlocking { store.downloadMedia(MediaRef(MediaKind.IMAGE, "x.png"), target) } })
        assertFalse(target.exists())

        server.enqueue(listing(Triple("fid", "img_x.png", "2026-10-01T10:00:00Z")))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(SyncError.SERVER_ERROR, failure { runBlocking { store.downloadMedia(MediaRef(MediaKind.IMAGE, "x.png"), target) } })
        assertFalse(target.exists())
    }

    @Test
    fun deleteMedia_ofMissingFile_isFine_andDeletesById() {
        server.enqueue(listing(Triple("fid", "img_x.png", "2026-10-01T10:00:00Z")))
        server.enqueue(MockResponse().setResponseCode(404))

        runBlocking { store.deleteMedia(MediaRef(MediaKind.IMAGE, "x.png")) }

        take()
        val delete = take()
        assertEquals("DELETE", delete.method)
        assertEquals("/drive/v3/files/fid", delete.requestUrl!!.encodedPath)
    }

    // ---- chyby

    @Test
    fun unauthorized_refreshesTheTokenOnceAndRetries() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(listing())

        assertNull(runBlocking { store.readMetadata() })

        assertEquals(listOf(false, true), tokenCalls)
        assertEquals("Bearer token", take().getHeader("Authorization"))
        assertEquals("Bearer novy-token", take().getHeader("Authorization"))
    }

    @Test
    fun unauthorizedTwice_isReported() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(SyncError.UNAUTHORIZED, failure { runBlocking { store.readMetadata() } })
    }

    @Test
    fun serverErrorsAndQuota_areServerError() {
        for (code in listOf(500, 503, 403, 429)) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertEquals("HTTP $code", SyncError.SERVER_ERROR, failure { runBlocking { store.readMetadata() } })
        }
    }

    @Test
    fun brokenListing_isBadResponse() {
        server.enqueue(json("není json"))
        assertEquals(SyncError.BAD_RESPONSE, failure { runBlocking { store.readMetadata() } })
    }

    @Test
    fun unreachableServer_isNoNetwork() {
        server.shutdown()
        assertEquals(SyncError.NO_NETWORK, failure { runBlocking { store.readMetadata() } })
    }

    @Test
    fun tokenProblems_arePassedThrough_withoutCallingTheServer() {
        tokenFailure = SyncException(SyncError.NEEDS_CONSENT)

        assertEquals(SyncError.NEEDS_CONSENT, failure { runBlocking { store.readMetadata() } })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mediaNames_roundTrip() {
        val image = MediaRef(MediaKind.IMAGE, "u.png")
        val audio = MediaRef(MediaKind.AUDIO, "u.m4a")
        assertEquals(image, DriveRemoteStore.parseMediaName(DriveRemoteStore.driveName(image)))
        assertEquals(audio, DriveRemoteStore.parseMediaName(DriveRemoteStore.driveName(audio)))
        assertNull(DriveRemoteStore.parseMediaName("dictionary.json"))
        assertNull(DriveRemoteStore.parseMediaName("img_"))
    }
}
