package cz.talkative.app.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.domain.speech.SpeechProvider
import cz.talkative.app.domain.speech.SpeechState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

/**
 * Řeč přes Android [TextToSpeech] (čeština, hlas dle engine zařízení).
 *
 * Inicializace enginu je asynchronní: text zadaný před dokončením inicializace se uloží a přečte se po ní.
 * Callbacky enginu chodí z jiných vláken, proto jsou všechny změny stavu přes [MutableStateFlow] a sdílená pole
 * se mění jen v bloku `synchronized`. Instance není singleton: patří vlastníkovi (ViewModel), který ji uvolní.
 */
class AndroidTtsSpeechProvider @Inject constructor(
    @ApplicationContext context: Context,
) : SpeechProvider {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var ready = false
    private var failure: SpeechError? = null
    private var pendingText: String? = null
    private var currentUtteranceId: String? = null
    private var utteranceCounter = 0
    private var released = false

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finished(utteranceId, error = null)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId, error = null)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId, error = SpeechError.FAILED)

        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId, error = SpeechError.FAILED)
    }

    init {
        // Callback inicializace čeká na [lock], takže vždy uvidí už uložený engine.
        synchronized(lock) {
            engine = TextToSpeech(context.applicationContext) { status -> onInit(status) }
        }
    }

    private fun onInit(status: Int) {
        synchronized(lock) {
            val tts = engine
            if (released || tts == null) return
            if (status != TextToSpeech.SUCCESS) {
                failure = SpeechError.ENGINE_UNAVAILABLE
                reportFailure()
                return
            }
            val result = tts.setLanguage(CZECH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                failure = SpeechError.LANGUAGE_MISSING
                reportFailure()
                return
            }
            tts.setOnUtteranceProgressListener(progressListener)
            ready = true
            pendingText?.let { text ->
                pendingText = null
                startSpeaking(tts, text)
            }
        }
    }

    override fun speak(text: String) {
        synchronized(lock) {
            if (released || text.isBlank()) return
            failure?.let {
                reportFailure()
                return
            }
            val tts = engine
            if (!ready || tts == null) {
                pendingText = text
                _state.update { it.copy(isSpeaking = true, error = null) }
            } else {
                startSpeaking(tts, text)
            }
        }
    }

    /** Volá se pod [lock]. */
    private fun startSpeaking(tts: TextToSpeech, text: String) {
        val id = "utterance-${utteranceCounter++}"
        currentUtteranceId = id
        _state.update { it.copy(isSpeaking = true, error = null) }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
            currentUtteranceId = null
            _state.update { SpeechState(isSpeaking = false, error = SpeechError.FAILED) }
        }
    }

    override fun stop() {
        synchronized(lock) {
            pendingText = null
            currentUtteranceId = null
            engine?.takeIf { ready }?.stop()
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    override fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun shutdown() {
        synchronized(lock) {
            if (released) return
            released = true
            pendingText = null
            currentUtteranceId = null
            engine?.stop()
            engine?.shutdown()
            engine = null
            ready = false
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    /** Konec promluvy; událost pozdní promluvy (po `stop()` nebo nové promluvě) se ignoruje. */
    private fun finished(utteranceId: String?, error: SpeechError?) {
        synchronized(lock) {
            if (utteranceId != currentUtteranceId) return
            currentUtteranceId = null
            _state.update { SpeechState(isSpeaking = false, error = error ?: it.error) }
        }
    }

    private fun reportFailure() {
        pendingText = null
        _state.update { SpeechState(isSpeaking = false, error = failure) }
    }

    private companion object {
        val CZECH: Locale = Locale.forLanguageTag("cs-CZ")
    }
}
