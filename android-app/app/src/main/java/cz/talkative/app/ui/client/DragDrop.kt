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
 * Na které místo ve větě se slovo pustilo: počet slov z [chips] (plochy ostatních slov ve větě v jejím pořadí),
 * která jsou před místem [pointer]. Slovo je před ním, když leží celé v řádku nad ním, nebo je ve stejném řádku
 * a jeho střed je vlevo od prstu. Funguje i pro slova zalomená do více řádků.
 */
fun dropIndex(chips: List<Rect>, pointer: Offset): Int = chips.count { chip ->
    when {
        chip.bottom <= pointer.y -> true
        chip.top > pointer.y -> false
        else -> chip.center.x < pointer.x
    }
}

/**
 * Rozhodne, co udělá puštění taženého prvku na místě [pointer] (souřadnice okna). Čistá logika bez Compose gest.
 * [barBounds] je plocha řádku věty. Vrací událost pro view model, nebo `null`, když se nemá nic stát.
 *
 * - Slovo z mřížky puštěné do věty se přidá, ale jen v režimu tažení (v režimu klepnutí tažení nic nedělá).
 * - Slovo z věty puštěné mimo řádek věty se odebere, opět jen v režimu tažení.
 * - Slovo z věty puštěné uvnitř řádku věty se přesune na místo pod prstem, ale jen když je změna pořadí povolená.
 *   [chips] jsou plochy slov ve větě v jejím pořadí (včetně taženého).
 */
fun resolveDrop(
    source: DragSource,
    pointer: Offset,
    barBounds: Rect?,
    addMode: AddWordMode,
    allowReorder: Boolean = false,
    chips: List<Pair<Int, Rect>> = emptyList(),
): ClientEvent? {
    val insideBar = barBounds?.contains(pointer) == true
    return when (source) {
        is DragSource.GridWord ->
            if (insideBar && addMode == AddWordMode.DRAG) ClientEvent.WordDropped(source.word) else null
        is DragSource.SentenceWord -> when {
            insideBar && allowReorder -> {
                val others = chips.filter { it.first != source.entryId }.map { it.second }
                ClientEvent.MoveInSentence(source.entryId, dropIndex(others, pointer))
            }
            !insideBar && addMode == AddWordMode.DRAG -> ClientEvent.RemoveFromSentence(source.entryId)
            else -> null
        }
    }
}
