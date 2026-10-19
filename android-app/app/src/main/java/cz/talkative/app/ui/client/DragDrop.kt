package cz.talkative.app.ui.client

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.Word

/** Co se právě táhne: slovo z mřížky, nebo slovo (konkrétní výskyt) z věty. */
sealed interface DragSource {
    val title: String

    data class GridWord(val word: Word) : DragSource {
        override val title: String get() = word.title
    }

    data class SentenceWord(val entryId: Int, override val title: String) : DragSource
}

/**
 * Rozhodne, co udělá puštění taženého prvku na místě [pointer] (souřadnice okna). Čistá logika bez Compose gest.
 * [barBounds] je plocha řádku věty. Vrací událost pro view model, nebo `null`, když se nemá nic stát.
 *
 * - Slovo z mřížky puštěné do věty se přidá, ale jen v režimu tažení (v režimu klepnutí tažení nic nedělá).
 * - Slovo z věty puštěné mimo řádek věty se odebere, opět jen v režimu tažení.
 */
fun resolveDrop(
    source: DragSource,
    pointer: Offset,
    barBounds: Rect?,
    addMode: AddWordMode,
): ClientEvent? {
    val insideBar = barBounds?.contains(pointer) == true
    return when (source) {
        is DragSource.GridWord ->
            if (insideBar && addMode == AddWordMode.DRAG) ClientEvent.WordDropped(source.word) else null
        is DragSource.SentenceWord ->
            if (!insideBar && addMode == AddWordMode.DRAG) ClientEvent.RemoveFromSentence(source.entryId) else null
    }
}
