package cz.talkative.app.ui.exchange

import cz.talkative.app.domain.exchange.ArchiveFolderRow
import cz.talkative.app.domain.exchange.ExportError
import cz.talkative.app.domain.exchange.ImportError
import cz.talkative.app.domain.model.Folder

/** Co se právě děje s exportem. */
sealed interface ExportStatus {
    data object Idle : ExportStatus

    /** Zapsáno [done] z [total] souborů médií (obrázky a MP3). */
    data class Running(val done: Int = 0, val total: Int = 0) : ExportStatus

    data class Done(val folders: Int, val words: Int, val missingMedia: Int) : ExportStatus

    data class Failed(val error: ExportError) : ExportStatus
}

/** K čemu se právě vybírá složka. */
enum class PickerPurpose { EXPORT_FOLDER, IMPORT_TARGET }

/** Dialog pro výběr složky: [path] vede ke složce, kterou dialog ukazuje (prázdná = kořen), [folders] jsou její podsložky. */
data class FolderPickerState(
    val path: List<Folder>,
    val folders: List<Folder>,
    val purpose: PickerPurpose = PickerPurpose.EXPORT_FOLDER,
)

/** Co se právě děje s importem. */
sealed interface ImportStatus {
    data object Idle : ImportStatus

    /** Kontroluje se vybraný archiv. */
    data object Reading : ImportStatus

    data object Running : ImportStatus

    data class Done(val folders: Int, val words: Int, val configImported: Boolean) : ImportStatus

    data class Failed(val error: ImportError) : ImportStatus
}

/** Obsah vybraného a zkontrolovaného archivu. */
data class ImportArchiveInfo(
    /** Všechny složky archivu v pořadí zobrazení. */
    val folders: List<ArchiveFolderRow>,
    val folderCount: Int,
    val wordCount: Int,
    /** V kořeni archivu jsou slovíčka (mimo složky). */
    val hasRootWords: Boolean,
    val hasConfig: Boolean,
)

data class ImportUiState(
    /** Zkontrolovaný archiv; `null`, dokud není vybraný nebo když byl odmítnutý. */
    val archive: ImportArchiveInfo? = null,
    val selectAll: Boolean = true,
    /** Zaškrtnuté složky při výběru jen některých; každá platí i s podsložkami. */
    val selectedFolderIds: Set<String> = emptySet(),
    val includeRootWords: Boolean = false,
    /** Cílová složka; `null` = kořen. */
    val target: Folder? = null,
    val replaceAll: Boolean = false,
    val importConfig: Boolean = false,
    /** Kolik složek a slovíček se při současném výběru přidá. */
    val plannedFolders: Int = 0,
    val plannedWords: Int = 0,
    val confirmReplace: Boolean = false,
    val status: ImportStatus = ImportStatus.Idle,
) {
    val isBusy: Boolean get() = status == ImportStatus.Reading || status == ImportStatus.Running

    val canImport: Boolean
        get() = archive != null && !isBusy && (selectAll || selectedFolderIds.isNotEmpty() || includeRootWords)

    /** Složka je zaškrtnutá přímo, nebo je uvnitř zaškrtnuté složky. */
    fun isCovered(folderId: String): Boolean {
        val parents = archive?.folders?.associate { it.folder.id to it.folder.parentId } ?: return false
        var id: String? = folderId
        while (id != null) {
            if (id in selectedFolderIds) return true
            id = parents[id]
        }
        return false
    }
}

data class ExchangeUiState(
    /** Exportovaná složka; `null` = celý slovník. */
    val exportFolder: Folder? = null,
    val includeConfig: Boolean = false,
    val export: ExportStatus = ExportStatus.Idle,
    val picker: FolderPickerState? = null,
    val import: ImportUiState = ImportUiState(),
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

    /** Uživatel v systémovém výběru zvolil archiv [uri] k importu. */
    data class ArchivePicked(val uri: String) : ExchangeEvent

    data class ImportSelectAllChanged(val all: Boolean) : ExchangeEvent
    data class ToggleImportFolder(val folderId: String) : ExchangeEvent
    data class IncludeRootWordsChanged(val include: Boolean) : ExchangeEvent
    data object ChooseImportTarget : ExchangeEvent
    data class ReplaceAllChanged(val replace: Boolean) : ExchangeEvent
    data class ImportConfigChanged(val import: Boolean) : ExchangeEvent

    /** „Importovat“; při nahrazení slovníku se nejdřív zeptá. */
    data object StartImport : ExchangeEvent
    data object ConfirmReplace : ExchangeEvent
    data object DismissReplace : ExchangeEvent
}
