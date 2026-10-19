package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder

enum class ItemKind { FOLDER, WORD }

/** Proč se obrázek nepodařilo vybrat nebo uložit; UI k tomu zobrazí srozumitelnou hlášku. */
enum class ImageProblem {
    UNREADABLE,
    STORAGE_FULL,
    NO_GALLERY,
    NO_CAMERA,
}

/** Proč se MP3 nepodařilo vybrat, uložit nebo přehrát; UI k tomu zobrazí srozumitelnou hlášku. */
enum class AudioProblem {
    UNREADABLE,
    UNSUPPORTED_FORMAT,
    TOO_LONG,
    TOO_LARGE,
    STORAGE_FULL,
    NO_PICKER,
    PLAYBACK_FAILED,
}

/** Proč se přesun nepodařil; nic se přitom nepřesunulo. */
enum class MoveProblem {
    INTO_ITSELF,
    TARGET_MISSING,
}

/** Otevřený dialog nad administrací. */
sealed interface AdminDialog {
    /**
     * Vytvoření ([itemId] je `null`) nebo úprava titulku. U slovíčka i obrázek:
     * [imageFile] je právě vybraný (nový nebo původní) obrázek, [originalImageFile] ten, který slovíčko mělo při otevření.
     * Nový obrázek je uložený v úložišti hned po výběru, ale slovíčku se přiřadí až po „Uložit“ (zrušením se smaže).
     */
    data class Edit(
        val kind: ItemKind,
        val itemId: String?,
        val title: String,
        val showEmptyError: Boolean = false,
        val imageFile: String? = null,
        val originalImageFile: String? = null,
        /** Jiný text pro čtení (slovíčko); prázdný = čte se titulek. Při vlastním MP3 se neukládá. */
        val alternateText: String = "",
        /** Vlastní MP3: [audioFile] je vybrané (nové nebo původní), [originalAudioFile] to, které slovíčko mělo při otevření. */
        val audioFile: String? = null,
        val originalAudioFile: String? = null,
        val isProcessingAudio: Boolean = false,
        val isPreviewingAudio: Boolean = false,
        val audioProblem: AudioProblem? = null,
        val isProcessingImage: Boolean = false,
        val imageProblem: ImageProblem? = null,
        /** Rozlišuje jednotlivá otevření dialogu, aby pozdní výsledek zpracování obrázku nepřistál do jiného dialogu. */
        val sessionId: Int = 0,
    ) : AdminDialog

    /**
     * Výběr cílové složky přesunu: [targetId] je složka, kterou dialog právě ukazuje (`null` = kořen),
     * [path] cesta k ní, [folders] její podsložky a [movingCount] počet přesouvaných položek.
     */
    data class MoveTarget(
        val targetId: String?,
        val path: List<Folder>,
        val folders: List<Folder>,
        val movingCount: Int,
        val problem: MoveProblem? = null,
    ) : AdminDialog

    /** Potvrzení abecedního řazení: přepíše ručně nastavené pořadí a nelze ho vrátit. */
    data object ConfirmSort : AdminDialog

    /**
     * Potvrzení hromadného smazání: [selectedCount] vybraných položek, [totalCount] jich je celkem
     * (vybrané plus obsah vybraných složek).
     */
    data class ConfirmDeleteSelected(val selectedCount: Int, val totalCount: Int) : AdminDialog

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
    /** Režim výběru více položek v aktuální složce; [selectedIds] jsou označené položky. */
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
)

sealed interface AdminEvent {
    /** Přejde do složky; `null` je kořen ("Domů"). */
    data class GoToFolder(val folderId: String?) : AdminEvent
    data object NavigateUp : AdminEvent
    data object AddFolder : AdminEvent
    data object AddWord : AdminEvent
    data class EditItem(val item: DictionaryItem) : AdminEvent
    data class TitleChanged(val title: String) : AdminEvent
    data class AlternateTextChanged(val text: String) : AdminEvent
    data object ConfirmEdit : AdminEvent
    data class DeleteItem(val item: DictionaryItem) : AdminEvent
    data object ConfirmDelete : AdminEvent
    data object DismissDialog : AdminEvent

    /** Systémový výběr (galerie nebo fotoaparát) vrátil obrázek ([uri]) ke zpracování. */
    data class ImagePicked(val uri: String) : AdminEvent

    /** Obrázek slovíčka se při „Uložit“ odebere. */
    data object RemoveImage : AdminEvent

    /** Výběr obrázku se nepodařilo spustit (např. chybí galerie nebo fotoaparát). */
    data class ImageSelectionFailed(val problem: ImageProblem) : AdminEvent

    data object DismissImageProblem : AdminEvent

    /** Systémový výběr souboru vrátil MP3 ([uri]) ke kontrole a uložení. */
    data class AudioPicked(val uri: String) : AdminEvent

    /** MP3 slovíčka se při „Uložit“ odebere. */
    data object RemoveAudio : AdminEvent

    /** Výběr MP3 se nepodařilo spustit (např. chybí aplikace pro výběr souborů). */
    data class AudioSelectionFailed(val problem: AudioProblem) : AdminEvent

    /** Spustí náhled MP3, nebo ho zastaví, když právě hraje. */
    data object ToggleAudioPreview : AdminEvent

    data object DismissAudioProblem : AdminEvent

    /** Zapne nebo vypne režim výběru (vypnutím se výběr zruší). */
    data object ToggleSelecting : AdminEvent
    data class ToggleSelected(val itemId: String) : AdminEvent

    /** Označí všechny položky aktuální složky, nebo je všechny odznačí, když už jsou označené. */
    data object ToggleSelectAll : AdminEvent
    data object DeleteSelected : AdminEvent
    data object ConfirmDeleteSelected : AdminEvent

    /** Posune položku o místo nahoru nebo dolů v ručním pořadí složky. */
    data class MoveItemUp(val itemId: String) : AdminEvent
    data class MoveItemDown(val itemId: String) : AdminEvent

    /** Zeptá se, zda seřadit obsah aktuální složky podle abecedy (přepíše ruční pořadí). */
    data object SortAlphabetically : AdminEvent

    /** Potvrzení dotazu: seřadí obsah aktuální složky podle abecedy (podle kolace z konfigurace). */
    data object ConfirmSortAlphabetically : AdminEvent

    /** Otevře výběr cílové složky pro přesun označených položek. */
    data object MoveSelected : AdminEvent

    /** V dialogu přesunu ukáže jinou složku (`null` = kořen). */
    data class BrowseMoveTarget(val folderId: String?) : AdminEvent
    data object ConfirmMove : AdminEvent
}
