package cz.talkative.app.ui.client

/** Stav klientské obrazovky (MVI: jediný neměnný stav). */
data class ClientUiState(
    val profileId: String? = null,
)

/** Akce z UI do view modelu. Zatím žádné, přibudou s procházením a skládáním věty. */
sealed interface ClientEvent
