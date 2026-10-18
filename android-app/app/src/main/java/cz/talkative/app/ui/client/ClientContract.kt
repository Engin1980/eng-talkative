package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.speech.SpeechError

/** Stav klientské obrazovky (MVI: jediný neměnný stav). */
data class ClientUiState(
    /** Cesta od první složky pod kořenem po aktuální; prázdná = jsme v kořeni. */
    val path: List<Folder> = emptyList(),
    /** Obsah aktuální složky (složky i slovíčka) v ručním pořadí. */
    val items: List<DictionaryItem> = emptyList(),
    /** Vybraná stránka mřížky; UI ji před použitím ještě omezí podle velikosti mřížky. */
    val pageIndex: Int = 0,
    /** Rozpracovaná věta; neukládá se přes restart aplikace. */
    val sentence: List<SentenceEntry> = emptyList(),
    val config: AppConfig = AppConfig(),
    val isSpeaking: Boolean = false,
    /** Chyba řeči čekající na zobrazení uživateli. */
    val speechError: SpeechError? = null,
    val isLoading: Boolean = true,
)

sealed interface ClientEvent {
    /** Vstoupí do složky (stránka se vrátí na začátek). */
    data class OpenFolder(val folderId: String) : ClientEvent

    /** Přejde na libovolnou složku na cestě (drobečky); `null` je kořen. */
    data class GoToFolder(val folderId: String?) : ClientEvent

    /** Tlačítko „Zpět“: o úroveň výš. */
    data object NavigateUp : ClientEvent

    /** Tlačítko „Domů“: do kořene. */
    data object GoHome : ClientEvent

    data class PageChanged(val pageIndex: Int) : ClientEvent

    /** Klepnutí na slovíčko v mřížce: přidá ho na konec věty (i opakovaně). */
    data class WordSelected(val word: Word) : ClientEvent

    /** Podržení slova ve větě: odebere právě tento výskyt. */
    data class RemoveFromSentence(val entryId: Int) : ClientEvent

    data object ClearSentence : ClientEvent

    /** Tlačítko Přehrát / Zastavit. */
    data object TogglePlayback : ClientEvent

    data object DismissSpeechError : ClientEvent
}
