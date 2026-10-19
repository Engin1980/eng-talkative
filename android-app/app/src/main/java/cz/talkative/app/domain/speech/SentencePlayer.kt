package cz.talkative.app.domain.speech

import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart

/**
 * Přehraje větu úsek po úseku (viz [PlaybackPlanner]): TTS úseky přes [SpeechProvider], MP3 úseky přes [AudioPlayer].
 * Úseky jdou přísně za sebou, takže se nikdy nepřekrývají. Celé přehrávání je jedna korutina, proto [stop] ji prostě
 * zruší: aktuální úsek se zastaví a zbylé se nepřehrají.
 *
 * Stav „hraje“ platí po celou větu, ne jen během jednotlivých úseků, takže tlačítko Přehrát/Stop mezi úseky neblikne.
 * Selže-li úsek, přehrávání se zastaví a stav nese chybu (aby uživatel nebyl zmaten větou s chybějícím slovem).
 */
class SentencePlayer(
    private val speech: SpeechProvider,
    private val audio: AudioPlayer,
    private val scope: CoroutineScope,
) {
    private val playing = MutableStateFlow(false)
    private val audioFailed = MutableStateFlow(false)
    private var job: Job? = null

    val isPlaying: Boolean get() = playing.value

    /** `isSpeaking` platí po celou větu; chyba je z TTS enginu nebo z přehrání MP3. */
    val state: Flow<SpeechState> = combine(playing, audioFailed, speech.state) { isPlaying, mp3Failed, ttsState ->
        SpeechState(isSpeaking = isPlaying, error = if (mp3Failed) SpeechError.AUDIO_FAILED else ttsState.error)
    }

    /**
     * Spustí přehrávání [segments]; prázdný seznam nebo už běžící přehrávání se ignoruje.
     * [onCompleted] se zavolá jen tehdy, když se přehrály všechny úseky; po [stop] ani po chybě ne.
     */
    fun play(segments: List<PlaybackSegment>, onCompleted: () -> Unit = {}) {
        if (segments.isEmpty() || playing.value) return
        audioFailed.value = false
        playing.value = true
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            val self = currentCoroutineContext()[Job]
            try {
                val allPlayed = segments.all { playSegment(it) }
                if (allPlayed) onCompleted()
            } finally {
                // Po stop() mohlo mezitím začít nové přehrávání; jeho stav se nesmí přepsat.
                if (job == null || job === self) playing.value = false
            }
        }
        job = newJob
        newJob.start()
    }

    /** Okamžitě zastaví aktuální úsek a zruší zbylé. Bez přehrávání nedělá nic. */
    fun stop() {
        job?.cancel()
        job = null
        playing.value = false
    }

    fun clearError() {
        audioFailed.value = false
        speech.clearError()
    }

    /** Zastaví přehrávání a uvolní [SpeechProvider]. */
    fun shutdown() {
        stop()
        speech.shutdown()
    }

    /** Vrací `false`, když úsek selhal a v přehrávání se nemá pokračovat. */
    private suspend fun playSegment(segment: PlaybackSegment): Boolean = when (segment) {
        is PlaybackSegment.Tts -> playTts(segment.text)
        is PlaybackSegment.Audio -> playAudio(segment.fileName)
    }

    /** `speak` nastaví „mluví“ hned, takže čekání na konec nemůže minout rozběh řeči. */
    private suspend fun playTts(text: String): Boolean {
        speech.speak(text)
        try {
            speech.state.first { !it.isSpeaking }
        } catch (e: CancellationException) {
            speech.stop()
            throw e
        }
        return speech.state.value.error == null
    }

    private suspend fun playAudio(fileName: String): Boolean =
        try {
            audio.play(fileName)
            true
        } catch (e: AudioPlaybackException) {
            audioFailed.value = true
            false
        }
}
