package cz.talkative.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.speech.SpeechProvider
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientViewModel @Inject constructor(
    observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    observeConfig: ObserveConfigUseCase,
    /** Instance patří tomuto view modelu a uvolní se v [onCleared]. */
    private val speech: SpeechProvider,
) : ViewModel() {

    /** Aktuální složka; `null` je kořen. Nezávisí na orientaci displeje, ta mění jen velikost mřížky. */
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val pageIndex = MutableStateFlow(0)
    private val sentence = MutableStateFlow<List<SentenceEntry>>(emptyList())
    private var nextEntryId = 0

    val uiState: StateFlow<ClientUiState> = combine(
        currentFolderId.flatMapLatest { folderId ->
            observeItems(folderId).map { items -> getPath(folderId) to items }
        },
        pageIndex,
        sentence,
        observeConfig(),
    ) { (path, items), page, sentenceEntries, config ->
        ClientUiState(
            path = path,
            items = items,
            pageIndex = page,
            sentence = sentenceEntries,
            config = config,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientUiState())

    fun onEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.OpenFolder -> goTo(event.folderId)
            is ClientEvent.GoToFolder -> goTo(event.folderId)
            ClientEvent.GoHome -> goTo(null)
            ClientEvent.NavigateUp -> viewModelScope.launch {
                val path = getPath(currentFolderId.value)
                goTo(path.getOrNull(path.size - 2)?.id)
            }
            is ClientEvent.PageChanged -> pageIndex.value = event.pageIndex
            is ClientEvent.WordSelected -> sentence.value += SentenceEntry(nextEntryId++, event.word)
            is ClientEvent.RemoveFromSentence -> sentence.value = sentence.value.filterNot { it.entryId == event.entryId }
            ClientEvent.ClearSentence -> sentence.value = emptyList()
        }
    }

    override fun onCleared() {
        speech.shutdown()
    }

    private fun goTo(folderId: String?) {
        currentFolderId.value = folderId
        pageIndex.value = 0
    }
}
