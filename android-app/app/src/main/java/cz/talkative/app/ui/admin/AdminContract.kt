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
}
