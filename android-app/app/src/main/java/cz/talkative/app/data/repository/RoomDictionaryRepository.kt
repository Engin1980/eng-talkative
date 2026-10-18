package cz.talkative.app.data.repository

import cz.talkative.app.data.local.DictionaryDao
import cz.talkative.app.data.local.FolderEntity
import cz.talkative.app.data.local.WordEntity
import cz.talkative.app.data.local.WordFiles
import cz.talkative.app.data.local.toDomain
import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.image.ImageStore
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
    private val imageStore: ImageStore,
    private val audioStore: AudioStore,
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

    override suspend fun createWord(
        parentId: String?,
        title: String,
        imageFile: String?,
        alternateText: String?,
        audioFile: String?,
    ): Word =
        dao.addWord(
            WordEntity(
                newId(), profileId, title, parentId, sortOrder = 0, updatedAt = clock.nowMillis(),
                imageFile = imageFile, alternateText = alternateText, audioFile = audioFile,
            ),
        ).toDomain()

    override suspend fun updateFolderTitle(id: String, title: String) =
        dao.updateFolderTitle(profileId, id, title, clock.nowMillis())

    override suspend fun updateWordTitle(id: String, title: String) =
        dao.updateWordTitle(profileId, id, title, clock.nowMillis())

    override suspend fun updateWordAlternateText(id: String, alternateText: String?) =
        dao.updateWordAlternateText(profileId, id, alternateText, clock.nowMillis())

    override suspend fun countFolderContents(folderId: String): Int {
        val folders = dao.countSubtreeFolders(profileId, folderId)
        if (folders == 0) return 0 // složka neexistuje
        return (folders - 1) + dao.countSubtreeWords(profileId, folderId)
    }

    override suspend fun updateWordImage(id: String, imageFile: String?) {
        val previous = dao.replaceWordImage(profileId, id, imageFile, clock.nowMillis())
        // Soubor se maže až po úspěšném zápisu do databáze (při pádu mezi tím zbyde jen osiřelý soubor, ne chybějící).
        if (previous != null && previous != imageFile) imageStore.delete(previous)
    }

    override suspend fun updateWordAudio(id: String, audioFile: String?) {
        val previous = dao.replaceWordAudio(profileId, id, audioFile, clock.nowMillis())
        if (previous != null && previous != audioFile) audioStore.delete(previous)
    }

    override suspend fun deleteFolder(id: String) {
        dao.deleteFolderSubtree(profileId, id, clock.nowMillis()).forEach { deleteFiles(it) }
    }

    override suspend fun deleteWord(id: String) {
        dao.deleteWord(profileId, id, clock.nowMillis())?.let { deleteFiles(it) }
    }

    override suspend fun reorderItems(parentId: String?, orderedIds: List<String>) =
        dao.reorderItems(profileId, parentId, orderedIds, clock.nowMillis())

    override suspend fun moveItems(ids: Collection<String>, targetParentId: String?) =
        dao.moveItems(profileId, ids, targetParentId, clock.nowMillis())

    override suspend fun deleteItems(ids: Collection<String>) {
        dao.deleteItems(profileId, ids, clock.nowMillis()).forEach { deleteFiles(it) }
    }

    override suspend fun cleanUpOrphanFiles() {
        imageStore.deleteAllExcept(dao.allImageFiles(profileId).toSet())
        audioStore.deleteAllExcept(dao.allAudioFiles(profileId).toSet())
    }

    private suspend fun deleteFiles(files: WordFiles) {
        files.imageFile?.let { imageStore.delete(it) }
        files.audioFile?.let { audioStore.delete(it) }
    }

    private fun newId() = UUID.randomUUID().toString()
}
