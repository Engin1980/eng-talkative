package cz.talkative.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.ordering.ItemOrdering
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Abecední řazení na skutečném Androidu (ICU), ne jen na JVM: čeština řadí "ch" za "h" a "č" za "c". */
@RunWith(AndroidJUnit4::class)
class CollationOnDeviceTest {

    private fun order(languageTag: String, vararg titles: String): List<String> {
        val items: List<DictionaryItem> = titles.mapIndexed { i, t -> Word("w$i", "default", t, null, i, 0) }
        val byId = items.associateBy { it.id }
        return ItemOrdering.alphabetical(items, languageTag).map { byId.getValue(it).title }
    }

    @Test
    fun czech_followsTheCzechAlphabet() {
        assertEquals(
            listOf("ahoj", "cibule", "čaj", "dům", "hrách", "chléb", "ibis"),
            order("cs", "chléb", "ibis", "hrách", "čaj", "cibule", "dům", "ahoj"),
        )
    }

    @Test
    fun diacritics_sortRightAfterTheBaseLetter() {
        assertEquals(listOf("ano", "áno", "bar"), order("cs", "bar", "áno", "ano"))
    }

    @Test
    fun otherLanguage_changesTheResult() {
        assertEquals(listOf("cibule", "dům", "chléb"), order("cs", "dům", "chléb", "cibule"))
        assertEquals(listOf("chléb", "cibule", "dům"), order("en", "dům", "chléb", "cibule"))
    }
}
