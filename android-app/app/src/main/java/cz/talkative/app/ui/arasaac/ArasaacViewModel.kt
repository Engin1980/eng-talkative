package cz.talkative.app.ui.arasaac

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramDownloader
import cz.talkative.app.domain.pictogram.PictogramSearch
import cz.talkative.app.domain.pictogram.PictogramSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Název argumentu trasy s předvyplněným hledaným slovem (titulek slovíčka). */
const val ARASAAC_QUERY_ARG = "query"

@HiltViewModel
class ArasaacViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pictogramSearch: PictogramSearch,
    private val downloader: PictogramDownloader,
) : ViewModel() {

    private val state = MutableStateFlow(ArasaacUiState(query = savedStateHandle.get<String>(ARASAAC_QUERY_ARG).orEmpty()))
    val uiState: StateFlow<ArasaacUiState> = state.asStateFlow()

    private var searchJob: Job? = null

    /** Text, podle kterého se naposledy hledalo; „Zkusit znovu“ hledá znovu podle něj, ne podle upraveného pole. */
    private var lastSearched: String? = null

    fun onEvent(event: ArasaacEvent) {
        when (event) {
            is ArasaacEvent.QueryChanged -> state.update { it.copy(query = event.query) }
            ArasaacEvent.Search -> search(state.value.query)
            ArasaacEvent.Retry -> lastSearched?.let { search(it) }
            is ArasaacEvent.Select -> select(event.pictogram)
            ArasaacEvent.PickedConsumed -> state.update { it.copy(pickedImageUri = null) }
        }
    }

    private fun search(text: String) {
        val query = text.trim()
        if (query.isEmpty()) {
            state.update { it.copy(search = SearchStatus.NotSearched) } // prázdné zadání se nehledá
            return
        }
        lastSearched = query
        searchJob?.cancel()
        state.update { it.copy(search = SearchStatus.Searching, downloadError = null) }
        searchJob = viewModelScope.launch {
            val status = when (val result = pictogramSearch.search(query)) {
                is PictogramSearchResult.Found ->
                    if (result.pictograms.isEmpty()) SearchStatus.Nothing else SearchStatus.Results(result.pictograms)
                is PictogramSearchResult.Failed -> SearchStatus.Failed(result.error)
            }
            state.update { it.copy(search = status) }
        }
    }

    private fun select(pictogram: Pictogram) {
        if (state.value.downloadingId != null) return // zdvojené klepnutí během stahování
        state.update { it.copy(downloadingId = pictogram.id, downloadError = null) }
        viewModelScope.launch {
            when (val result = downloader.download(pictogram.id)) {
                is PictogramDownloadResult.Downloaded ->
                    state.update { it.copy(downloadingId = null, pickedImageUri = result.file.toURI().toString()) }
                is PictogramDownloadResult.Failed ->
                    state.update { it.copy(downloadingId = null, downloadError = result.error) }
            }
        }
    }
}
