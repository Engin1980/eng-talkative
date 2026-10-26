package cz.talkative.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.FolderEntity
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.local.WordEntity
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeImageStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Zotavení osiřelých položek nad skutečnou databází Room. */
@RunWith(AndroidJUnit4::class)
class RecoverOrphansTest {

    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { 9_000L }, FakeImageStore(), FakeAudioStore())
    }

    @After
    fun tearDown() = database.close()

    private fun folder(id: String, parent: String?, deleted: Boolean = false) =
        FolderEntity(id, "default", id, parent, 0, 100, deleted)

    private fun word(id: String, parent: String?) = WordEntity(id, "default", id, parent, 0, 100)

    private suspend fun recovered() = repository.observeItems("lost-and-found-default").first()

    @Test
    fun healthyDictionary_isLeftAlone() = runBlocking {
        database.dictionaryDao().upsertFolders(listOf(folder("f", null)))
        database.dictionaryDao().upsertWords(listOf(word("w", "f")))

        assertEquals(0, repository.recoverOrphans("Nalezené"))

        assertEquals(listOf("f"), repository.observeItems(null).first().map { it.id })
    }

    @Test
    fun wordInDeletedFolder_movesToRecoveredFolder_andCanBeMovedAway() = runBlocking {
        // druhé zařízení smazalo složku, první do ní mezitím přidalo slovo
        database.dictionaryDao().upsertFolders(listOf(folder("smazana", null, deleted = true)))
        database.dictionaryDao().upsertWords(listOf(word("sirotek", "smazana")))

        assertEquals(1, repository.recoverOrphans("Nalezené"))

        val root = repository.observeItems(null).first()
        assertEquals(listOf("Nalezené"), root.map { it.title })
        assertEquals(listOf("sirotek"), recovered().map { it.id })
        assertEquals(0, repository.recoverOrphans("Nalezené")) // podruhé není co přesouvat

        repository.moveItems(listOf("sirotek"), null)
        assertTrue(recovered().isEmpty())
    }

    @Test
    fun cycleOfFolders_isBroken_andNothingIsLost() = runBlocking {
        database.dictionaryDao().upsertFolders(listOf(folder("a", "b"), folder("b", "a")))
        database.dictionaryDao().upsertWords(listOf(word("w", "b")))

        assertEquals(1, repository.recoverOrphans("Nalezené"))

        assertEquals(listOf("a"), recovered().map { it.id })
        assertEquals(listOf("b"), repository.observeItems("a").first().map { it.id })
        assertEquals(listOf("w"), repository.observeItems("b").first().map { it.id })
        assertEquals(0, repository.recoverOrphans("Nalezené"))
    }

    @Test
    fun deletedRecoveredFolder_isRevivedWithTheSameId() = runBlocking {
        database.dictionaryDao().upsertFolders(listOf(folder("lost-and-found-default", null, deleted = true)))
        database.dictionaryDao().upsertWords(listOf(word("sirotek", "nikde")))

        assertEquals(1, repository.recoverOrphans("Nalezené"))

        assertEquals(listOf("sirotek"), recovered().map { it.id })
        assertEquals(listOf("lost-and-found-default"), repository.observeItems(null).first().map { it.id })
    }
}
