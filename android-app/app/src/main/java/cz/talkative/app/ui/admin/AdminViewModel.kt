package cz.talkative.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
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
class AdminViewModel @Inject constructor(
    observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    private val saveFolder: SaveFolderUseCase,
    private val saveWord: SaveWordUseCase,
    private val countContents: CountFolderContentsUseCase,
    private val deleteFolder: DeleteFolderUseCase,
    private val deleteWord: DeleteWordUseCase,
) : ViewModel() {

    private val currentFolderId = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<AdminDialog?>(null)

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
            AdminEvent.AddFolder -> dialog.value = AdminDialog.Edit(ItemKind.FOLDER, itemId = null, title = "")
            AdminEvent.AddWord -> dialog.value = AdminDialog.Edit(ItemKind.WORD, itemId = null, title = "")
            is AdminEvent.EditItem -> dialog.value = AdminDialog.Edit(
                kind = if (event.item is Folder) ItemKind.FOLDER else ItemKind.WORD,
                itemId = event.item.id,
                title = event.item.title,
            )
            is AdminEvent.TitleChanged -> updateEditDialog { it.copy(title = event.title, showEmptyError = false) }
            AdminEvent.ConfirmEdit -> confirmEdit()
            is AdminEvent.DeleteItem -> requestDelete(event)
            AdminEvent.ConfirmDelete -> confirmDelete()
            AdminEvent.DismissDialog -> dialog.value = null
        }
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
        viewModelScope.launch {
            val parentId = currentFolderId.value
            val result = when (edit.kind) {
                ItemKind.FOLDER -> saveFolder(edit.itemId, parentId, edit.title)
                ItemKind.WORD -> saveWord(edit.itemId, parentId, edit.title)
            }
            when (result) {
                SaveResult.Saved -> dialog.value = null
                SaveResult.EmptyTitle -> updateEditDialog { it.copy(showEmptyError = true) }
            }
        }
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
            dialog.value = null
        }
    }
}
