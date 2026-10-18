package cz.talkative.app.testing

import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.domain.speech.SpeechProvider
import cz.talkative.app.domain.speech.SpeechState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Falešný poskytovatel hlasu pro testy (sdílený JVM i instrumentovanými testy). */
class FakeSpeechProvider : SpeechProvider {
    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state

    /** Všechny texty zadané k přečtení, v pořadí. */
    val spoken = mutableListOf<String>()
    var stopCount = 0
        private set
    var isShutdown = false
        private set

    override fun speak(text: String) {
        spoken += text
        _state.update { SpeechState(isSpeaking = true) }
    }

    override fun stop() {
        stopCount++
        _state.update { it.copy(isSpeaking = false) }
    }

    override fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun shutdown() {
        isShutdown = true
        _state.update { it.copy(isSpeaking = false) }
    }

    /** Simuluje, že řeč dohrála sama. */
    fun finishSpeaking() {
        _state.update { it.copy(isSpeaking = false) }
    }

    /** Simuluje chybu enginu. */
    fun fail(error: SpeechError) {
        _state.update { SpeechState(isSpeaking = false, error = error) }
    }
}
