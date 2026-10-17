package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig

/** Stav klientské obrazovky (MVI: jediný neměnný stav). */
data class ClientUiState(
    val profileId: String? = null,
    /** Jak dlouho podržet skrytý prvek pro vstup do administrace (z konfigurace). */
    val adminHoldMillis: Long = AppConfig().adminHoldMillis,
)

/** Akce z UI do view modelu. Zatím žádné, přibudou s procházením a skládáním věty. */
sealed interface ClientEvent
