package cz.talkative.app.data.repository

import cz.talkative.app.data.local.DictionaryDao
import cz.talkative.app.data.local.FolderEntity
import cz.talkative.app.data.local.WordEntity
import cz.talkative.app.data.local.toDomain
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.repository.DictionaryRepository
import cz.talkative.app.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

class RoomDictionaryRepository @Inject constructor(
    private val dao: DictionaryDao,
    private val clock: Clock,
) : DictionaryRepository {

    // Zatím existuje jen výchozí profil.
    private val profileId = Profile.DEFAULT_ID

    override fun observeItems(parentId: String?): Flow<List<DictionaryItem>> =
        combine(
            dao.observeFolders(profileId, parentId),
            dao.observeWords(profileId, parentId),
        ) { folders, words ->
            val items: List<DictionaryItem> = folders.map { it.toDomain() } + words.map { it.toDomain() }
            items.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }

    override suspend fun getPath(folderId: String?): List<Folder> {
        val path = ArrayDeque<Folder>()
        val seen = mutableSetOf<String>() // ochrana proti cyklu v poškozených datech
        var id = folderId
        while (id != null && seen.add(id)) {
            val folder = dao.getFolder(profileId, id)?.toDomain() ?: break
            path.addFirst(folder)
            id = folder.parentId
        }
        return path.toList()
    }

    override suspend fun createFolder(parentId: String?, title: String): Folder =
        dao.addFolder(
            FolderEntity(newId(), profileId, title, parentId, sortOrder = 0, updatedAt = clock.nowMillis()),
        ).toDomain()

    override suspend fun createWord(parentId: String?, title: String): Word =
        dao.addWord(
            WordEntity(newId(), profileId, title, parentId, sortOrder = 0, updatedAt = clock.nowMillis()),
        ).toDomain()

    override suspend fun updateFolderTitle(id: String, title: String) =
        dao.updateFolderTitle(profileId, id, title, clock.nowMillis())

    override suspend fun updateWordTitle(id: String, title: String) =
        dao.updateWordTitle(profileId, id, title, clock.nowMillis())

    override suspend fun countFolderContents(folderId: String): Int {
        val folders = dao.countSubtreeFolders(profileId, folderId)
        if (folders == 0) return 0 // složka neexistuje
        return (folders - 1) + dao.countSubtreeWords(profileId, folderId)
    }

    override suspend fun deleteFolder(id: String) = dao.deleteFolderSubtree(profileId, id, clock.nowMillis())

    override suspend fun deleteWord(id: String) = dao.deleteWord(profileId, id, clock.nowMillis())

    private fun newId() = UUID.randomUUID().toString()
}
