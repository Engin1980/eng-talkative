package cz.talkative.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Rekurzivní CTE `subtree` se všemi nesmazanými složkami pod `:rootId` (včetně něj). */
private const val SUBTREE = """
    WITH RECURSIVE subtree(id) AS (
        SELECT id FROM folders WHERE id = :rootId AND profileId = :profileId AND deleted = 0
        UNION ALL
        SELECT f.id FROM folders f JOIN subtree s ON f.parentId = s.id
        WHERE f.profileId = :profileId AND f.deleted = 0
    )
"""

/** Názvy souborů slovíčka v úložištích (obrázek a MP3); každý může chybět. */
data class WordFiles(val imageFile: String?, val audioFile: String?)

/** Dotazy nad složkami a slovíčky. Všechny běžné dotazy vynechávají smazané položky (tombstony). */
@Dao
abstract class DictionaryDao {

    @Query("SELECT * FROM folders WHERE profileId = :profileId AND parentId IS :parentId AND deleted = 0")
    abstract fun observeFolders(profileId: String, parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM words WHERE profileId = :profileId AND parentId IS :parentId AND deleted = 0")
    abstract fun observeWords(profileId: String, parentId: String?): Flow<List<WordEntity>>

    @Query("SELECT * FROM folders WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun getFolder(profileId: String, id: String): FolderEntity?

    @Query("SELECT * FROM words WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun getWord(profileId: String, id: String): WordEntity?

    @Query("SELECT * FROM folders WHERE profileId = :profileId AND deleted = 0")
    abstract suspend fun allFolders(profileId: String): List<FolderEntity>

    @Query("SELECT * FROM words WHERE profileId = :profileId AND deleted = 0")
    abstract suspend fun allWords(profileId: String): List<WordEntity>

    @Query("SELECT imageFile, audioFile FROM words WHERE profileId = :profileId AND deleted = 0 AND (imageFile IS NOT NULL OR audioFile IS NOT NULL)")
    abstract suspend fun allWordFiles(profileId: String): List<WordFiles>

    @Query("UPDATE words SET deleted = 1, updatedAt = :now, imageFile = NULL, audioFile = NULL WHERE profileId = :profileId AND deleted = 0")
    abstract suspend fun deleteAllWords(profileId: String, now: Long)

    @Query("UPDATE folders SET deleted = 1, updatedAt = :now WHERE profileId = :profileId AND deleted = 0")
    abstract suspend fun deleteAllFolders(profileId: String, now: Long)

    @Insert
    abstract suspend fun insertFolder(entity: FolderEntity)

    // ---- synchronizace: čtení včetně tombstonů a zápis beze změny `updatedAt`

    @Query("SELECT * FROM folders WHERE profileId = :profileId")
    abstract suspend fun allFoldersWithDeleted(profileId: String): List<FolderEntity>

    @Query("SELECT * FROM words WHERE profileId = :profileId")
    abstract suspend fun allWordsWithDeleted(profileId: String): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertFolders(entities: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertWords(entities: List<WordEntity>)

    @Query("SELECT * FROM folders WHERE profileId = :profileId AND id = :id")
    abstract suspend fun getFolderIncludingDeleted(profileId: String, id: String): FolderEntity?

    @Query("SELECT * FROM words WHERE profileId = :profileId AND id = :id")
    abstract suspend fun getWordIncludingDeleted(profileId: String, id: String): WordEntity?

    /** Maže jen tombstony: položka, která mezitím ožila (nová úprava), zůstane. */
    @Query("DELETE FROM folders WHERE profileId = :profileId AND deleted = 1 AND id IN (:ids)")
    abstract suspend fun purgeFolders(profileId: String, ids: List<String>)

    @Query("DELETE FROM words WHERE profileId = :profileId AND deleted = 1 AND id IN (:ids)")
    abstract suspend fun purgeWords(profileId: String, ids: List<String>)

    /**
     * Zapíše výsledek synchronizace v jedné transakci: položky se uloží přesně tak, jak přišly (včetně `updatedAt`
     * a příznaku smazání), a prošlé tombstony se smažou úplně. Synchronizace trvá sekundy, během nichž administrátor
     * mohl položku upravit, proto se uvnitř transakce znovu zkontroluje „novější vyhrává“: příchozí verze, která je
     * starší než uložená, se zahodí. Bez omezení počtu položek v jednom SQL dotazu.
     */
    @Transaction
    open suspend fun applySync(profileId: String, folders: List<FolderEntity>, words: List<WordEntity>, purgeIds: Collection<String>) {
        upsertFolders(folders.filter { incoming -> getFolderIncludingDeleted(profileId, incoming.id).let { it == null || it.updatedAt <= incoming.updatedAt } })
        upsertWords(words.filter { incoming -> getWordIncludingDeleted(profileId, incoming.id).let { it == null || it.updatedAt <= incoming.updatedAt } })
        purgeIds.chunked(500).forEach { chunk ->
            purgeFolders(profileId, chunk)
            purgeWords(profileId, chunk)
        }
    }

    @Insert
    abstract suspend fun insertWord(entity: WordEntity)

    @Query("UPDATE folders SET parentId = :parentId, sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun reparentFolder(profileId: String, id: String, parentId: String?, sortOrder: Int, now: Long)

    @Query("UPDATE words SET parentId = :parentId, sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun reparentWord(profileId: String, id: String, parentId: String?, sortOrder: Int, now: Long)

    /**
     * Přesune nesmazané položky [ids] na konec složky [recoveredId] v kořeni; ta se přitom vytvoří, nebo oživí, když byla
     * smazaná. Vrací počet skutečně přesunutých položek. Vše v jedné transakci.
     */
    @Transaction
    open suspend fun moveToRecovered(profileId: String, recoveredId: String, title: String, ids: Collection<String>, now: Long): Int {
        val existing = getFolderIncludingDeleted(profileId, recoveredId)
        if (existing == null || existing.deleted) {
            upsertFolders(listOf(FolderEntity(recoveredId, profileId, title, null, nextSortOrder(profileId, null), now)))
        }
        var moved = 0
        for (id in ids.sorted()) {
            if (id == recoveredId) continue
            val order = nextSortOrder(profileId, recoveredId)
            if (getFolder(profileId, id) != null) {
                reparentFolder(profileId, id, recoveredId, order, now)
                moved++
            } else if (getWord(profileId, id) != null) {
                reparentWord(profileId, id, recoveredId, order, now)
                moved++
            }
        }
        return moved
    }

    @Query("UPDATE folders SET title = :title, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateFolderTitle(profileId: String, id: String, title: String, now: Long)

    @Query("UPDATE words SET title = :title, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateWordTitle(profileId: String, id: String, title: String, now: Long)

    /** Pořadí na konci složky: o jedna víc než největší pořadí složek i slovíček v ní. */
    @Query(
        """
        SELECT COALESCE(MAX(m), -1) + 1 FROM (
            SELECT MAX(sortOrder) AS m FROM folders WHERE profileId = :profileId AND parentId IS :parentId AND deleted = 0
            UNION ALL
            SELECT MAX(sortOrder) AS m FROM words WHERE profileId = :profileId AND parentId IS :parentId AND deleted = 0
        )
        """,
    )
    abstract suspend fun nextSortOrder(profileId: String, parentId: String?): Int

    /** Vloží složku na konec její složky a vrátí uloženou podobu (s přiděleným pořadím). */
    @Transaction
    open suspend fun addFolder(entity: FolderEntity): FolderEntity {
        val stored = entity.copy(sortOrder = nextSortOrder(entity.profileId, entity.parentId))
        insertFolder(stored)
        return stored
    }

    @Transaction
    open suspend fun addWord(entity: WordEntity): WordEntity {
        val stored = entity.copy(sortOrder = nextSortOrder(entity.profileId, entity.parentId))
        insertWord(stored)
        return stored
    }

    /** Počet složek v podstromu včetně kořene [rootId]. */
    @Query(SUBTREE + " SELECT COUNT(*) FROM subtree")
    abstract suspend fun countSubtreeFolders(profileId: String, rootId: String): Int

    /** Počet slovíček v celém podstromu složky [rootId]. */
    @Query(
        SUBTREE + """
        SELECT COUNT(*) FROM words
        WHERE profileId = :profileId AND deleted = 0 AND parentId IN (SELECT id FROM subtree)
        """,
    )
    abstract suspend fun countSubtreeWords(profileId: String, rootId: String): Int

    /** Soubory (obrázky a MP3) nesmazaných slovíček v podstromu, pro úklid při mazání složky. */
    @Query(
        SUBTREE + """
        SELECT imageFile, audioFile FROM words
        WHERE profileId = :profileId AND deleted = 0 AND (imageFile IS NOT NULL OR audioFile IS NOT NULL)
        AND parentId IN (SELECT id FROM subtree)
        """,
    )
    abstract suspend fun subtreeFiles(profileId: String, rootId: String): List<WordFiles>

    @Query(
        SUBTREE + """
        UPDATE words SET deleted = 1, updatedAt = :now, imageFile = NULL, audioFile = NULL
        WHERE profileId = :profileId AND deleted = 0 AND parentId IN (SELECT id FROM subtree)
        """,
    )
    abstract suspend fun deleteSubtreeWords(profileId: String, rootId: String, now: Long)

    @Query(
        SUBTREE + """
        UPDATE folders SET deleted = 1, updatedAt = :now
        WHERE profileId = :profileId AND deleted = 0 AND id IN (SELECT id FROM subtree)
        """,
    )
    abstract suspend fun deleteSubtreeFolders(profileId: String, rootId: String, now: Long)

    @Query("UPDATE words SET deleted = 1, updatedAt = :now, imageFile = NULL, audioFile = NULL WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun markWordDeleted(profileId: String, id: String, now: Long)

    @Query("UPDATE words SET imageFile = :imageFile, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateWordImage(profileId: String, id: String, imageFile: String?, now: Long)

    @Query("UPDATE words SET alternateText = :alternateText, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateWordAlternateText(profileId: String, id: String, alternateText: String?, now: Long)

    @Query("UPDATE words SET audioFile = :audioFile, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateWordAudio(profileId: String, id: String, audioFile: String?, now: Long)

    /** Všechny obrázky, které nesmazaná slovíčka používají (zbytek souborů je osiřelý). */
    @Query("SELECT imageFile FROM words WHERE profileId = :profileId AND deleted = 0 AND imageFile IS NOT NULL")
    abstract suspend fun allImageFiles(profileId: String): List<String>

    /** Všechna MP3, která nesmazaná slovíčka používají. */
    @Query("SELECT audioFile FROM words WHERE profileId = :profileId AND deleted = 0 AND audioFile IS NOT NULL")
    abstract suspend fun allAudioFiles(profileId: String): List<String>

    /** Smaže slovíčko a vrátí názvy jeho souborů (k úklidu), nebo `null`, když slovíčko neexistuje. */
    @Transaction
    open suspend fun deleteWord(profileId: String, id: String, now: Long): WordFiles? {
        val word = getWord(profileId, id) ?: return null
        markWordDeleted(profileId, id, now)
        return WordFiles(word.imageFile, word.audioFile)
    }

    /** Nastaví obrázek slovíčka a vrátí název původního obrázku (k úklidu souboru), nebo `null`. */
    @Transaction
    open suspend fun replaceWordImage(profileId: String, id: String, imageFile: String?, now: Long): String? {
        val previous = getWord(profileId, id)?.imageFile
        updateWordImage(profileId, id, imageFile, now)
        return previous
    }

    /** Nastaví MP3 slovíčka a vrátí název původního MP3 (k úklidu souboru), nebo `null`. */
    @Transaction
    open suspend fun replaceWordAudio(profileId: String, id: String, audioFile: String?, now: Long): String? {
        val previous = getWord(profileId, id)?.audioFile
        updateWordAudio(profileId, id, audioFile, now)
        return previous
    }

    @Query("UPDATE folders SET parentId = :parentId, sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun moveFolder(profileId: String, id: String, parentId: String?, sortOrder: Int, now: Long)

    @Query("UPDATE words SET parentId = :parentId, sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun moveWord(profileId: String, id: String, parentId: String?, sortOrder: Int, now: Long)

    /**
     * Přesune složky a slovíčka na konec složky [targetParentId] a zachová jejich dosavadní pořadí
     * (podle `sortOrder`, ne podle pořadí v [ids]). Přesunuté položky dostanou nové `updatedAt`.
     */
    @Transaction
    open suspend fun moveItems(profileId: String, ids: Collection<String>, targetParentId: String?, now: Long) {
        val folders = ids.mapNotNull { getFolder(profileId, it) }.filter { it.parentId != targetParentId }
        val words = ids.mapNotNull { getWord(profileId, it) }.filter { it.parentId != targetParentId }
        val moving = folders.map { Triple(it.sortOrder, it.id, true) } + words.map { Triple(it.sortOrder, it.id, false) }
        var order = nextSortOrder(profileId, targetParentId)
        for ((_, id, isFolder) in moving.sortedWith(compareBy({ it.first }, { it.second }))) {
            if (isFolder) moveFolder(profileId, id, targetParentId, order++, now) else moveWord(profileId, id, targetParentId, order++, now)
        }
    }

    @Query("UPDATE folders SET sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun setFolderOrder(profileId: String, id: String, sortOrder: Int, now: Long)

    @Query("UPDATE words SET sortOrder = :sortOrder, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun setWordOrder(profileId: String, id: String, sortOrder: Int, now: Long)

    /** Přečísluje položky složky podle [orderedIds] na 0, 1, 2, ...; položky z jiné složky se přeskočí. */
    @Transaction
    open suspend fun reorderItems(profileId: String, parentId: String?, orderedIds: List<String>, now: Long) {
        orderedIds.forEachIndexed { index, id ->
            val folder = getFolder(profileId, id)
            val word = if (folder == null) getWord(profileId, id) else null
            if (folder != null && folder.parentId == parentId && folder.sortOrder != index) {
                setFolderOrder(profileId, id, index, now)
            } else if (word != null && word.parentId == parentId && word.sortOrder != index) {
                setWordOrder(profileId, id, index, now)
            }
        }
    }

    /** Smaže složky (s podstromy) i slovíčka podle ID v jedné transakci a vrátí soubory k úklidu. */
    @Transaction
    open suspend fun deleteItems(profileId: String, ids: Collection<String>, now: Long): List<WordFiles> {
        val files = mutableListOf<WordFiles>()
        for (id in ids) {
            if (getFolder(profileId, id) != null) {
                files += deleteFolderSubtree(profileId, id, now)
            } else {
                deleteWord(profileId, id, now)?.let { files += it }
            }
        }
        return files
    }

    /**
     * Tombstone celého podstromu v jedné transakci. Slovíčka první, dokud složky ještě "žijí" pro rekurzi.
     * Vrací soubory smazaných slovíček, aby je volající po úspěšném commitu smazal z úložiště.
     */
    @Transaction
    open suspend fun deleteFolderSubtree(profileId: String, rootId: String, now: Long): List<WordFiles> {
        val files = subtreeFiles(profileId, rootId)
        deleteSubtreeWords(profileId, rootId, now)
        deleteSubtreeFolders(profileId, rootId, now)
        return files
    }

    /**
     * Vloží hotové entity v jedné transakci. Entity s rodičem [targetParentId] jsou na nejvyšší úrovni importu a dostanou
     * pořadí za dosavadními položkami cíle. S [replaceAll] se předtím smaže celý slovník; vrací jeho soubory k úklidu.
     */
    @Transaction
    open suspend fun importItems(
        profileId: String,
        targetParentId: String?,
        folders: List<FolderEntity>,
        words: List<WordEntity>,
        replaceAll: Boolean,
        now: Long,
    ): List<WordFiles> {
        val oldFiles = if (replaceAll) allWordFiles(profileId) else emptyList()
        if (replaceAll) {
            deleteAllWords(profileId, now)
            deleteAllFolders(profileId, now)
        }
        val base = nextSortOrder(profileId, targetParentId)
        folders.forEach { insertFolder(if (it.parentId == targetParentId) it.copy(sortOrder = it.sortOrder + base) else it) }
        words.forEach { insertWord(if (it.parentId == targetParentId) it.copy(sortOrder = it.sortOrder + base) else it) }
        return oldFiles
    }
}
