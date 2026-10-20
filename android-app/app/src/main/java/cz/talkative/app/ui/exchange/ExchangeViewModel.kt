package cz.talkative.app.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.exchange.ExportDestinationFactory
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ExportResult
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExchangeViewModel @Inject constructor(
    private val observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    private val exportDictionary: ExportDictionaryUseCase,
    private val destinations: ExportDestinationFactory,
) : ViewModel() {

    private val state = MutableStateFlow(ExchangeUiState())
    val uiState: StateFlow<ExchangeUiState> = state.asStateFlow()

    fun onEvent(event: ExchangeEvent) {
        when (event) {
            ExchangeEvent.ChooseExportFolder -> browse(null)
            is ExchangeEvent.BrowsePicker -> browse(event.folderId)
            ExchangeEvent.ConfirmPicker -> confirmPicker()
            ExchangeEvent.DismissPicker -> state.update { it.copy(picker = null) }
            ExchangeEvent.ExportWholeDictionary -> state.update { it.copy(exportFolder = null, export = ExportStatus.Idle) }
            is ExchangeEvent.IncludeConfigChanged -> state.update { it.copy(includeConfig = event.include, export = ExportStatus.Idle) }
            is ExchangeEvent.ExportToFile -> exportTo(event.uri)
        }
    }

    private fun browse(folderId: String?) {
        viewModelScope.launch {
            val path = getPath(folderId)
            val folders = observeItems(folderId).first().filterIsInstance<Folder>()
            state.update { it.copy(picker = FolderPickerState(path, folders)) }
        }
    }

    /** Vybere složku, kterou dialog právě ukazuje; v kořeni nic nevybírá (celý slovník má vlastní tlačítko). */
    private fun confirmPicker() {
        val picked = state.value.picker?.path?.lastOrNull()
        state.update { it.copy(picker = null, exportFolder = picked ?: it.exportFolder, export = ExportStatus.Idle) }
    }

    private fun exportTo(uri: String) {
        if (state.value.export is ExportStatus.Running) return
        val folderId = state.value.exportFolder?.id
        val includeConfig = state.value.includeConfig
        state.update { it.copy(export = ExportStatus.Running()) }
        viewModelScope.launch {
            val result = exportDictionary(folderId, includeConfig, destinations.forUri(uri)) { done, total ->
                state.update { it.copy(export = ExportStatus.Running(done, total)) }
            }
            state.update {
                it.copy(
                    export = when (result) {
                        is ExportResult.Success -> ExportStatus.Done(result.folders, result.words, result.missingMedia)
                        is ExportResult.Failed -> ExportStatus.Failed(result.error)
                    },
                )
            }
        }
    }
}
