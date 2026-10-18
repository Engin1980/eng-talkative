package cz.talkative.app.domain

import cz.talkative.app.domain.paging.Pagination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaginationTest {

    private val nine = 9 // mřížka 3×3

    @Test
    fun tenItemsInThreeByThree_areTwoPages() {
        assertEquals(2, Pagination.pageCount(itemCount = 10, pageSize = nine))
    }

    @Test
    fun emptyFolder_hasOneEmptyPage() {
        assertEquals(1, Pagination.pageCount(0, nine))
        assertEquals(emptyList<Int>(), Pagination.pageItems(emptyList<Int>(), 0, nine))
    }

    @Test
    fun exactMultipleOfPageSize_hasNoExtraEmptyPage() {
        assertEquals(1, Pagination.pageCount(9, nine))
        assertEquals(2, Pagination.pageCount(18, nine))
        assertEquals(3, Pagination.pageCount(19, nine))
    }

    @Test
    fun pageItems_splitsInOrder() {
        val items = (1..10).toList()

        assertEquals((1..9).toList(), Pagination.pageItems(items, 0, nine))
        assertEquals(listOf(10), Pagination.pageItems(items, 1, nine))
    }

    @Test
    fun pageItems_ofExactMultiple_lastPageIsFull() {
        val items = (1..18).toList()

        assertEquals((10..18).toList(), Pagination.pageItems(items, 1, nine))
    }

    @Test
    fun clampPage_keepsPageInRange() {
        assertEquals(1, Pagination.clampPage(5, itemCount = 10, pageSize = nine))
        assertEquals(0, Pagination.clampPage(-3, itemCount = 10, pageSize = nine))
        assertEquals(1, Pagination.clampPage(1, itemCount = 10, pageSize = nine))
        assertEquals(0, Pagination.clampPage(4, itemCount = 0, pageSize = nine))
    }

    @Test
    fun biggerGrid_meansFewerPages() {
        // otočení na šířku: 4×3 = 12 položek na stránku
        assertEquals(1, Pagination.pageCount(10, pageSize = 12))
        assertEquals(2, Pagination.pageCount(13, pageSize = 12))
        // a stránka vybraná v menší mřížce se po zvětšení mřížky omezí
        assertEquals(0, Pagination.clampPage(1, itemCount = 10, pageSize = 12))
    }

    @Test
    fun nonPositivePageSize_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { Pagination.pageCount(5, 0) }
    }
}
