package cz.talkative.app.ui.client

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragDropTest {

    private val word = Word("w1", "default", "chci", null, 0, 0)
    private val bar = Rect(0f, 0f, 400f, 100f)
    private val inside = Offset(200f, 50f)
    private val outside = Offset(200f, 500f)
    private val gridWord = DragSource.GridWord(word)
    private val sentenceWord = DragSource.SentenceWord(entryId = 7, title = "chci")

    @Test
    fun gridWordDroppedOnTheBar_inDragMode_isAdded() {
        assertEquals(ClientEvent.WordDropped(word), resolveDrop(gridWord, inside, bar, AddWordMode.DRAG))
    }

    @Test
    fun gridWordDroppedOnTheBar_inTapMode_isIgnored() {
        assertNull(resolveDrop(gridWord, inside, bar, AddWordMode.TAP))
    }

    @Test
    fun gridWordDroppedElsewhere_isIgnored() {
        assertNull(resolveDrop(gridWord, outside, bar, AddWordMode.DRAG))
    }

    @Test
    fun gridWordDropped_withUnknownBar_isIgnored() {
        assertNull(resolveDrop(gridWord, inside, null, AddWordMode.DRAG))
    }

    @Test
    fun sentenceWordDraggedOutOfTheBar_inDragMode_isRemoved() {
        assertEquals(ClientEvent.RemoveFromSentence(7), resolveDrop(sentenceWord, outside, bar, AddWordMode.DRAG))
    }

    @Test
    fun sentenceWordDraggedOutOfTheBar_inTapMode_isKept() {
        assertNull(resolveDrop(sentenceWord, outside, bar, AddWordMode.TAP))
    }

    @Test
    fun sentenceWordDroppedBackInsideTheBar_isNotRemoved_andNotMovedWhenReorderIsOff() {
        assertNull(resolveDrop(sentenceWord, inside, bar, AddWordMode.DRAG))
    }

    @Test
    fun barEdges_countAsInside_exactlyLikeRectContains() {
        assertEquals(ClientEvent.WordDropped(word), resolveDrop(gridWord, Offset(0f, 0f), bar, AddWordMode.DRAG))
        assertNull(resolveDrop(gridWord, Offset(400f, 100f), bar, AddWordMode.DRAG)) // pravý dolní okraj už je venku
    }

    // --- změna pořadí ve větě (7.4)

    /** Tři slova v jednom řádku: střed 50, 150 a 250. */
    private val row = listOf(
        1 to Rect(0f, 0f, 100f, 100f),
        2 to Rect(100f, 0f, 200f, 100f),
        3 to Rect(200f, 0f, 300f, 100f),
    )
    private val wideBar = Rect(0f, 0f, 400f, 100f)

    private fun reorder(entryId: Int, x: Float, allow: Boolean = true, mode: AddWordMode = AddWordMode.TAP) =
        resolveDrop(DragSource.SentenceWord(entryId, "x"), Offset(x, 50f), wideBar, mode, allow, row)

    @Test
    fun dropIndex_countsWordsWhoseCenterIsLeftOfTheFinger() {
        val rects = row.map { it.second }

        assertEquals(0, dropIndex(rects, Offset(10f, 50f)))
        assertEquals(1, dropIndex(rects, Offset(60f, 50f)))
        assertEquals(2, dropIndex(rects, Offset(160f, 50f)))
        assertEquals(3, dropIndex(rects, Offset(390f, 50f)))
    }

    @Test
    fun dropIndex_worksWithWrappedRows() {
        val rects = listOf(
            Rect(0f, 0f, 100f, 50f), Rect(100f, 0f, 200f, 50f), // první řádek
            Rect(0f, 50f, 100f, 100f), Rect(100f, 50f, 200f, 100f), // druhý řádek
        )

        assertEquals(0, dropIndex(rects, Offset(10f, 10f)))
        assertEquals(2, dropIndex(rects, Offset(300f, 10f))) // za koncem prvního řádku
        assertEquals(2, dropIndex(rects, Offset(10f, 80f))) // na začátku druhého řádku
        assertEquals(3, dropIndex(rects, Offset(120f, 80f))) // před středem posledního slova
        assertEquals(4, dropIndex(rects, Offset(160f, 80f)))
        assertEquals(4, dropIndex(rects, Offset(300f, 80f)))
    }

    @Test
    fun dropIndex_withNoOtherWords_isZero() {
        assertEquals(0, dropIndex(emptyList(), Offset(10f, 10f)))
    }

    @Test
    fun draggingTheFirstWordToTheEnd_movesItToTheLastPosition() {
        assertEquals(ClientEvent.MoveInSentence(1, 2), reorder(entryId = 1, x = 390f))
    }

    @Test
    fun draggingTheLastWordToTheFront_movesItToTheFirstPosition() {
        assertEquals(ClientEvent.MoveInSentence(3, 0), reorder(entryId = 3, x = 10f))
    }

    @Test
    fun draggingAWordBetweenTwoOthers_movesItThere() {
        assertEquals(ClientEvent.MoveInSentence(1, 1), reorder(entryId = 1, x = 160f)) // mezi 2 a 3
    }

    @Test
    fun reorder_whenDisabled_doesNothing_inBothModes() {
        assertNull(reorder(entryId = 1, x = 390f, allow = false, mode = AddWordMode.TAP))
        assertNull(reorder(entryId = 1, x = 390f, allow = false, mode = AddWordMode.DRAG))
    }

    @Test
    fun reorder_works_inDragModeToo_andDraggingOutStillRemoves() {
        assertEquals(ClientEvent.MoveInSentence(1, 2), reorder(entryId = 1, x = 390f, mode = AddWordMode.DRAG))
        assertEquals(
            ClientEvent.RemoveFromSentence(1),
            resolveDrop(DragSource.SentenceWord(1, "x"), outside, bar, AddWordMode.DRAG, allowReorder = true, chips = row),
        )
    }

    @Test
    fun draggingOutOfTheBar_inTapMode_doesNothingEvenWithReorderAllowed() {
        assertNull(resolveDrop(DragSource.SentenceWord(1, "x"), outside, bar, AddWordMode.TAP, allowReorder = true, chips = row))
    }
}
