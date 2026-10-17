package cz.talkative.app.domain.paging

/** Čistá logika stránkování mřížky. Stránky se číslují od 0; prázdný seznam má vždy jednu (prázdnou) stránku. */
object Pagination {

    fun pageCount(itemCount: Int, pageSize: Int): Int {
        require(pageSize > 0) { "pageSize must be positive" }
        return maxOf(1, (itemCount + pageSize - 1) / pageSize)
    }

    /** Omezí číslo stránky na existující stránky (např. po smazání položek nebo změně velikosti mřížky). */
    fun clampPage(page: Int, itemCount: Int, pageSize: Int): Int =
        page.coerceIn(0, pageCount(itemCount, pageSize) - 1)

    /** Položky na dané stránce; neplatná stránka se omezí stejně jako v [clampPage]. */
    fun <T> pageItems(items: List<T>, page: Int, pageSize: Int): List<T> {
        val safePage = clampPage(page, items.size, pageSize)
        return items.drop(safePage * pageSize).take(pageSize)
    }
}
