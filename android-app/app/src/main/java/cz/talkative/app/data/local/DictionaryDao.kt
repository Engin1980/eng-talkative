package cz.talkative.app.data.local

import androidx.room.Dao
import androidx.room.Insert
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

    @Insert
    abstract suspend fun insertFolder(entity: FolderEntity)

    @Insert
    abstract suspend fun insertWord(entity: WordEntity)

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

    /** Obrázky nesmazaných slovíček v podstromu (pro úklid souborů při mazání složky). */
    @Query(
        SUBTREE + """
        SELECT imageFile FROM words
        WHERE profileId = :profileId AND deleted = 0 AND imageFile IS NOT NULL
        AND parentId IN (SELECT id FROM subtree)
        """,
    )
    abstract suspend fun subtreeImageFiles(profileId: String, rootId: String): List<String>

    @Query(
        SUBTREE + """
        UPDATE words SET deleted = 1, updatedAt = :now, imageFile = NULL
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

    @Query("UPDATE words SET deleted = 1, updatedAt = :now, imageFile = NULL WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun markWordDeleted(profileId: String, id: String, now: Long)

    @Query("UPDATE words SET imageFile = :imageFile, updatedAt = :now WHERE profileId = :profileId AND id = :id AND deleted = 0")
    abstract suspend fun updateWordImage(profileId: String, id: String, imageFile: String?, now: Long)

    /** Všechny obrázky, které nesmazaná slovíčka používají (zbytek souborů je osiřelý). */
    @Query("SELECT imageFile FROM words WHERE profileId = :profileId AND deleted = 0 AND imageFile IS NOT NULL")
    abstract suspend fun allImageFiles(profileId: String): List<String>

    /** Smaže slovíčko a vrátí název jeho obrázku (k úklidu souboru), nebo `null`. */
    @Transaction
    open suspend fun deleteWord(profileId: String, id: String, now: Long): String? {
        val image = getWord(profileId, id)?.imageFile
        markWordDeleted(profileId, id, now)
        return image
    }

    /** Nastaví obrázek slovíčka a vrátí název původního obrázku (k úklidu souboru), nebo `null`. */
    @Transaction
    open suspend fun replaceWordImage(profileId: String, id: String, imageFile: String?, now: Long): String? {
        val previous = getWord(profileId, id)?.imageFile
        updateWordImage(profileId, id, imageFile, now)
        return previous
    }

    /**
     * Tombstone celého podstromu v jedné transakci. Slovíčka první, dokud složky ještě "žijí" pro rekurzi.
     * Vrací obrázky smazaných slovíček, aby je volající po úspěšném commitu smazal ze souborů.
     */
    @Transaction
    open suspend fun deleteFolderSubtree(profileId: String, rootId: String, now: Long): List<String> {
        val images = subtreeImageFiles(profileId, rootId)
        deleteSubtreeWords(profileId, rootId, now)
        deleteSubtreeFolders(profileId, rootId, now)
        return images
    }
}
