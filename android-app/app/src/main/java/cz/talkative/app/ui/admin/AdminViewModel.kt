package cz.talkative.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioPlayer
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DiscardAudioUseCase
import cz.talkative.app.domain.usecase.SaveAudioUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
class AdminViewModel @Inject constructor(
    observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    private val saveFolder: SaveFolderUseCase,
    private val saveWord: SaveWordUseCase,
    private val countContents: CountFolderContentsUseCase,
    private val deleteFolder: DeleteFolderUseCase,
    private val deleteWord: DeleteWordUseCase,
    private val saveImage: SaveImageUseCase,
    private val discardImage: DiscardImageUseCase,
    private val saveAudio: SaveAudioUseCase,
    private val discardAudio: DiscardAudioUseCase,
    private val audioPlayer: AudioPlayer,
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<AdminDialog?>(null)
    private var nextSessionId = 0
    private var previewJob: Job? = null

    private val browsing = currentFolderId.flatMapLatest { folderId ->
        observeItems(folderId).map { items -> AdminUiState(path = getPath(folderId), items = items, isLoading = false) }
    }

    val uiState: StateFlow<AdminUiState> = combine(browsing, dialog) { browsingState, openDialog ->
        browsingState.copy(dialog = openDialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    fun onEvent(event: AdminEvent) {
        when (event) {
            is AdminEvent.GoToFolder -> currentFolderId.value = event.folderId
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
            is AdminEvent.ImagePicked -> processPickedImage(event.uri)
            AdminEvent.RemoveImage -> removeImage()
            is AdminEvent.ImageSelectionFailed -> updateEditDialog { it.copy(imageProblem = event.problem) }
            AdminEvent.DismissImageProblem -> updateEditDialog { it.copy(imageProblem = null) }
            is AdminEvent.AudioPicked -> processPickedAudio(event.uri)
            AdminEvent.RemoveAudio -> removeAudio()
            is AdminEvent.AudioSelectionFailed -> updateEditDialog { it.copy(audioProblem = event.problem) }
            AdminEvent.ToggleAudioPreview -> toggleAudioPreview()
            AdminEvent.DismissAudioProblem -> updateEditDialog { it.copy(audioProblem = null) }
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
            currentFolderId.value = path.getOrNull(path.size - 2)?.id
        }
    }

    private fun updateEditDialog(transform: (AdminDialog.Edit) -> AdminDialog.Edit) {
        val current = dialog.value as? AdminDialog.Edit ?: return
        dialog.value = transform(current)
    }

    private fun confirmEdit() {
        val edit = dialog.value as? AdminDialog.Edit ?: return
        if (edit.isProcessingImage || edit.isProcessingAudio) return
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
        if (edit.kind != ItemKind.WORD || edit.isProcessingAudio) return
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

    private fun AudioError.toProblem() = when (this) {
        AudioError.UNREADABLE -> AudioProblem.UNREADABLE
        AudioError.UNSUPPORTED_FORMAT -> AudioProblem.UNSUPPORTED_FORMAT
        AudioError.TOO_LONG -> AudioProblem.TOO_LONG
        AudioError.TOO_LARGE -> AudioProblem.TOO_LARGE
        AudioError.STORAGE_FULL -> AudioProblem.STORAGE_FULL
    }

    private fun removeAudio() {
        val current = dialog.value as? AdminDialog.Edit ?: return
        if (current.audioFile == null) return
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
