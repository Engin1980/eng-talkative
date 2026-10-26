package cz.talkative.app.domain.integrity

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word

/** Čistá logika kontroly slovníku bez závislosti na Androidu. */
object DictionaryIntegrity {

    /** Problémy se soubory jednoho slovíčka; [hasImage] a [hasAudio] říkají, zda soubor daného názvu existuje. */
    fun fileProblems(word: Word, hasImage: (String) -> Boolean, hasAudio: (String) -> Boolean): Set<ItemProblem> =
        buildSet {
            if (word.imageFile != null && !hasImage(word.imageFile)) add(ItemProblem.MISSING_IMAGE)
            if (word.audioFile != null && !hasAudio(word.audioFile)) add(ItemProblem.MISSING_AUDIO)
        }

    /** ID složek a slovíček, ke kterým se nejde dostat z kořene (rodič chybí, je smazaný, nebo jsou složky v cyklu). */
    fun unreachableIds(folders: List<Folder>, words: List<Word>): Set<String> {
        val reachable = reachableFolderIds(folders, movedToRoot = emptySet())
        return folders.filter { it.id !in reachable }.map { it.id }.toSet() +
            words.filter { it.parentId != null && it.parentId !in reachable }.map { it.id }
    }

    /**
     * ID položek, které je třeba přesunout do jedné složky „Nalezené“, aby bylo opět dosažitelné všechno:
     * položky s chybějícím rodičem a z každého cyklu složek jedna (s nejmenším ID, aby výsledek byl na všech zařízeních stejný).
     * Položky uvnitř přesouvané složky se přesouvají s ní.
     */
    fun recoveryMoves(folders: List<Folder>, words: List<Word>): Set<String> {
        val folderIds = folders.map { it.id }.toSet()
        val moves = mutableSetOf<String>()
        moves += folders.filter { it.parentId != null && it.parentId !in folderIds }.map { it.id }
        moves += words.filter { it.parentId != null && it.parentId !in folderIds }.map { it.id }
        // Zbývají složky v cyklu (nebo visící na cyklu); každým kolem se přesune jedna, takže to skončí.
        while (true) {
            val reachable = reachableFolderIds(folders, moves)
            val stuck = folders.filter { it.id !in reachable }
            if (stuck.isEmpty()) return moves
            val byId = folders.associateBy { it.id }
            val inCycle = stuck.filter { start ->
                var current = byId[start.id]?.parentId
                val seen = mutableSetOf<String>()
                while (current != null && current != start.id && seen.add(current)) current = byId[current]?.parentId
                current == start.id
            }
            moves += (inCycle.ifEmpty { stuck }).minOf { it.id }
        }
    }

    private fun reachableFolderIds(folders: List<Folder>, movedToRoot: Set<String>): Set<String> {
        val byParent = folders.groupBy { if (it.id in movedToRoot) null else it.parentId }
        val reachable = mutableSetOf<String>()
        val queue = ArrayDeque(byParent[null].orEmpty().map { it.id })
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!reachable.add(id)) continue
            byParent[id]?.forEach { if (it.id !in movedToRoot) queue += it.id }
        }
        return reachable
    }
}
