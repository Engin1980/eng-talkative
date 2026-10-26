package cz.talkative.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioPlayer
import cz.talkative.app.domain.audio.AudioRecorder
import cz.talkative.app.domain.audio.RecordingError
import cz.talkative.app.domain.audio.RecordingResult
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.MoveResult
import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DiscardAudioUseCase
import cz.talkative.app.domain.usecase.SaveAudioUseCase
import cz.talkative.app.domain.usecase.SaveRecordingUseCase
import cz.talkative.app.domain.usecase.DeleteItemsUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.FindWordProblemsUseCase
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.MoveItemInOrderUseCase
import cz.talkative.app.domain.usecase.MoveItemsUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
import cz.talkative.app.domain.usecase.SortAlphabeticallyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    private val saveFolder: SaveFolderUseCase,
    private val saveWord: SaveWordUseCase,
    private val countContents: CountFolderContentsUseCase,
    private val deleteFolder: DeleteFolderUseCase,
    private val deleteWord: DeleteWordUseCase,
    private val deleteItems: DeleteItemsUseCase,
    private val moveItems: MoveItemsUseCase,
    private val moveItemInOrder: MoveItemInOrderUseCase,
    private val sortAlphabetically: SortAlphabeticallyUseCase,
    private val saveImage: SaveImageUseCase,
    private val discardImage: DiscardImageUseCase,
    private val saveAudio: SaveAudioUseCase,
    private val discardAudio: DiscardAudioUseCase,
    private val audioPlayer: AudioPlayer,
    private val audioRecorder: AudioRecorder,
    private val saveRecording: SaveRecordingUseCase,
    private val findWordProblems: FindWordProblemsUseCase,
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<AdminDialog?>(null)
    private val selection = MutableStateFlow<Set<String>?>(null) // null = režim výběru je vypnutý
    private var nextSessionId = 0
    private var previewJob: Job? = null
    private var recordJob: Job? = null

    private val browsing = currentFolderId.flatMapLatest { folderId ->
        observeItems(folderId).map { items -> AdminUiState(path = getPath(folderId), items = items, isLoading = false, problems = findWordProblems(items.filterIsInstance<Word>())) }
    }

    val uiState: StateFlow<AdminUiState> = combine(browsing, dialog, selection) { browsingState, openDialog, selected ->
        browsingState.copy(dialog = openDialog, isSelecting = selected != null, selectedIds = selected.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    fun onEvent(event: AdminEvent) {
        when (event) {
            is AdminEvent.GoToFolder -> goToFolder(event.folderId)
            AdminEvent.NavigateUp -> navigateUp()
            AdminEvent.AddFolder -> dialog.value = AdminDialog.Edit(ItemKind.FOLDER, itemId = null, title = "", sessionId = nextSessionId++)
            AdminEvent.AddWord -> dialog.value = AdminDialog.Edit(ItemKind.WORD, itemId = null, title = "", sessionId = nextSessionId++)
            is AdminEvent.EditItem -> dialog.value = editDialogFor(event)
            is AdminEvent.TitleChanged -> updateEditDialog { it.copy(title = event.title, showEmptyError = false) }
            is AdminEvent.AlternateTextChanged -> updateEditDialog { it.copy(alternateText = event.text) }
            AdminEvent.ConfirmEdit -> confirmEdit()
            is AdminEvent.DeleteItem -> requestDelete(event)
            AdminEvent.ConfirmDelete -> confirmDelete()
            AdminEvent.DismissDialog -> dismissDialog()
            is AdminEvent.ShowProblems -> dialog.value = AdminDialog.ShowProblems(event.item, uiState.value.problems[event.item.id].orEmpty())
            is AdminEvent.ImagePicked -> processPickedImage(event.uri)
            AdminEvent.RemoveImage -> removeImage()
            is AdminEvent.ImageSelectionFailed -> updateEditDialog { it.copy(imageProblem = event.problem) }
            AdminEvent.DismissImageProblem -> updateEditDialog { it.copy(imageProblem = null) }
            is AdminEvent.AudioPicked -> processPickedAudio(event.uri)
            AdminEvent.RemoveAudio -> removeAudio()
            is AdminEvent.AudioSelectionFailed -> updateEditDialog { it.copy(audioProblem = event.problem) }
            AdminEvent.ToggleAudioPreview -> toggleAudioPreview()
            AdminEvent.DismissAudioProblem -> updateEditDialog { it.copy(audioProblem = null) }
            AdminEvent.StartRecording -> startRecording()
            AdminEvent.StopRecording -> audioRecorder.stop()
            AdminEvent.CancelRecording -> cancelRecording()
            is AdminEvent.RecordingPermissionDenied -> updateEditDialog {
                it.copy(
                    audioProblem = if (event.permanently) AudioProblem.MICROPHONE_PERMISSION_BLOCKED else AudioProblem.MICROPHONE_PERMISSION_DENIED,
                )
            }
            AdminEvent.ToggleSelecting -> selection.value = if (selection.value == null) emptySet() else null
            is AdminEvent.ToggleSelected -> toggleSelected(event.itemId)
            AdminEvent.ToggleSelectAll -> toggleSelectAll()
            AdminEvent.DeleteSelected -> requestDeleteSelected()
            AdminEvent.ConfirmDeleteSelected -> confirmDeleteSelected()
            is AdminEvent.MoveItemUp -> moveInOrder(event.itemId, offset = -1)
            is AdminEvent.MoveItemDown -> moveInOrder(event.itemId, offset = 1)
            AdminEvent.SortAlphabetically -> dialog.value = AdminDialog.ConfirmSort
            AdminEvent.ConfirmSortAlphabetically -> confirmSort()
            AdminEvent.MoveSelected -> requestMove()
            is AdminEvent.BrowseMoveTarget -> browseMoveTarget(event.folderId)
            AdminEvent.ConfirmMove -> confirmMove()
        }
    }

    /** Výběr patří jedné složce, proto se při přechodu jinam zruší. */
    private fun goToFolder(folderId: String?) {
        selection.value = null
        currentFolderId.value = folderId
    }

    private fun toggleSelected(itemId: String) {
        val selected = selection.value ?: return
        selection.value = if (itemId in selected) selected - itemId else selected + itemId
    }

    private fun toggleSelectAll() {
        val selected = selection.value ?: return
        val allIds = uiState.value.items.map { it.id }.toSet()
        selection.value = if (selected.containsAll(allIds)) emptySet() else allIds
    }

    private fun requestDeleteSelected() {
        val selected = selection.value?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val folders = uiState.value.items.filter { it.id in selected && it is Folder }
            val total = selected.size + folders.sumOf { countContents(it.id) }
            dialog.value = AdminDialog.ConfirmDeleteSelected(selected.size, total)
        }
    }

    private fun confirmSort() {
        if (dialog.value !is AdminDialog.ConfirmSort) return
        viewModelScope.launch {
            sortAlphabetically(currentFolderId.value)
            closeDialog()
        }
    }

    private fun moveInOrder(itemId: String, offset: Int) {
        val orderedIds = uiState.value.items.map { it.id }
        viewModelScope.launch { moveItemInOrder(currentFolderId.value, orderedIds, itemId, offset) }
    }

    /** Dialog přesunu začíná ve složce, ve které se právě výběr dělá. */
    private fun requestMove() {
        val selected = selection.value?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch { dialog.value = moveDialogFor(currentFolderId.value, selected.size) }
    }

    private fun browseMoveTarget(folderId: String?) {
        val current = dialog.value as? AdminDialog.MoveTarget ?: return
        viewModelScope.launch { dialog.value = moveDialogFor(folderId, current.movingCount) }
    }

    private suspend fun moveDialogFor(folderId: String?, movingCount: Int) = AdminDialog.MoveTarget(
        targetId = folderId,
        path = getPath(folderId),
        folders = observeItems(folderId).first().filterIsInstance<Folder>(),
        movingCount = movingCount,
    )

    private fun confirmMove() {
        val move = dialog.value as? AdminDialog.MoveTarget ?: return
        val selected = selection.value ?: return
        if (move.targetId == currentFolderId.value) return // položky už v cíli jsou
        viewModelScope.launch {
            when (moveItems(selected, move.targetId)) {
                MoveResult.Moved -> {
                    selection.value = null
                    closeDialog()
                }
                MoveResult.TargetInsideMovedFolder -> dialog.value = move.copy(problem = MoveProblem.INTO_ITSELF)
                MoveResult.TargetNotFound -> dialog.value = move.copy(problem = MoveProblem.TARGET_MISSING)
            }
        }
    }

    private fun confirmDeleteSelected() {
        if (dialog.value !is AdminDialog.ConfirmDeleteSelected) return
        val selected = selection.value ?: return
        viewModelScope.launch {
            deleteItems(selected)
            selection.value = null
            closeDialog()
        }
    }

    private fun editDialogFor(event: AdminEvent.EditItem): AdminDialog.Edit {
        val item = event.item
        val word = item as? Word
        val image = word?.imageFile
        return AdminDialog.Edit(
            kind = if (item is Folder) ItemKind.FOLDER else ItemKind.WORD,
            itemId = item.id,
            title = item.title,
            imageFile = image,
            originalImageFile = image,
            alternateText = word?.alternateText.orEmpty(),
            audioFile = word?.audioFile,
            originalAudioFile = word?.audioFile,
            sessionId = nextSessionId++,
        )
    }

    private fun navigateUp() {
        viewModelScope.launch {
            val path = getPath(currentFolderId.value)
            goToFolder(path.getOrNull(path.size - 2)?.id)
        }
    }

    private fun updateEditDialog(transform: (AdminDialog.Edit) -> AdminDialog.Edit) {
        val current = dialog.value as? AdminDialog.Edit ?: return
        dialog.value = transform(current)
    }

    private fun confirmEdit() {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.isProcessingImage || edit.isProcessingAudio || edit.isRecording) return
        viewModelScope.launch {
            val parentId = currentFolderId.value
            val result = when (edit.kind) {
                ItemKind.FOLDER -> saveFolder(edit.itemId, parentId, edit.title)
                ItemKind.WORD -> saveWord(edit.itemId, parentId, edit.title, edit.imageFile, edit.originalImageFile, edit.alternateText, edit.audioFile, edit.originalAudioFile)
            }
            when (result) {
                SaveResult.Saved -> closeDialog() // nové soubory jsou nyní přiřazeny slovíčku, nemažou se
                SaveResult.EmptyTitle -> updateEditDialog { it.copy(showEmptyError = true) }
            }
        }
    }

    /** Zruší dialog; obrázek a MP3 vybrané v dialogu, ale nepřiřazené slovíčku, se smažou ze souborů. */
    private fun dismissDialog() {
        val edit = dialog.value as? AdminDialog.Edit
        closeDialog()
        if (edit != null) {
            discardPendingImage(edit)
            discardPendingAudio(edit)
        }
    }

    private fun closeDialog() {
        cancelRecording()
        stopPreview()
        dialog.value = null
    }

    private fun discardPendingImage(edit: AdminDialog.Edit) {
        val pending = edit.imageFile
        if (pending != null && pending != edit.originalImageFile) viewModelScope.launch { discardImage(pending) }
    }

    private fun processPickedImage(uri: String) {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.kind != ItemKind.WORD || edit.isProcessingImage) return
        updateEditDialog { it.copy(isProcessingImage = true, imageProblem = null) }
        viewModelScope.launch {
            when (val result = saveImage(uri)) {
                is ImageSaveResult.Saved -> {
                    val current = dialog.value as? AdminDialog.Edit
                    if (current == null || current.sessionId != edit.sessionId) {
                        discardImage(result.fileName) // dialog mezitím zmizel, nikdo si obrázek nevezme
                        return@launch
                    }
                    discardPendingImage(current) // nahrazený (ještě nepřiřazený) obrázek
                    dialog.value = current.copy(imageFile = result.fileName, isProcessingImage = false)
                }
                is ImageSaveResult.Failed -> {
                    val problem = if (result.error == ImageError.STORAGE_FULL) ImageProblem.STORAGE_FULL else ImageProblem.UNREADABLE
                    val current = dialog.value as? AdminDialog.Edit ?: return@launch
                    if (current.sessionId == edit.sessionId) {
                        dialog.value = current.copy(isProcessingImage = false, imageProblem = problem)
                    }
                }
            }
        }
    }

    private fun discardPendingAudio(edit: AdminDialog.Edit) {
        val pending = edit.audioFile
        if (pending != null && pending != edit.originalAudioFile) viewModelScope.launch { discardAudio(pending) }
    }

    private fun processPickedAudio(uri: String) {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.kind != ItemKind.WORD || edit.isProcessingAudio || edit.isRecording) return
        stopPreview()
        updateEditDialog { it.copy(isProcessingAudio = true, audioProblem = null) }
        viewModelScope.launch {
            when (val result = saveAudio(uri)) {
                is AudioSaveResult.Saved -> {
                    val current = dialog.value as? AdminDialog.Edit
                    if (current == null || current.sessionId != edit.sessionId) {
                        discardAudio(result.fileName) // dialog mezitím zmizel, nikdo si MP3 nevezme
                        return@launch
                    }
                    discardPendingAudio(current) // nahrazené (ještě nepřiřazené) MP3
                    dialog.value = current.copy(audioFile = result.fileName, isProcessingAudio = false)
                }
                is AudioSaveResult.Failed -> {
                    val current = dialog.value as? AdminDialog.Edit ?: return@launch
                    if (current.sessionId == edit.sessionId) {
                        dialog.value = current.copy(isProcessingAudio = false, audioProblem = result.error.toProblem())
                    }
                }
            }
        }
    }

    /**
     * Nahrávání z mikrofonu: po zastavení se nahrávka uloží stejnou cestou jako vybraný soubor (nahrazuje dosud
     * nepřiřazený zvuk). Zrušení ([cancelRecording], zavření dialogu, smrt view modelu) nahrávku zahodí.
     */
    private fun startRecording() {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.kind != ItemKind.WORD || edit.isRecording || edit.isProcessingAudio) return
        stopPreview()
        updateEditDialog { it.copy(isRecording = true, recordingElapsedMillis = 0L, audioProblem = null) }
        recordJob = viewModelScope.launch {
            val elapsedJob = launch {
                audioRecorder.elapsedMillis.collect { millis -> updateEditDialog { it.copy(recordingElapsedMillis = millis) } }
            }
            val result = try {
                audioRecorder.record()
            } finally {
                elapsedJob.cancel()
            }
            finishRecording(edit.sessionId, result)
        }
    }

    private suspend fun finishRecording(sessionId: Int, result: RecordingResult) {
        val current = dialog.value as? AdminDialog.Edit
        if (current == null || current.sessionId != sessionId) {
            if (result is RecordingResult.Recorded) saveRecording.discard(result) // dialog mezitím zmizel
            return
        }
        when (result) {
            is RecordingResult.Recorded -> {
                dialog.value = current.copy(isRecording = false, recordingElapsedMillis = 0L, isProcessingAudio = true)
                when (val saved = saveRecording(result)) {
                    is AudioSaveResult.Saved -> {
                        val latest = dialog.value as? AdminDialog.Edit
                        if (latest == null || latest.sessionId != sessionId) {
                            discardAudio(saved.fileName)
                            return
                        }
                        discardPendingAudio(latest) // nahrazená (ještě nepřiřazená) nahrávka nebo vybraný soubor
                        dialog.value = latest.copy(
                            audioFile = saved.fileName,
                            isProcessingAudio = false,
                            audioProblem = if (result.reachedLimit) AudioProblem.RECORDING_LIMIT_REACHED else null,
                        )
                    }
                    is AudioSaveResult.Failed -> {
                        val latest = dialog.value as? AdminDialog.Edit ?: return
                        if (latest.sessionId == sessionId) {
                            dialog.value = latest.copy(isProcessingAudio = false, audioProblem = saved.error.toProblem())
                        }
                    }
                }
            }
            RecordingResult.TooShort ->
                dialog.value = current.copy(isRecording = false, recordingElapsedMillis = 0L, audioProblem = AudioProblem.NOTHING_RECORDED)
            is RecordingResult.Failed -> dialog.value = current.copy(
                isRecording = false,
                recordingElapsedMillis = 0L,
                audioProblem = when (result.error) {
                    RecordingError.NO_MICROPHONE -> AudioProblem.NO_MICROPHONE
                    RecordingError.MICROPHONE_UNAVAILABLE -> AudioProblem.MICROPHONE_UNAVAILABLE
                    RecordingError.STORAGE_FULL -> AudioProblem.STORAGE_FULL
                },
            )
        }
    }

    /** Zahodí běžící nahrávku (soubor smaže nahrávač při zrušení); po skončení nahrávání, při ukládání, nedělá nic. */
    private fun cancelRecording() {
        val edit = dialog.value as? AdminDialog.Edit
        if (edit?.isRecording != true) return
        recordJob?.cancel()
        recordJob = null
        updateEditDialog { it.copy(isRecording = false, recordingElapsedMillis = 0L) }
    }

    private fun AudioError.toProblem() = when (this) {
        AudioError.UNREADABLE -> AudioProblem.UNREADABLE
        AudioError.UNSUPPORTED_FORMAT -> AudioProblem.UNSUPPORTED_FORMAT
        AudioError.TOO_LONG -> AudioProblem.TOO_LONG
        AudioError.TOO_LARGE -> AudioProblem.TOO_LARGE
        AudioError.STORAGE_FULL -> AudioProblem.STORAGE_FULL
    }

    private fun removeAudio() {
        val current = dialog.value as? AdminDialog.Edit ?: return
        if (current.audioFile == null || current.isRecording) return
        stopPreview()
        discardPendingAudio(current)
        dialog.value = current.copy(audioFile = null, audioProblem = null, isPreviewingAudio = false)
    }

    private fun toggleAudioPreview() {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.isPreviewingAudio) {
            stopPreview()
            return
        }
        val fileName = edit.audioFile ?: return
        if (edit.isRecording) return // mikrofon a přehrávání se nesmí potkat
        updateEditDialog { it.copy(isPreviewingAudio = true, audioProblem = null) }
        previewJob = viewModelScope.launch {
            var problem: AudioProblem? = null
            try {
                audioPlayer.play(fileName)
            } catch (e: AudioPlaybackException) {
                problem = AudioProblem.PLAYBACK_FAILED
            }
            // Sem se dojde jen při dohrání nebo chybě; zrušení (stopPreview) stav nastavilo samo.
            if (isActive) updateEditDialog { it.copy(isPreviewingAudio = false, audioProblem = problem) }
        }
    }

    /** Zastaví náhled MP3 (přehrávač se uvolní zrušením korutiny). */
    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        updateEditDialog { it.copy(isPreviewingAudio = false) }
    }

    private fun removeImage() {
        val current = dialog.value as? AdminDialog.Edit ?: return
        if (current.imageFile == null) return
        discardPendingImage(current)
        dialog.value = current.copy(imageFile = null, imageProblem = null)
    }

    private fun requestDelete(event: AdminEvent.DeleteItem) {
        viewModelScope.launch {
            val count = if (event.item is Folder) countContents(event.item.id) else 0
            dialog.value = AdminDialog.ConfirmDelete(event.item, count)
        }
    }

    private fun confirmDelete() {
        val confirm = dialog.value as? AdminDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            when (val item = confirm.item) {
                is Folder -> deleteFolder(item.id)
                is Word -> deleteWord(item.id)
            }
            closeDialog()
        }
    }
}
