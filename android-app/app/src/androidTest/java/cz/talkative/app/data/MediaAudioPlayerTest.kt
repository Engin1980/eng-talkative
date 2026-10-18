package cz.talkative.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.audio.MediaAudioPlayer
import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.testing.AudioFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Skutečný [MediaPlayer] s tichým MP3. Emulátor nemá zvuk, takže se ověřuje průběh (dohrání, zrušení, chyba), ne zvuk. */
@RunWith(AndroidJUnit4::class)
class MediaAudioPlayerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioDir get() = File(context.filesDir, "audio")
    private lateinit var store: FileAudioStore
    private lateinit var player: MediaAudioPlayer
    private val sources = mutableListOf<File>()

    @Before
    fun setUp() {
        audioDir.deleteRecursively()
        store = FileAudioStore(context)
        player = MediaAudioPlayer(store)
    }

    @After
    fun tearDown() {
        audioDir.deleteRecursively()
        sources.forEach { it.delete() }
    }

    private fun stored(seconds: Double): String = runBlocking {
        val (file, uri) = AudioFixtures.writeToCache(context, "player-${System.nanoTime()}.mp3", AudioFixtures.silentMp3(seconds))
        sources += file
        (store.saveFromUri(uri) as AudioSaveResult.Saved).fileName
    }

    @Test
    fun playback_returnsWhenTheAudioFinishes() = runBlocking {
        val name = stored(1.0)
        val started = System.nanoTime()

        withTimeout(10_000) { player.play(name) }

        val elapsedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue("Přehrání skončilo příliš brzy: $elapsedMillis ms", elapsedMillis >= 500)
    }

    @Test
    fun cancellingPlayback_stopsItQuickly() = runBlocking {
        val name = stored(30.0)
        val job = async { player.play(name) }
        delay(1_000) // přehrávač se stihne připravit a začít

        val started = System.nanoTime()
        job.cancel()
        try {
            withTimeout(5_000) { job.await() }
            fail("Zrušené přehrávání se nemělo dokončit")
        } catch (e: CancellationException) {
            // očekávané
        }

        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    @Test
    fun missingFile_throwsPlaybackException() = runBlocking {
        try {
            player.play("neexistuje.mp3")
            fail("Očekávala se AudioPlaybackException")
        } catch (e: AudioPlaybackException) {
            // očekávané
        }
    }

    @Test
    fun brokenFile_throwsPlaybackException_insteadOfHangingOrCrashing() = runBlocking {
        audioDir.mkdirs()
        File(audioDir, "rozbite.mp3").writeBytes(ByteArray(2000) { (it * 7).toByte() })

        try {
            withTimeout(10_000) { player.play("rozbite.mp3") }
            fail("Očekávala se AudioPlaybackException")
        } catch (e: AudioPlaybackException) {
            // očekávané
        }
    }
}
