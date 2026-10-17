package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder

enum class ItemKind { FOLDER, WORD }

/** Otevřený dialog nad administrací. */
sealed interface AdminDialog {
    /** Vytvoření ([itemId] je `null`) nebo úprava titulku. */
    data class Edit(
        val kind: ItemKind,
        val itemId: String?,
        val title: String,
        val showEmptyError: Boolean = false,
    ) : AdminDialog

    /** Potvrzení smazání; [contentCount] je počet položek uvnitř složky (u slovíčka 0). */
    data class ConfirmDelete(val item: DictionaryItem, val contentCount: Int) : AdminDialog
}

/** Stav administrace: aktuální složka (cesta), její obsah a případný dialog. */
data class AdminUiState(
    /** Od první složky pod kořenem po aktuální; prázdné = jsme v kořeni. */
    val path: List<Folder> = emptyList(),
    val items: List<DictionaryItem> = emptyList(),
    val isLoading: Boolean = true,
    val dialog: AdminDialog? = null,
)

sealed interface AdminEvent {
    /** Přejde do složky; `null` je kořen ("Domů"). */
    data class GoToFolder(val folderId: String?) : AdminEvent
    data object NavigateUp : AdminEvent
    data object AddFolder : AdminEvent
    data object AddWord : AdminEvent
    data class EditItem(val item: DictionaryItem) : AdminEvent
    data class TitleChanged(val title: String) : AdminEvent
    data object ConfirmEdit : AdminEvent
    data class DeleteItem(val item: DictionaryItem) : AdminEvent
    data object ConfirmDelete : AdminEvent
    data object DismissDialog : AdminEvent
}
