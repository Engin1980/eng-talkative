package cz.talkative.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.usecase.ObserveCurrentProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    observeCurrentProfile: ObserveCurrentProfileUseCase,
) : ViewModel() {

    val uiState: StateFlow<ClientUiState> = observeCurrentProfile()
        .map { profile -> ClientUiState(profileId = profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientUiState())

    @Suppress("UNUSED_PARAMETER")
    fun onEvent(event: ClientEvent) {
        // Zatím nic ke zpracování.
    }
}
