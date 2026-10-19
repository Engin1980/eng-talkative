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
    fun sentenceWordDroppedBackInsideTheBar_isNotRemoved() {
        assertNull(resolveDrop(sentenceWord, inside, bar, AddWordMode.DRAG))
    }

    @Test
    fun barEdges_countAsInside_exactlyLikeRectContains() {
        assertEquals(ClientEvent.WordDropped(word), resolveDrop(gridWord, Offset(0f, 0f), bar, AddWordMode.DRAG))
        assertNull(resolveDrop(gridWord, Offset(400f, 100f), bar, AddWordMode.DRAG)) // pravý dolní okraj už je venku
    }
}
