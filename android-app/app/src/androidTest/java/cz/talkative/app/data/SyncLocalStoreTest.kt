package cz.talkative.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.data.sync.FileSyncMediaStore
import cz.talkative.app.data.sync.RoomSyncLocalStore
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.domain.sync.MediaKind
import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.SyncItem
import cz.talkative.app.domain.sync.SyncItemKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Místní strana synchronizace nad skutečnou databází Room a skutečnými soubory. */
@RunWith(AndroidJUnit4::class)
class SyncLocalStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: TalkativeDatabase
    private lateinit var store: RoomSyncLocalStore
    private lateinit var repository: RoomDictionaryRepository
    private val media = FileSyncMediaStore(context)
    private val scratch = mutableListOf<File>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { 5_000L }, FakeImageStore(), FakeAudioStore())
        store = RoomSyncLocalStore(database.dictionaryDao())
    }

    @After
    fun tearDown() {
        database.close()
        scratch.forEach { it.delete() }
        File(context.filesDir, "images").listFiles()?.filter { it.name.startsWith("sync-test-") }?.forEach { it.delete() }
        File(context.filesDir, "audio").listFiles()?.filter { it.name.startsWith("sync-test-") }?.forEach { it.delete() }
    }

    private fun folder(id: String, updatedAt: Long = 100, deleted: Boolean = false) =
        SyncItem(id, SyncItemKind.FOLDER, "default", "složka $id", null, 1, updatedAt, deleted)

    private fun word(id: String, parent: String? = null, updatedAt: Long = 100, deleted: Boolean = false, image: String? = null) =
        SyncItem(id, SyncItemKind.WORD, "default", "slovo $id", parent, 2, updatedAt, deleted, image, "jinak", null)

    @Test
    fun applied_itemsKeepTheirTimestampsAndDeletedFlag_andReadAllIncludesTombstones() = runBlocking {
        store.apply(listOf(folder("f", updatedAt = 111), word("w", "f", updatedAt = 222), word("x", updatedAt = 333, deleted = true)), emptySet())

        val all = store.readAll().associateBy { it.id }

        assertEquals(setOf("f", "w", "x"), all.keys)
        assertEquals(111L, all.getValue("f").updatedAt)
        assertEquals(222L, all.getValue("w").updatedAt)
        assertEquals("jinak", all.getValue("w").alternateText)
        assertTrue(all.getValue("x").deleted)
        // běžné dotazy tombstony nevidí
        assertEquals(listOf("slovo w"), repository.observeItems("f").first().map { it.title })
        assertTrue(repository.snapshot().words.none { it.id == "x" })
    }

    @Test
    fun apply_overwritesAnExistingItem_andPurgeRemovesTombstonesForGood() = runBlocking {
        store.apply(listOf(word("w", updatedAt = 100), word("old", updatedAt = 50, deleted = true)), emptySet())

        store.apply(listOf(word("w", updatedAt = 200, deleted = true)), setOf("old"))

        val all = store.readAll().associateBy { it.id }
        assertEquals(setOf("w"), all.keys)
        assertEquals(200L, all.getValue("w").updatedAt)
        assertTrue(all.getValue("w").deleted)
    }

    @Test
    fun incomingVersionOlderThanTheStoredOne_isDropped() = runBlocking {
        // administrátor upravil slovíčko během synchronizace: uložená verze (500) je novější než příchozí (300)
        store.apply(listOf(word("w", updatedAt = 500).copy(title = "čerstvá úprava"), folder("f", updatedAt = 500)), emptySet())

        store.apply(listOf(word("w", updatedAt = 300), folder("f", updatedAt = 300), word("novy", updatedAt = 300)), emptySet())

        val all = store.readAll().associateBy { it.id }
        assertEquals("čerstvá úprava", all.getValue("w").title)
        assertEquals(500L, all.getValue("w").updatedAt)
        assertEquals(500L, all.getValue("f").updatedAt)
        assertEquals(300L, all.getValue("novy").updatedAt) // nové položky se zapíšou
    }

    @Test
    fun incomingVersionWithTheSameOrNewerTime_overwrites() = runBlocking {
        store.apply(listOf(word("w", updatedAt = 300)), emptySet())

        store.apply(listOf(word("w", updatedAt = 300).copy(title = "stejný čas")), emptySet())
        assertEquals("stejný čas", store.readAll().single().title)
        store.apply(listOf(word("w", updatedAt = 400).copy(title = "novější")), emptySet())
        assertEquals("novější", store.readAll().single().title)
    }

    @Test
    fun purge_keepsAnItemThatCameToLifeMeanwhile() = runBlocking {
        store.apply(listOf(word("ozivene", updatedAt = 900, deleted = false), word("tombstone", updatedAt = 50, deleted = true)), emptySet())

        store.apply(emptyList(), setOf("ozivene", "tombstone"))

        assertEquals(setOf("ozivene"), store.readAll().map { it.id }.toSet())
    }

    @Test
    fun purgeOfManyIds_worksBeyondTheSqlVariableLimit() = runBlocking {
        val many = (1..1_200).map { word("w$it", deleted = true) }
        store.apply(many, emptySet())

        store.apply(emptyList(), many.map { it.id }.toSet())

        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun restoredMedia_keepsTheExactName_andRefusesUnsafeNames() = runBlocking {
        val source = File.createTempFile("zdroj", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        scratch += source
        val ref = MediaRef(MediaKind.IMAGE, "sync-test-obrazek.png")

        assertNull(media.file(ref))
        assertTrue(media.restore(ref, source))

        val stored = media.file(ref)
        assertNotNull(stored)
        assertEquals("sync-test-obrazek.png", stored!!.name)
        assertEquals(listOf<Byte>(1, 2, 3), stored.readBytes().toList())
        assertEquals("images", stored.parentFile!!.name)
        assertFalse(media.restore(MediaRef(MediaKind.IMAGE, "../databases/x"), source))
        assertFalse(media.restore(MediaRef(MediaKind.AUDIO, "a/b.m4a"), source))
        assertNull(media.file(MediaRef(MediaKind.AUDIO, "..")))

        media.delete(ref)
        assertNull(media.file(ref))
    }

    @Test
    fun audioIsRestoredIntoTheAudioFolder() = runBlocking {
        val source = File.createTempFile("zdroj", ".m4a").apply { writeBytes(byteArrayOf(4)) }
        scratch += source
        val ref = MediaRef(MediaKind.AUDIO, "sync-test-zvuk.m4a")

        assertTrue(media.restore(ref, source))

        assertEquals("audio", media.file(ref)!!.parentFile!!.name)
        media.delete(ref)
    }
}
