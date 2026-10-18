package cz.talkative.app.domain.ordering

import cz.talkative.app.domain.model.DictionaryItem
import java.text.Collator
import java.util.Locale

/** Čisté funkce pro výpočet nového pořadí položek ve složce; ukládání pořadí je věc repozitáře. */
object ItemOrdering {

    /**
     * Posune položku [id] o [offset] míst (záporné = nahoru, kladné = dolů) a vrátí nové pořadí ID.
     * Vrací `null`, když položka v seznamu není nebo by vypadla za jeho okraj.
     */
    fun moved(ids: List<String>, id: String, offset: Int): List<String>? {
        val from = ids.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in ids.indices) return null
        val result = ids.toMutableList()
        result.removeAt(from)
        result.add(to, id)
        return result
    }

    /** ID položek seřazených podle titulku podle pravidel jazyka [languageTag]; stejné titulky zůstanou ve stávajícím pořadí. */
    fun alphabetical(items: List<DictionaryItem>, languageTag: String): List<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag(languageTag))
        return items.sortedWith { a, b -> collator.compare(a.title, b.title) }.map { it.id }
    }
}
