package cz.talkative.app.ui.arasaac

import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramError

/** Stav hledání. */
sealed interface SearchStatus {
    /** Ještě se nehledalo (nebo bylo zadání prázdné). */
    data object NotSearched : SearchStatus

    data object Searching : SearchStatus

    data class Results(val pictograms: List<Pictogram>) : SearchStatus

    /** Hledání proběhlo, ale nic se nenašlo. */
    data object Nothing : SearchStatus

    data class Failed(val error: PictogramError) : SearchStatus
}

data class ArasaacUiState(
    /** Text v hledacím poli; zpočátku titulek slovíčka. */
    val query: String = "",
    val search: SearchStatus = SearchStatus.NotSearched,
    /** ID piktogramu, který se právě stahuje; další klepnutí se tou dobou ignoruje. */
    val downloadingId: Int? = null,
    val downloadError: PictogramError? = null,
    /** Stažený piktogram (URI souboru v cache), který má převzít editace slovíčka; po převzetí se vynuluje. */
    val pickedImageUri: String? = null,
)

sealed interface ArasaacEvent {
    data class QueryChanged(val query: String) : ArasaacEvent

    /** Hledat podle textu v poli (po potvrzení, ne při každém písmenu). */
    data object Search : ArasaacEvent

    /** Zopakovat poslední hledání. */
    data object Retry : ArasaacEvent

    data class Select(val pictogram: Pictogram) : ArasaacEvent

    /** Editace slovíčka si [ArasaacUiState.pickedImageUri] převzala. */
    data object PickedConsumed : ArasaacEvent
}
