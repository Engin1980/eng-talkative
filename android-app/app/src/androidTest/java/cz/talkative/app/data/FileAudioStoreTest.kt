package cz.talkative.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioLimits
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.testing.AudioFixtures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileAudioStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: FileAudioStore
    private val audioDir get() = File(context.filesDir, "audio")
    private val sources = mutableListOf<File>()

    @Before
    fun setUp() {
        audioDir.deleteRecursively()
        store = FileAudioStore(context)
    }

    @After
    fun tearDown() {
        audioDir.deleteRecursively()
        sources.forEach { it.delete() }
    }

    private fun source(name: String, bytes: ByteArray): String {
        val (file, uri) = AudioFixtures.writeToCache(context, name, bytes)
        sources += file
        return uri
    }

    private fun save(uri: String) = runBlocking { store.saveFromUri(uri) }

    private fun filesInStore() = audioDir.listFiles()?.map { it.name }.orEmpty()

    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        } finally {
            retriever.release()
        }
    }

    @Test
    fun fixture_isReallyAnMp3_ofTheExpectedLength() {
        // Hlídá samotný testovací soubor: kdyby byl špatně vyrobený, ostatní testy by prošly omylem.
        val (file, _) = AudioFixtures.writeToCache(context, "fixture-check.mp3", AudioFixtures.silentMp3(3.0))
        sources += file
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            assertEquals("audio/mpeg", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
        } finally {
            retriever.release()
        }
        assertEquals(3000.0, durationOf(file).toDouble(), 300.0)
    }

    @Test
    fun validMp3_isSavedAndResolvable() {
        val result = save(source("ok.mp3", AudioFixtures.silentMp3(2.0)))

        assertTrue(result is AudioSaveResult.Saved)
        val name = (result as AudioSaveResult.Saved).fileName
        assertTrue(name.endsWith(".mp3"))
        val saved = store.resolve(name)
        assertNotNull(saved)
        assertEquals(listOf(name), filesInStore()) // žádný dočasný soubor nezůstal
        assertEquals(2000.0, durationOf(saved!!).toDouble(), 300.0)
    }

    @Test
    fun mp3LongerThanTheLimit_isRejected_andLeavesNothing() {
        val seconds = AudioLimits.MAX_DURATION_MILLIS / 1000.0 + 10
        val result = save(source("long.mp3", AudioFixtures.silentMp3(seconds)))

        assertEquals(AudioSaveResult.Failed(AudioError.TOO_LONG), result)
        assertTrue(filesInStore().isEmpty())
    }

    @Test
    fun fileLargerThanTheLimit_isRejected_andLeavesNothing() {
        val result = save(source("big.mp3", ByteArray((AudioLimits.MAX_BYTES + 1).toInt())))

        assertEquals(AudioSaveResult.Failed(AudioError.TOO_LARGE), result)
        assertTrue(filesInStore().isEmpty())
    }

    @Test
    fun nonMp3Audio_isRejectedAsUnsupportedFormat() {
        val result = save(source("zvuk.wav", AudioFixtures.silentWav(2)))

        assertEquals(AudioSaveResult.Failed(AudioError.UNSUPPORTED_FORMAT), result)
        assertTrue(filesInStore().isEmpty())
    }

    @Test
    fun garbage_isRejectedAsUnreadable() {
        val result = save(source("smeti.mp3", ByteArray(5000) { (it * 31).toByte() }))

        assertTrue(result is AudioSaveResult.Failed)
        assertTrue(filesInStore().isEmpty())
    }

    @Test
    fun missingSource_isRejectedAsUnreadable() {
        val result = save("file:///neexistuje/vubec.mp3")

        assertEquals(AudioSaveResult.Failed(AudioError.UNREADABLE), result)
        assertTrue(filesInStore().isEmpty())
    }

    @Test
    fun delete_removesFile_andIgnoresMissingOrUnsafeNames() = runBlocking {
        val name = (save(source("ok.mp3", AudioFixtures.silentMp3(1.0))) as AudioSaveResult.Saved).fileName

        store.delete("neexistuje.mp3")
        store.delete("../x")
        assertNotNull(store.resolve(name))

        store.delete(name)
        assertNull(store.resolve(name))
    }

    @Test
    fun resolve_refusesPathsOutsideTheStore() {
        assertNull(store.resolve("../databases/x"))
        assertNull(store.resolve("a/b.mp3"))
        assertNull(store.resolve(""))
    }

    @Test
    fun deleteAllExcept_keepsOnlyListedFiles() = runBlocking {
        val keep = (save(source("a.mp3", AudioFixtures.silentMp3(1.0))) as AudioSaveResult.Saved).fileName
        val drop = (save(source("b.mp3", AudioFixtures.silentMp3(1.0))) as AudioSaveResult.Saved).fileName

        store.deleteAllExcept(setOf(keep))

        assertNotNull(store.resolve(keep))
        assertNull(store.resolve(drop))
    }
}
