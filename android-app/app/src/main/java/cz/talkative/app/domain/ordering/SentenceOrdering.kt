package cz.talkative.app.domain.ordering

import cz.talkative.app.domain.model.SentenceEntry

/** Změna pořadí slov ve větě; čistá logika bez závislosti na UI. */
object SentenceOrdering {

    /**
     * Přesune slovo s [entryId] tak, aby bylo ve výsledku na pozici [toIndex] (0 = první).
     * Pozice se počítá ve větě bez přesouvaného slova; mimo rozsah se srazí na začátek či konec.
     * Neznámé [entryId] větu nezmění.
     */
    fun moved(sentence: List<SentenceEntry>, entryId: Int, toIndex: Int): List<SentenceEntry> {
        val entry = sentence.firstOrNull { it.entryId == entryId } ?: return sentence
        val others = sentence.filterNot { it.entryId == entryId }
        return others.toMutableList().apply { add(toIndex.coerceIn(0, others.size), entry) }
    }
}
