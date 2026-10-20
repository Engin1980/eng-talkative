package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.repository.ImportedFolder

/** Co z archivu se importuje. */
sealed interface ImportSelection {
    /** Celý archiv. */
    data object All : ImportSelection

    /**
     * Jen vybrané složky, každá i s celým podstromem; [includeRootWords] přidá slovíčka z kořene archivu.
     * Když je vybraná složka i její podsložka, importuje se jednou (podsložka zůstane uvnitř).
     */
    data class Folders(val folderIds: Set<String>, val includeRootWords: Boolean = false) : ImportSelection
}

/** Slovíčko k importu: [parentKey] je ID složky z archivu (`null` = cíl importu), [sortOrder] pořadí uvnitř složky. */
data class PlannedWord(val word: ArchiveWord, val parentKey: String?, val sortOrder: Int)

data class ImportPlan(val folders: List<ImportedFolder>, val words: List<PlannedWord>)

/** Složka archivu a její hloubka v celém stromu, v pořadí, v jakém se zobrazují. */
data class ArchiveFolderRow(val folder: ArchiveFolder, val depth: Int)

private sealed interface Entry {
    val sortOrder: Int
    val index: Int
    val id: String

    data class FolderEntry(val folder: ArchiveFolder, override val index: Int) : Entry {
        override val sortOrder get() = folder.sortOrder
        override val id get() = folder.id
    }

    data class WordEntry(val word: ArchiveWord, override val index: Int) : Entry {
        override val sortOrder get() = word.sortOrder
        override val id get() = word.id
    }
}

/** Děti složky (`null` = kořen archivu) v pořadí: podle `sortOrder`, při shodě složky před slovíčky, pak podle pořadí v souboru. */
private fun children(manifest: ArchiveManifest, parentId: String?): List<Entry> {
    val entries = manifest.folders.withIndex().filter { it.value.parentId == parentId }.map { Entry.FolderEntry(it.value, it.index) } +
        manifest.words.withIndex().filter { it.value.parentId == parentId }.map { Entry.WordEntry(it.value, it.index) }
    return entries.sortedWith(compareBy<Entry>({ it.sortOrder }, { it is Entry.WordEntry }, { it.index }))
}

/** Všechny složky archivu v pořadí zobrazení (do hloubky), pro výběr složek k importu. */
fun listFolders(manifest: ArchiveManifest): List<ArchiveFolderRow> {
    val rows = mutableListOf<ArchiveFolderRow>()
    fun visit(parentId: String?, depth: Int) {
        children(manifest, parentId).filterIsInstance<Entry.FolderEntry>().forEach {
            rows += ArchiveFolderRow(it.folder, depth)
            visit(it.folder.id, depth + 1)
        }
    }
    visit(null, 0)
    return rows
}

/**
 * Sestaví z manifestu to, co se má importovat. Pořadí zůstane zachované: uvnitř každé složky se položky přečíslují
 * na 0, 1, 2, ... podle původního pořadí a položky nejvyšší úrovně (parentKey `null`) podle pořadí v celém stromu archivu.
 */
fun planImport(manifest: ArchiveManifest, selection: ImportSelection): ImportPlan {
    val topLevel: List<Entry> = when (selection) {
        ImportSelection.All -> children(manifest, null)
        is ImportSelection.Folders -> {
            val position = treePositions(manifest)
            val chosen = selection.folderIds.filter { id -> manifest.folders.any { it.id == id } }.toSet()
            val roots = manifest.folders.withIndex()
                .filter { it.value.id in chosen && !hasAncestorIn(manifest, it.value, chosen) }
                .map { Entry.FolderEntry(it.value, it.index) }
            val rootWords = if (selection.includeRootWords) children(manifest, null).filterIsInstance<Entry.WordEntry>() else emptyList()
            (roots + rootWords).sortedBy { position.getValue(it.id) }
        }
    }

    val folders = mutableListOf<ImportedFolder>()
    val words = mutableListOf<PlannedWord>()
    fun emit(entries: List<Entry>, parentKey: String?) {
        entries.forEachIndexed { order, entry ->
            when (entry) {
                is Entry.FolderEntry -> {
                    folders += ImportedFolder(entry.folder.id, parentKey, entry.folder.title, order)
                    emit(children(manifest, entry.folder.id), entry.folder.id)
                }
                is Entry.WordEntry -> words += PlannedWord(entry.word, parentKey, order)
            }
        }
    }
    emit(topLevel, null)
    return ImportPlan(folders, words)
}

/** Pořadí všech položek při průchodu stromem do hloubky; podle něj se řadí položky nejvyšší úrovně z různých složek. */
private fun treePositions(manifest: ArchiveManifest): Map<String, Int> {
    val positions = mutableMapOf<String, Int>()
    fun visit(parentId: String?) {
        children(manifest, parentId).forEach {
            positions[it.id] = positions.size
            if (it is Entry.FolderEntry) visit(it.folder.id)
        }
    }
    visit(null)
    return positions
}

private fun hasAncestorIn(manifest: ArchiveManifest, folder: ArchiveFolder, ids: Set<String>): Boolean {
    var parent = folder.parentId
    while (parent != null) {
        if (parent in ids) return true
        parent = manifest.folders.firstOrNull { it.id == parent }?.parentId
    }
    return false
}
