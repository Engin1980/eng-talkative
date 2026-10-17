package cz.talkative.app.domain.speech

import kotlinx.coroutines.flow.StateFlow

/** Proč se řeč nepodařilo spustit. UI k tomu zobrazí srozumitelnou hlášku s nápovědou. */
enum class SpeechError {
    /** Na zařízení chybí TTS engine nebo se nepodařilo ho spustit. */
    ENGINE_UNAVAILABLE,

    /** Engine nemá český hlas (chybí jazyková data). */
    LANGUAGE_MISSING,

    /** Přehrání selhalo za běhu. */
    FAILED,
}

data class SpeechState(
    val isSpeaking: Boolean = false,
    val error: SpeechError? = null,
)

/**
 * Poskytovatel hlasu (TTS, později např. i MP3 nebo cloudové hlasy). Aplikace s ním pracuje jen přes toto rozhraní.
 * Instance drží systémové prostředky, proto je potřeba po použití zavolat [shutdown].
 */
interface SpeechProvider {
    val state: StateFlow<SpeechState>

    /** Přečte [text] jedním plynulým výstupem; rozmluvené přehrávání se nahradí novým. */
    fun speak(text: String)

    /** Okamžitě přeruší řeč. Bez běžící řeči nedělá nic. */
    fun stop()

    /** Zruší aktuální chybu (po zobrazení uživateli). */
    fun clearError()

    /** Uvolní prostředky; instance se potom už nepoužívá. */
    fun shutdown()
}
