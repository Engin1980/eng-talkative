package cz.talkative.app.domain.ordering

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemOrderingTest {

    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun moved_up_swapsWithPreviousNeighbour() {
        assertEquals(listOf("a", "c", "b", "d"), ItemOrdering.moved(ids, "c", -1))
    }

    @Test
    fun moved_down_swapsWithNextNeighbour() {
        assertEquals(listOf("b", "a", "c", "d"), ItemOrdering.moved(ids, "a", 1))
    }

    @Test
    fun moved_lastItemDown_andFirstItemUp_changeNothing() {
        assertNull(ItemOrdering.moved(ids, "d", 1))
        assertNull(ItemOrdering.moved(ids, "a", -1))
    }

    @Test
    fun moved_unknownItem_changesNothing() {
        assertNull(ItemOrdering.moved(ids, "x", 1))
    }

    @Test
    fun moved_keepsEveryItemExactlyOnce() {
        val result = ItemOrdering.moved(ids, "b", 2)!!
        assertEquals(listOf("a", "c", "d", "b"), result)
        assertEquals(ids.sorted(), result.sorted())
    }

    private fun words(vararg titles: String): List<DictionaryItem> =
        titles.mapIndexed { i, t -> Word("w$i", "default", t, null, i, 0) }

    private fun titlesInOrder(items: List<DictionaryItem>, languageTag: String): List<String> {
        val byId = items.associateBy { it.id }
        return ItemOrdering.alphabetical(items, languageTag).map { byId.getValue(it).title }
    }

    @Test
    fun alphabetical_czech_ordersCibuleBeforeChleb_andChAfterH() {
        val result = titlesInOrder(words("chléb", "ibis", "hrách", "cibule", "dům", "ahoj"), "cs")

        // Česká abeceda: ... c, č, d, ..., h, ch, i ...
        assertEquals(listOf("ahoj", "cibule", "dům", "hrách", "chléb", "ibis"), result)
    }

    @Test
    fun alphabetical_czech_putsCcaronAfterC_andDiacriticsRightAfterTheirBaseLetter() {
        val result = titlesInOrder(words("dům", "čaj", "cibule", "áno", "ano", "bar"), "cs")

        assertEquals(listOf("ano", "áno", "bar", "cibule", "čaj", "dům"), result)
    }

    @Test
    fun alphabetical_ignoresCaseOfFirstLetter() {
        val result = titlesInOrder(words("zebra", "Ahoj", "banán"), "cs")

        assertEquals(listOf("Ahoj", "banán", "zebra"), result)
    }

    @Test
    fun alphabetical_otherLanguage_givesOtherOrder() {
        val items = words("dům", "chléb", "cibule", "hrách")

        val czech = titlesInOrder(items, "cs")
        val english = titlesInOrder(items, "en")

        assertEquals(listOf("cibule", "dům", "hrách", "chléb"), czech)
        assertEquals(listOf("chléb", "cibule", "dům", "hrách"), english) // v angličtině je "ch" jen c + h
    }

    @Test
    fun alphabetical_foldersAndWordsTogether_equalTitlesKeepTheirOrder() {
        val items: List<DictionaryItem> = listOf(
            Word("w1", "default", "Jídlo", null, 0, 0),
            Folder("f1", "default", "Jídlo", null, 1, 0),
            Folder("f2", "default", "Auto", null, 2, 0),
        )

        assertEquals(listOf("f2", "w1", "f1"), ItemOrdering.alphabetical(items, "cs"))
    }
}
