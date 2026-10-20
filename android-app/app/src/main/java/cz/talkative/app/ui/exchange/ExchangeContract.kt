package cz.talkative.app.ui.exchange

import cz.talkative.app.domain.exchange.ExportError
import cz.talkative.app.domain.model.Folder

/** Co se právě děje s exportem. */
sealed interface ExportStatus {
    data object Idle : ExportStatus

    /** Zapsáno [done] z [total] souborů médií (obrázky a MP3). */
    data class Running(val done: Int = 0, val total: Int = 0) : ExportStatus

    data class Done(val folders: Int, val words: Int, val missingMedia: Int) : ExportStatus

    data class Failed(val error: ExportError) : ExportStatus
}

/** Dialog pro výběr složky: [path] vede ke složce, kterou dialog ukazuje (prázdná = kořen), [folders] jsou její podsložky. */
data class FolderPickerState(val path: List<Folder>, val folders: List<Folder>)

data class ExchangeUiState(
    /** Exportovaná složka; `null` = celý slovník. */
    val exportFolder: Folder? = null,
    val includeConfig: Boolean = false,
    val export: ExportStatus = ExportStatus.Idle,
    val picker: FolderPickerState? = null,
)

sealed interface ExchangeEvent {
    /** Otevře výběr složky pro export. */
    data object ChooseExportFolder : ExchangeEvent
    data class BrowsePicker(val folderId: String?) : ExchangeEvent
    data object ConfirmPicker : ExchangeEvent
    data object DismissPicker : ExchangeEvent

    /** Exportovat celý slovník. */
    data object ExportWholeDictionary : ExchangeEvent
    data class IncludeConfigChanged(val include: Boolean) : ExchangeEvent

    /** Uživatel v systémovém výběru zvolil soubor [uri], do kterého se má archiv zapsat. */
    data class ExportToFile(val uri: String) : ExchangeEvent
}
