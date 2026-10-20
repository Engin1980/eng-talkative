package cz.talkative.app.ui.config

import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize

/** Stav obrazovky konfigurace: aktuální hodnoty a případný dotaz na obnovení výchozích. */
data class ConfigUiState(
    val config: AppConfig = AppConfig(),
    val isLoading: Boolean = true,
    val confirmReset: Boolean = false,
)

sealed interface ConfigEvent {
    data class AddWordModeChanged(val mode: AddWordMode) : ConfigEvent
    data class AllowReorderChanged(val enabled: Boolean) : ConfigEvent
    data class AddHoldChanged(val millis: Long) : ConfigEvent
    data class RemoveHoldChanged(val millis: Long) : ConfigEvent
    data class AdminHoldChanged(val millis: Long) : ConfigEvent
    data class SpeakWordOnTapChanged(val enabled: Boolean) : ConfigEvent

    /** Velikost mřížky pro orientaci na šířku ([landscape]) nebo na výšku. */
    data class GridChanged(val landscape: Boolean, val grid: GridSize) : ConfigEvent
    data class CollationChanged(val languageTag: String) : ConfigEvent
    data class HideCaptionsChanged(val enabled: Boolean) : ConfigEvent
    data class AutoClearChanged(val enabled: Boolean) : ConfigEvent

    data object ResetRequested : ConfigEvent
    data object ResetConfirmed : ConfigEvent
    data object ResetDismissed : ConfigEvent
}
