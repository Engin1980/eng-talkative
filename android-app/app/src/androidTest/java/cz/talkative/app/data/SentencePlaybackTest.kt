package cz.talkative.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.audio.MediaAudioPlayer
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.speech.PlaybackSegment
import cz.talkative.app.domain.speech.SentencePlayer
import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.testing.AudioFixtures
import cz.talkative.app.testing.FakeSpeechProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Věta „TTS, MP3, TTS“ přes skutečný [MediaAudioPlayer] a tichý MP3 soubor. TTS je falešné, protože emulátor nemá zvuk
 * a o řeči samotné rozhodl test se skutečným enginem; tady se ověřuje řazení úseků, Stop a chyby.
 */
@RunWith(AndroidJUnit4::class)
class SentencePlaybackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioDir get() = File(context.filesDir, "audio")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val speech = FakeSpeechProvider()
    private lateinit var store: FileAudioStore
    private lateinit var player: SentencePlayer
    private val sources = mutableListOf<File>()

    @Before
    fun setUp() {
        audioDir.deleteRecursively()
        store = FileAudioStore(context)
        player = SentencePlayer(speech, MediaAudioPlayer(store), scope)
    }

    @After
    fun tearDown() {
        player.stop()
        scope.cancel()
        audioDir.deleteRecursively()
        sources.forEach { it.delete() }
    }

    private fun storedMp3(seconds: Double): String = runBlocking {
        val (file, uri) = AudioFixtures.writeToCache(context, "sentence-${System.nanoTime()}.mp3", AudioFixtures.silentMp3(seconds))
        sources += file
        (store.saveFromUri(uri) as AudioSaveResult.Saved).fileName
    }

    private fun waitUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) = runBlocking {
        withTimeout(timeoutMillis) { while (!condition()) delay(20) }
    }

    @Test
    fun ttsThenMp3ThenTts_playInOrder_andEndWhenTheLastOneFinishes() {
        val mp3 = storedMp3(1.0)
        player.play(listOf(PlaybackSegment.Tts("A"), PlaybackSegment.Audio(mp3), PlaybackSegment.Tts("B C")))

        waitUntil { speech.spoken.size == 1 }
        Thread.sleep(500)
        assertEquals(listOf("A"), speech.spoken) // MP3 ještě nezačalo, takže "B C" taky ne
        speech.finishSpeaking()

        val mp3Started = System.nanoTime()
        waitUntil { speech.spoken.size == 2 } // "B C" přijde až po dohrání MP3
        val mp3Millis = (System.nanoTime() - mp3Started) / 1_000_000
        assertEquals(listOf("A", "B C"), speech.spoken)
        assertTrue("MP3 skončilo příliš brzy ($mp3Millis ms)", mp3Millis >= 500)
        assertTrue("Mezi MP3 a dalším úsekem je zbytečná pauza ($mp3Millis ms)", mp3Millis < 4_000)

        speech.finishSpeaking()
        waitUntil { !player.isPlaying }
    }

    @Test
    fun stopDuringMp3_endsQuickly_andNextSegmentDoesNotPlay() {
        val mp3 = storedMp3(30.0)
        player.play(listOf(PlaybackSegment.Audio(mp3), PlaybackSegment.Tts("nikdy")))
        Thread.sleep(1_000) // MP3 se připraví a hraje

        val started = System.nanoTime()
        player.stop()

        assertFalse(player.isPlaying)
        Thread.sleep(1_500)
        assertTrue(speech.spoken.isEmpty())
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun brokenMp3_reportsError_doesNotCrash_andPlayButtonWorksAgain() = runBlocking {
        audioDir.mkdirs()
        File(audioDir, "rozbite.mp3").writeBytes(ByteArray(2000) { (it * 7).toByte() })
        player.play(listOf(PlaybackSegment.Audio("rozbite.mp3"), PlaybackSegment.Tts("nikdy")))

        waitUntil { !player.isPlaying }
        assertEquals(SpeechError.AUDIO_FAILED, player.state.first().error)
        assertTrue(speech.spoken.isEmpty())

        player.clearError()
        player.play(listOf(PlaybackSegment.Tts("znovu")))
        waitUntil { speech.spoken.size == 1 }
        assertEquals(listOf("znovu"), speech.spoken)
    }

    @Test
    fun missingMp3File_reportsError() = runBlocking {
        player.play(listOf(PlaybackSegment.Audio("neexistuje.mp3")))

        waitUntil { !player.isPlaying }
        assertEquals(SpeechError.AUDIO_FAILED, player.state.first().error)
    }
}
