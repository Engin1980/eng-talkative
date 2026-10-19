package cz.talkative.app.domain.ordering

import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceOrderingTest {

    private fun sentence(vararg titles: String) =
        titles.mapIndexed { i, t -> SentenceEntry(i, Word("w$i", "default", t, null, i, 0)) }

    private fun List<SentenceEntry>.titles() = map { it.word.title }

    private val sentence = sentence("a", "b", "c", "d")

    @Test
    fun moveToTheFront() {
        assertEquals(listOf("c", "a", "b", "d"), SentenceOrdering.moved(sentence, entryId = 2, toIndex = 0).titles())
    }

    @Test
    fun moveToTheEnd() {
        assertEquals(listOf("b", "c", "d", "a"), SentenceOrdering.moved(sentence, entryId = 0, toIndex = 3).titles())
    }

    @Test
    fun moveToAnArbitraryPosition_indexCountsWithoutTheMovedWord() {
        // bez "a" zbývá b, c, d; pozice 2 je mezi c a d
        assertEquals(listOf("b", "c", "a", "d"), SentenceOrdering.moved(sentence, entryId = 0, toIndex = 2).titles())
        assertEquals(listOf("a", "c", "b", "d"), SentenceOrdering.moved(sentence, entryId = 1, toIndex = 2).titles())
    }

    @Test
    fun moveToTheSamePlace_changesNothing() {
        assertEquals(sentence, SentenceOrdering.moved(sentence, entryId = 1, toIndex = 1))
        assertEquals(sentence, SentenceOrdering.moved(sentence, entryId = 0, toIndex = 0))
        assertEquals(sentence, SentenceOrdering.moved(sentence, entryId = 3, toIndex = 3))
    }

    @Test
    fun indexOutOfRange_isClamped() {
        assertEquals(listOf("d", "a", "b", "c"), SentenceOrdering.moved(sentence, entryId = 3, toIndex = -5).titles())
        assertEquals(listOf("b", "c", "d", "a"), SentenceOrdering.moved(sentence, entryId = 0, toIndex = 99).titles())
    }

    @Test
    fun unknownEntry_changesNothing() {
        assertEquals(sentence, SentenceOrdering.moved(sentence, entryId = 42, toIndex = 1))
    }

    @Test
    fun sameWordTwice_movesOnlyTheChosenOccurrence() {
        val twice = sentence("a", "b", "a")

        val result = SentenceOrdering.moved(twice, entryId = 2, toIndex = 0)

        assertEquals(listOf(2, 0, 1), result.map { it.entryId })
    }

    @Test
    fun everyWordIsKeptExactlyOnce() {
        val result = SentenceOrdering.moved(sentence, entryId = 1, toIndex = 3)

        assertEquals(sentence.map { it.entryId }.sorted(), result.map { it.entryId }.sorted())
    }

    @Test
    fun emptySentence_staysEmpty() {
        assertEquals(emptyList<SentenceEntry>(), SentenceOrdering.moved(emptyList(), entryId = 0, toIndex = 0))
    }
}
