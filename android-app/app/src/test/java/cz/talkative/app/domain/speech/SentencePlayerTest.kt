package cz.talkative.app.domain.speech

import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.speech.PlaybackSegment.Audio
import cz.talkative.app.domain.speech.PlaybackSegment.Tts
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeSpeechProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SentencePlayerTest {

    private val speech = FakeSpeechProvider()
    private val audio = FakeAudioPlayer()

    private fun runPlayerTest(block: suspend TestScope.(SentencePlayer) -> Unit) =
        runTest(UnconfinedTestDispatcher()) { block(SentencePlayer(speech, audio, backgroundScope)) }

    private suspend fun SentencePlayer.error() = state.first().error

    @Test
    fun sentence_playsInOrder_tts_mp3_tts_withoutOverlap() = runPlayerTest { player ->
        // „A, [MP3], B, C“ → TTS(A), MP3, TTS(B C)
        player.play(listOf(Tts("A"), Audio("m.mp3"), Tts("B C")))

        assertEquals(listOf("A"), speech.spoken)
        assertTrue(audio.played.isEmpty()) // MP3 čeká, dokud TTS nedomluví

        speech.finishSpeaking()
        assertEquals(listOf("m.mp3"), audio.played)
        assertEquals(1, audio.activePlays)
        assertEquals(listOf("A"), speech.spoken) // druhé TTS čeká, dokud MP3 nedohraje

        audio.finishPlaying()
        assertEquals(listOf("A", "B C"), speech.spoken)
        assertEquals(0, audio.activePlays)

        speech.finishSpeaking()
        assertFalse(player.isPlaying)
        assertEquals(listOf("A", "B C"), speech.spoken)
    }

    @Test
    fun playing_staysTrueBetweenSegments_soTheButtonDoesNotFlicker() = runPlayerTest { player ->
        player.play(listOf(Tts("A"), Audio("m.mp3"), Tts("B")))
        assertTrue(player.state.first().isSpeaking)

        speech.finishSpeaking() // přechod TTS → MP3
        assertTrue(player.state.first().isSpeaking)

        audio.finishPlaying() // přechod MP3 → TTS
        assertTrue(player.state.first().isSpeaking)

        speech.finishSpeaking()
        assertFalse(player.state.first().isSpeaking)
    }

    @Test
    fun sentenceOfOnlyMp3_playsThemOneAfterAnother() = runPlayerTest { player ->
        player.play(listOf(Audio("a.mp3"), Audio("b.mp3")))
        assertEquals(listOf("a.mp3"), audio.played)

        audio.finishPlaying()
        assertEquals(listOf("a.mp3", "b.mp3"), audio.played)
        assertTrue(speech.spoken.isEmpty())

        audio.finishPlaying()
        assertFalse(player.isPlaying)
    }

    @Test
    fun mp3AtTheStartAndAtTheEnd() = runPlayerTest { player ->
        player.play(listOf(Audio("start.mp3"), Tts("A"), Audio("end.mp3")))

        audio.finishPlaying()
        assertEquals(listOf("A"), speech.spoken)
        speech.finishSpeaking()
        assertEquals(listOf("start.mp3", "end.mp3"), audio.played)
        audio.finishPlaying()
        assertFalse(player.isPlaying)
    }

    @Test
    fun stopDuringTts_stopsSpeech_andRemainingSegmentsDoNotPlay() = runPlayerTest { player ->
        player.play(listOf(Tts("A"), Audio("m.mp3"), Tts("B")))

        player.stop()

        assertFalse(player.isPlaying)
        assertEquals(1, speech.stopCount)
        assertTrue(audio.played.isEmpty())
        assertEquals(listOf("A"), speech.spoken)
    }

    @Test
    fun stopDuringMp3_releasesPlayer_andRemainingSegmentsDoNotPlay() = runPlayerTest { player ->
        player.play(listOf(Tts("A"), Audio("m.mp3"), Tts("B")))
        speech.finishSpeaking()
        assertEquals(1, audio.activePlays)

        player.stop()

        assertFalse(player.isPlaying)
        assertEquals(0, audio.activePlays)
        assertEquals(listOf("A"), speech.spoken) // B se nepřehrálo
    }

    @Test
    fun afterStop_playingAgainStartsFromTheBeginning() = runPlayerTest { player ->
        player.play(listOf(Tts("A"), Audio("m.mp3")))
        player.stop()

        player.play(listOf(Tts("A"), Audio("m.mp3")))

        assertTrue(player.isPlaying)
        assertEquals(listOf("A", "A"), speech.spoken)
        speech.finishSpeaking()
        assertEquals(listOf("m.mp3"), audio.played)
    }

    @Test
    fun failingMp3_stopsPlayback_reportsError_andPlayWorksAgain() = runPlayerTest { player ->
        audio.failingFiles += "rozbite.mp3"
        player.play(listOf(Audio("rozbite.mp3"), Tts("A")))

        assertFalse(player.isPlaying)
        assertEquals(SpeechError.AUDIO_FAILED, player.error())
        assertTrue(speech.spoken.isEmpty()) // za rozbitým slovem se nepokračuje

        player.clearError()
        assertNull(player.error())

        player.play(listOf(Tts("B")))
        assertTrue(player.isPlaying)
        assertEquals(listOf("B"), speech.spoken)
    }

    @Test
    fun failingMp3AfterTts_stopsRemainingSegments() = runPlayerTest { player ->
        audio.failWith = AudioPlaybackException("chyba")
        player.play(listOf(Tts("A"), Audio("m.mp3"), Tts("B")))

        speech.finishSpeaking()

        assertFalse(player.isPlaying)
        assertEquals(SpeechError.AUDIO_FAILED, player.error())
        assertEquals(listOf("A"), speech.spoken)
    }

    @Test
    fun newPlayClearsThePreviousAudioError() = runPlayerTest { player ->
        audio.failWith = AudioPlaybackException("chyba")
        player.play(listOf(Audio("m.mp3")))
        assertEquals(SpeechError.AUDIO_FAILED, player.error())

        audio.failWith = null
        audio.holdPlayback = false
        player.play(listOf(Audio("m.mp3")))

        assertNull(player.error())
    }

    @Test
    fun ttsFailure_stopsRemainingSegments() = runPlayerTest { player ->
        player.play(listOf(Tts("A"), Audio("m.mp3")))

        speech.fail(SpeechError.ENGINE_UNAVAILABLE)

        assertFalse(player.isPlaying)
        assertEquals(SpeechError.ENGINE_UNAVAILABLE, player.error())
        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun playWhilePlaying_isIgnored() = runPlayerTest { player ->
        player.play(listOf(Tts("A")))

        player.play(listOf(Tts("jiná")))

        assertEquals(listOf("A"), speech.spoken)
    }

    @Test
    fun emptyPlan_doesNothing() = runPlayerTest { player ->
        player.play(emptyList())

        assertFalse(player.isPlaying)
        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun shutdown_stopsPlaybackAndReleasesProvider() = runPlayerTest { player ->
        player.play(listOf(Audio("m.mp3"), Tts("A")))

        player.shutdown()

        assertFalse(player.isPlaying)
        assertEquals(0, audio.activePlays)
        assertTrue(speech.isShutdown)
        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun onCompleted_isCalledOnlyAfterTheLastSegmentFinishes() = runPlayerTest { player ->
        var completed = 0
        player.play(listOf(Tts("A"), Audio("m.mp3"))) { completed++ }

        assertEquals(0, completed)
        speech.finishSpeaking()
        assertEquals(0, completed)
        audio.finishPlaying()

        assertEquals(1, completed)
        assertFalse(player.isPlaying)
    }

    @Test
    fun onCompleted_isNotCalledAfterStop() = runPlayerTest { player ->
        var completed = 0
        player.play(listOf(Tts("A"), Audio("m.mp3"))) { completed++ }

        player.stop()

        assertEquals(0, completed)
    }

    @Test
    fun onCompleted_isNotCalledAfterStopDuringMp3() = runPlayerTest { player ->
        var completed = 0
        player.play(listOf(Audio("m.mp3"))) { completed++ }

        player.stop()

        assertEquals(0, completed)
    }

    @Test
    fun onCompleted_isNotCalledAfterAnError() = runPlayerTest { player ->
        var completed = 0
        audio.failingFiles += "rozbite.mp3"
        player.play(listOf(Tts("A"), Audio("rozbite.mp3"))) { completed++ }
        speech.finishSpeaking()

        assertEquals(0, completed)
        assertEquals(SpeechError.AUDIO_FAILED, player.error())
    }

    @Test
    fun onCompleted_isNotCalledAfterTtsError() = runPlayerTest { player ->
        var completed = 0
        player.play(listOf(Tts("A"))) { completed++ }

        speech.fail(SpeechError.ENGINE_UNAVAILABLE)

        assertEquals(0, completed)
    }
}
