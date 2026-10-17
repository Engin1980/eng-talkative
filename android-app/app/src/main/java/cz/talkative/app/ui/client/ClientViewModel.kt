package cz.talkative.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.ObserveCurrentProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    observeCurrentProfile: ObserveCurrentProfileUseCase,
    observeConfig: ObserveConfigUseCase,
) : ViewModel() {

    val uiState: StateFlow<ClientUiState> = combine(observeCurrentProfile(), observeConfig()) { profile, config ->
        ClientUiState(profileId = profile.id, adminHoldMillis = config.adminHoldMillis)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientUiState())

    @Suppress("UNUSED_PARAMETER")
    fun onEvent(event: ClientEvent) {
        // Zatím nic ke zpracování.
    }
}
