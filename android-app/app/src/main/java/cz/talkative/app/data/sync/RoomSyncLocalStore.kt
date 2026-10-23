package cz.talkative.app.data.sync

import android.database.SQLException
import cz.talkative.app.data.local.DictionaryDao
import cz.talkative.app.data.local.FolderEntity
import cz.talkative.app.data.local.WordEntity
import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncException
import cz.talkative.app.domain.sync.SyncItem
import cz.talkative.app.domain.sync.SyncItemKind
import cz.talkative.app.domain.sync.SyncLocalStore
import javax.inject.Inject

/**
 * Místní databáze pro synchronizaci. Čte i tombstony a zapisuje položky beze změny `updatedAt` (přes
 * [DictionaryDao.applySync]), takže použití vzdálených změn nevyvolá další synchronizaci ani nepřepíše čas změny.
 */
class RoomSyncLocalStore @Inject constructor(
    private val dao: DictionaryDao,
) : SyncLocalStore {

    // Zatím existuje jen výchozí profil.
    private val profileId = Profile.DEFAULT_ID

    override suspend fun readAll(): List<SyncItem> =
        dao.allFoldersWithDeleted(profileId).map { it.toSyncItem() } + dao.allWordsWithDeleted(profileId).map { it.toSyncItem() }

    override suspend fun apply(changed: List<SyncItem>, purgeIds: Set<String>) {
        val folders = changed.filter { it.kind == SyncItemKind.FOLDER }.map { it.toFolderEntity() }
        val words = changed.filter { it.kind == SyncItemKind.WORD }.map { it.toWordEntity() }
        try {
            dao.applySync(profileId, folders, words, purgeIds)
        } catch (e: SQLException) {
            throw SyncException(SyncError.LOCAL_STORAGE, "Zápis do databáze selhal", e)
        }
    }
}

internal fun FolderEntity.toSyncItem() =
    SyncItem(id, SyncItemKind.FOLDER, profileId, title, parentId, sortOrder, updatedAt, deleted)

internal fun WordEntity.toSyncItem() =
    SyncItem(id, SyncItemKind.WORD, profileId, title, parentId, sortOrder, updatedAt, deleted, imageFile, alternateText, audioFile)

internal fun SyncItem.toFolderEntity() = FolderEntity(id, profileId, title, parentId, sortOrder, updatedAt, deleted)

internal fun SyncItem.toWordEntity() =
    WordEntity(id, profileId, title, parentId, sortOrder, updatedAt, deleted, imageFile, alternateText, audioFile)
