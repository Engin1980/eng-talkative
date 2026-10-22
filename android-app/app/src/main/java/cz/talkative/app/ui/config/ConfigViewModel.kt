package cz.talkative.app.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.UpdateConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    observeConfig: ObserveConfigUseCase,
    private val updateConfig: UpdateConfigUseCase,
) : ViewModel() {

    private val confirmReset = MutableStateFlow(false)

    val uiState: StateFlow<ConfigUiState> = combine(observeConfig(), confirmReset) { config, askReset ->
        ConfigUiState(config = config, isLoading = false, confirmReset = askReset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigUiState())

    fun onEvent(event: ConfigEvent) {
        when (event) {
            is ConfigEvent.AddWordModeChanged -> update { it.copy(addWordMode = event.mode) }
            is ConfigEvent.AllowReorderChanged -> update { it.copy(allowReorderInSentence = event.enabled) }
            is ConfigEvent.AddHoldChanged -> update { it.copy(addHoldMillis = event.millis) }
            is ConfigEvent.RemoveHoldChanged -> update { it.copy(removeHoldMillis = event.millis) }
            is ConfigEvent.AdminHoldChanged -> update { it.copy(adminHoldMillis = event.millis) }
            is ConfigEvent.SpeakWordOnTapChanged -> update { it.copy(speakWordOnTap = event.enabled) }
            is ConfigEvent.GridChanged ->
                update { if (event.landscape) it.copy(gridLandscape = event.grid) else it.copy(gridPortrait = event.grid) }
            is ConfigEvent.CollationChanged -> update { it.copy(collationLanguageTag = event.languageTag) }
            is ConfigEvent.HideCaptionsChanged -> update { it.copy(hideCaptionsInSentence = event.enabled) }
            is ConfigEvent.AutoClearChanged -> update { it.copy(autoClearAfterPlayback = event.enabled) }
            is ConfigEvent.TextScaleChanged -> update { it.copy(textScalePercent = event.percent) }
            ConfigEvent.ResetRequested -> confirmReset.value = true
            ConfigEvent.ResetDismissed -> confirmReset.value = false
            ConfigEvent.ResetConfirmed -> {
                confirmReset.value = false
                update { AppConfig() }
            }
        }
    }

    /** Hodnoty mimo povolené meze srazí [UpdateConfigUseCase] na nejbližší povolenou. */
    private fun update(transform: (AppConfig) -> AppConfig) {
        viewModelScope.launch { updateConfig(transform) }
    }
}
