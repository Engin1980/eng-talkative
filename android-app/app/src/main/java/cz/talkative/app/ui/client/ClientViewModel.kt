package cz.talkative.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientViewModel @Inject constructor(
    observeItems: ObserveFolderItemsUseCase,
    observeConfig: ObserveConfigUseCase,
) : ViewModel() {

    /** Aktuální složka; `null` je kořen. Nezávisí na orientaci displeje, ta mění jen velikost mřížky. */
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val pageIndex = MutableStateFlow(0)

    val uiState: StateFlow<ClientUiState> = combine(
        currentFolderId.flatMapLatest { observeItems(it) },
        pageIndex,
        observeConfig(),
    ) { items, page, config ->
        ClientUiState(items = items, pageIndex = page, config = config, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientUiState())

    fun onEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.OpenFolder -> {
                currentFolderId.value = event.folderId
                pageIndex.value = 0
            }
            is ClientEvent.PageChanged -> pageIndex.value = event.pageIndex
        }
    }
}
