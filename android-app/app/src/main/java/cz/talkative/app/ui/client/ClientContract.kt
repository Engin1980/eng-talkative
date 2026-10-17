package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.DictionaryItem

/** Stav klientské obrazovky (MVI: jediný neměnný stav). */
data class ClientUiState(
    /** Obsah aktuální složky (složky i slovíčka) v ručním pořadí. */
    val items: List<DictionaryItem> = emptyList(),
    /** Vybraná stránka mřížky; UI ji před použitím ještě omezí podle velikosti mřížky. */
    val pageIndex: Int = 0,
    val config: AppConfig = AppConfig(),
    val isLoading: Boolean = true,
)

sealed interface ClientEvent {
    /** Vstoupí do složky (stránka se vrátí na začátek). */
    data class OpenFolder(val folderId: String) : ClientEvent

    data class PageChanged(val pageIndex: Int) : ClientEvent
}
