package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Jednoduchý repozitář v paměti pro testy view modelu (bez Room). */
class FakeDictionaryRepository : DictionaryRepository {
    private val folders = MutableStateFlow<List<Folder>>(emptyList())
    private val words = MutableStateFlow<List<Word>>(emptyList())
    private var counter = 0

    val allFolders: List<Folder> get() = folders.value
    val allWords: List<Word> get() = words.value

    override fun observeItems(parentId: String?): Flow<List<DictionaryItem>> =
        kotlinx.coroutines.flow.combine(folders, words) { f, w ->
            val items: List<DictionaryItem> = f.filter { it.parentId == parentId } + w.filter { it.parentId == parentId }
            items.sortedBy { it.sortOrder }
        }

    override suspend fun getPath(folderId: String?): List<Folder> {
        val path = ArrayDeque<Folder>()
        var id = folderId
        while (id != null) {
            val folder = folders.value.firstOrNull { it.id == id } ?: break
            path.addFirst(folder)
            id = folder.parentId
        }
        return path.toList()
    }

    override suspend fun createFolder(parentId: String?, title: String): Folder {
        val folder = Folder("f${counter++}", Profile.DEFAULT_ID, title, parentId, nextOrder(parentId), 0)
        folders.value += folder
        return folder
    }

    override suspend fun createWord(parentId: String?, title: String, imageFile: String?): Word {
        val word = Word("w${counter++}", Profile.DEFAULT_ID, title, parentId, nextOrder(parentId), 0, imageFile)
        words.value += word
        return word
    }

    override suspend fun updateFolderTitle(id: String, title: String) {
        folders.value = folders.value.map { if (it.id == id) it.copy(title = title) else it }
    }

    override suspend fun updateWordTitle(id: String, title: String) {
        words.value = words.value.map { if (it.id == id) it.copy(title = title) else it }
    }

    override suspend fun updateWordImage(id: String, imageFile: String?) {
        words.value = words.value.map { if (it.id == id) it.copy(imageFile = imageFile) else it }
    }

    override suspend fun cleanUpOrphanImages() = Unit

    override suspend fun countFolderContents(folderId: String): Int {
        val ids = subtreeIds(folderId)
        return (ids.size - 1) + words.value.count { it.parentId in ids }
    }

    override suspend fun deleteFolder(id: String) {
        val ids = subtreeIds(id)
        folders.value = folders.value.filterNot { it.id in ids }
        words.value = words.value.filterNot { it.parentId in ids }
    }

    override suspend fun deleteWord(id: String) {
        words.value = words.value.filterNot { it.id == id }
    }

    private fun subtreeIds(rootId: String): Set<String> {
        val ids = mutableSetOf(rootId)
        var added = true
        while (added) {
            added = ids.addAll(folders.value.filter { it.parentId in ids }.map { it.id })
        }
        return ids
    }

    private fun nextOrder(parentId: String?): Int =
        (folders.value.filter { it.parentId == parentId }.map { it.sortOrder } +
            words.value.filter { it.parentId == parentId }.map { it.sortOrder }).maxOrNull()?.plus(1) ?: 0
}
