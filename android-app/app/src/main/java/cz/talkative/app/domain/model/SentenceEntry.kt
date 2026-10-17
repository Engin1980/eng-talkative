package cz.talkative.app.domain.model

/**
 * Jedno slovo ve větě. Stejné slovíčko může být ve větě víckrát, proto má každý výskyt vlastní [entryId].
 * Drží snapshot [word] z okamžiku přidání, takže věta se nemění, když administrátor mezitím slovíčko upraví.
 */
data class SentenceEntry(val entryId: Int, val word: Word)
