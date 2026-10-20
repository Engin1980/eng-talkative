package cz.talkative.app.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.exchange.ArchiveManifest
import cz.talkative.app.domain.exchange.ArchiveReader
import cz.talkative.app.domain.exchange.ArchiveSource
import cz.talkative.app.domain.exchange.ArchiveSourceFactory
import cz.talkative.app.domain.exchange.ExportDestinationFactory
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ExportResult
import cz.talkative.app.domain.exchange.ImportArchiveUseCase
import cz.talkative.app.domain.exchange.ImportRequest
import cz.talkative.app.domain.exchange.ImportResult
import cz.talkative.app.domain.exchange.ImportSelection
import cz.talkative.app.domain.exchange.ReadResult
import cz.talkative.app.domain.exchange.listFolders
import cz.talkative.app.domain.exchange.planImport
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
    private val archiveReader: ArchiveReader,
    private val importArchive: ImportArchiveUseCase,
    private val archives: ArchiveSourceFactory,
) : ViewModel() {

    private val state = MutableStateFlow(ExchangeUiState())
    val uiState: StateFlow<ExchangeUiState> = state.asStateFlow()

    /** Zkontrolovaný archiv, ze kterého se plánuje import; uvnitř stavu je jen to, co UI zobrazuje. */
    private var manifest: ArchiveManifest? = null
    private var source: ArchiveSource? = null

    fun onEvent(event: ExchangeEvent) {
        when (event) {
            ExchangeEvent.ChooseExportFolder -> browse(null, PickerPurpose.EXPORT_FOLDER)
            ExchangeEvent.ChooseImportTarget -> browse(null, PickerPurpose.IMPORT_TARGET)
            is ExchangeEvent.BrowsePicker -> browse(event.folderId, state.value.picker?.purpose ?: PickerPurpose.EXPORT_FOLDER)
            ExchangeEvent.ConfirmPicker -> confirmPicker()
            ExchangeEvent.DismissPicker -> state.update { it.copy(picker = null) }
            ExchangeEvent.ExportWholeDictionary -> state.update { it.copy(exportFolder = null, export = ExportStatus.Idle) }
            is ExchangeEvent.IncludeConfigChanged -> state.update { it.copy(includeConfig = event.include, export = ExportStatus.Idle) }
            is ExchangeEvent.ExportToFile -> exportTo(event.uri)
            is ExchangeEvent.ArchivePicked -> readArchive(event.uri)
            is ExchangeEvent.ImportSelectAllChanged -> editImport { it.copy(selectAll = event.all) }
            is ExchangeEvent.ToggleImportFolder -> editImport { it.copy(selectedFolderIds = it.selectedFolderIds.toggled(event.folderId)) }
            is ExchangeEvent.IncludeRootWordsChanged -> editImport { it.copy(includeRootWords = event.include) }
            is ExchangeEvent.ReplaceAllChanged -> editImport { it.copy(replaceAll = event.replace) }
            is ExchangeEvent.ImportConfigChanged -> editImport { it.copy(importConfig = event.import) }
            ExchangeEvent.StartImport -> startImport()
            ExchangeEvent.ConfirmReplace -> {
                changeImport { it.copy(confirmReplace = false) }
                runImport()
            }
            ExchangeEvent.DismissReplace -> changeImport { it.copy(confirmReplace = false) }
        }
    }

    private fun browse(folderId: String?, purpose: PickerPurpose) {
        viewModelScope.launch {
            val path = getPath(folderId)
            val folders = observeItems(folderId).first().filterIsInstance<Folder>()
            state.update { it.copy(picker = FolderPickerState(path, folders, purpose)) }
        }
    }

    /** Vybere složku, kterou dialog ukazuje. U exportu v kořeni nic nevybírá (celý slovník má vlastní tlačítko). */
    private fun confirmPicker() {
        val picker = state.value.picker ?: return
        val picked = picker.path.lastOrNull()
        state.update {
            when (picker.purpose) {
                PickerPurpose.EXPORT_FOLDER ->
                    it.copy(picker = null, exportFolder = picked ?: it.exportFolder, export = ExportStatus.Idle)
                PickerPurpose.IMPORT_TARGET ->
                    it.copy(picker = null, import = refreshed(it.import.copy(target = picked, status = ImportStatus.Idle)))
            }
        }
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

    private fun readArchive(uri: String) {
        if (state.value.import.isBusy) return
        val newSource = archives.forUri(uri)
        manifest = null
        source = null
        state.update { it.copy(import = ImportUiState(status = ImportStatus.Reading)) }
        viewModelScope.launch {
            when (val result = archiveReader.read(newSource)) {
                is ReadResult.Success -> {
                    manifest = result.manifest
                    source = newSource
                    val info = ImportArchiveInfo(
                        folders = listFolders(result.manifest),
                        folderCount = result.manifest.folders.size,
                        wordCount = result.manifest.words.size,
                        hasRootWords = result.manifest.words.any { it.parentId == null },
                        hasConfig = result.manifest.config != null,
                    )
                    state.update { it.copy(import = refreshed(ImportUiState(archive = info))) }
                }
                is ReadResult.Failure ->
                    state.update { it.copy(import = ImportUiState(status = ImportStatus.Failed(result.error))) }
            }
        }
    }

    private fun startImport() {
        val import = state.value.import
        if (!import.canImport) return
        if (import.replaceAll) changeImport { it.copy(confirmReplace = true) } else runImport()
    }

    private fun runImport() {
        val import = state.value.import
        val currentSource = source ?: return
        if (!import.canImport) return
        val request = ImportRequest(
            selection = selectionOf(import),
            targetFolderId = import.target?.id,
            replaceAll = import.replaceAll,
            importConfig = import.importConfig && import.archive?.hasConfig == true,
        )
        changeImport { it.copy(status = ImportStatus.Running) }
        viewModelScope.launch {
            val result = importArchive(currentSource, request)
            changeImport {
                when (result) {
                    is ImportResult.Success -> it.copy(
                        status = ImportStatus.Done(result.folders, result.words, result.configImported),
                        replaceAll = false,
                        target = if (request.replaceAll) null else it.target,
                    )
                    is ImportResult.Failed -> it.copy(status = ImportStatus.Failed(result.error))
                }
            }
        }
    }

    private fun selectionOf(import: ImportUiState): ImportSelection =
        if (import.selectAll) ImportSelection.All else ImportSelection.Folders(import.selectedFolderIds, import.includeRootWords)

    /** Změní nastavení importu, přepočítá, kolik se přidá, a smaže starý výsledek. Během práce se nastavení nemění. */
    private fun editImport(transform: (ImportUiState) -> ImportUiState) {
        changeImport { if (it.isBusy) it else transform(it).copy(status = ImportStatus.Idle) }
    }

    private fun changeImport(transform: (ImportUiState) -> ImportUiState) {
        state.update { it.copy(import = refreshed(transform(it.import))) }
    }

    /** Přepočítá, kolik složek a slovíček se při současném výběru přidá. */
    private fun refreshed(import: ImportUiState): ImportUiState {
        val current = manifest ?: return import
        val plan = planImport(current, selectionOf(import))
        return import.copy(plannedFolders = plan.folders.size, plannedWords = plan.words.size)
    }

    private fun Set<String>.toggled(id: String) = if (id in this) this - id else this + id
}
