package cz.talkative.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.domain.model.Word
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryTest {

    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository
    private val imageStore = FakeImageStore()
    private val audioStore = FakeAudioStore()
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { now }, imageStore, audioStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun titles(parentId: String?) = repository.observeItems(parentId).first().map { it.title }

    @Test
    fun createdItems_areReadBackInCreationOrder_foldersAndWordsTogether() = runTest {
        repository.createFolder(null, "Jídlo")
        repository.createWord(null, "Chci")
        repository.createFolder(null, "Pití")

        assertEquals(listOf("Jídlo", "Chci", "Pití"), titles(null))
        assertEquals(listOf(0, 1, 2), repository.observeItems(null).first().map { it.sortOrder })
    }

    @Test
    fun newItem_isPlacedAfterTheLastOneEvenAfterDeletion() = runTest {
        repository.createWord(null, "a")
        val b = repository.createWord(null, "b")
        repository.deleteWord(b.id)

        val c = repository.createWord(null, "c")

        assertEquals(1, c.sortOrder)
        assertEquals(listOf("a", "c"), titles(null))
    }

    @Test
    fun sortOrder_isPerFolder() = runTest {
        val folder = repository.createFolder(null, "A")
        repository.createWord(null, "root-word")

        val inside = repository.createWord(folder.id, "inside")

        assertEquals(0, inside.sortOrder)
    }

    @Test
    fun items_storeProfileAndParentAndUpdatedAt() = runTest {
        val folder = repository.createFolder(null, "A")
        now = 2_000L
        val word = repository.createWord(folder.id, "w")

        assertEquals("default", folder.profileId)
        assertNull(folder.parentId)
        assertEquals(1_000L, folder.updatedAt)
        assertEquals(folder.id, word.parentId)
        assertEquals(2_000L, word.updatedAt)
    }

    @Test
    fun rename_changesTitleAndUpdatedAt_notOtherFields() = runTest {
        val word = repository.createWord(null, "old")
        now = 5_000L

        repository.updateWordTitle(word.id, "new")

        val stored = repository.observeItems(null).first().single() as Word
        assertEquals("new", stored.title)
        assertEquals(5_000L, stored.updatedAt)
        assertEquals(word.id, stored.id)
        assertEquals(word.sortOrder, stored.sortOrder)

        val folder = repository.createFolder(null, "F")
        repository.updateFolderTitle(folder.id, "G")
        assertEquals("G", repository.getPath(folder.id).single().title)
    }

    @Test
    fun titlesNeedNotBeUnique() = runTest {
        val a = repository.createWord(null, "same")
        val b = repository.createWord(null, "same")

        assertNotEquals(a.id, b.id)
        assertEquals(listOf("same", "same"), titles(null))
    }

    @Test
    fun deepTree_path_listsAncestorsFromTheTop() = runTest {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        repository.createWord(c.id, "w")

        assertEquals(listOf("A", "B", "C"), repository.getPath(c.id).map { it.title })
        assertEquals(listOf("A"), repository.getPath(a.id).map { it.title })
        assertTrue(repository.getPath(null).isEmpty())
        assertEquals(listOf("B"), titles(a.id))
        assertEquals(listOf("w"), titles(c.id))
    }

    @Test
    fun countFolderContents_includesWholeSubtreeButNotTheFolderItself() = runTest {
        val a = repository.createFolder(null, "A")
        repository.createWord(a.id, "a1")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        repository.createWord(c.id, "c1")
        repository.createWord(c.id, "c2")
        val other = repository.createFolder(null, "Other")
        repository.createWord(other.id, "o1")

        assertEquals(5, repository.countFolderContents(a.id)) // a1, B, C, c1, c2
        assertEquals(1, repository.countFolderContents(other.id))
        assertEquals(2, repository.countFolderContents(c.id)) // c1, c2
    }

    @Test
    fun deleteFolder_removesWholeSubtree_leavesSiblingsAndTheRest() = runTest {
        val a = repository.createFolder(null, "A")
        repository.createWord(a.id, "a1")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        repository.createWord(c.id, "c1")
        val other = repository.createFolder(null, "Other")
        repository.createWord(other.id, "o1")
        repository.createWord(null, "root-word")

        repository.deleteFolder(a.id)

        assertEquals(listOf("Other", "root-word"), titles(null))
        assertTrue(titles(a.id).isEmpty())
        assertTrue(titles(b.id).isEmpty())
        assertTrue(titles(c.id).isEmpty())
        assertEquals(listOf("o1"), titles(other.id))
        assertTrue(repository.getPath(c.id).isEmpty())
    }

    @Test
    fun deletedItems_stayAsTombstones_withNewUpdatedAt() = runTest {
        val folder = repository.createFolder(null, "A")
        val word = repository.createWord(folder.id, "w")
        now = 9_000L

        repository.deleteFolder(folder.id)

        val dao = database.dictionaryDao()
        val rows = database.query("SELECT deleted, updatedAt FROM folders WHERE id = ?", arrayOf(folder.id))
        rows.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(9_000L, it.getLong(1))
        }
        assertNull(dao.getWord("default", word.id)) // běžné dotazy tombstone nevrací
        database.query("SELECT deleted, updatedAt FROM words WHERE id = ?", arrayOf(word.id)).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(9_000L, it.getLong(1))
        }
    }

    @Test
    fun deleteWord_removesOnlyThatWord() = runTest {
        val keep = repository.createWord(null, "keep")
        val drop = repository.createWord(null, "drop")

        repository.deleteWord(drop.id)

        assertEquals(listOf("keep"), titles(null))
        assertEquals(keep.id, repository.observeItems(null).first().single().id)
    }

    @Test
    fun counts_ignoreAlreadyDeletedItems() = runTest {
        val a = repository.createFolder(null, "A")
        val w = repository.createWord(a.id, "w1")
        repository.createWord(a.id, "w2")
        val b = repository.createFolder(a.id, "B")
        repository.deleteWord(w.id)
        repository.deleteFolder(b.id)

        assertEquals(1, repository.countFolderContents(a.id))
        assertEquals(0, repository.countFolderContents(b.id)) // smazaná složka
        assertTrue(repository.observeItems(a.id).first().none { it is Folder })
    }

    // ---- obrázky (krok 4.1)

    @Test
    fun deleteWord_deletesItsImageFile_andLeavesOthers() = runTest {
        val a = repository.createWord(null, "a", imageStore.add("a.jpg"))
        repository.createWord(null, "b", imageStore.add("b.jpg"))

        repository.deleteWord(a.id)

        assertEquals(setOf("b.jpg"), imageStore.files)
    }

    @Test
    fun deleteFolder_deletesImagesOfWholeSubtree_notOfOtherFolders() = runTest {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        repository.createWord(a.id, "a1", imageStore.add("a1.jpg"))
        repository.createWord(b.id, "b1", imageStore.add("b1.jpg"))
        repository.createWord(b.id, "b2-bez-obrazku")
        val other = repository.createFolder(null, "Other")
        repository.createWord(other.id, "o1", imageStore.add("o1.jpg"))
        repository.createWord(null, "root", imageStore.add("root.jpg"))

        repository.deleteFolder(a.id)

        assertEquals(setOf("o1.jpg", "root.jpg"), imageStore.files)
    }

    @Test
    fun deletedWord_tombstoneKeepsNoImageReference() = runTest {
        val word = repository.createWord(null, "w", imageStore.add("w.jpg"))

        repository.deleteWord(word.id)

        database.query("SELECT deleted, imageFile FROM words WHERE id = ?", arrayOf(word.id)).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun updateWordImage_replacesAndDeletesOldFile() = runTest {
        val word = repository.createWord(null, "w", imageStore.add("old.jpg"))
        val newName = imageStore.add("new.jpg")

        repository.updateWordImage(word.id, newName)

        assertEquals("new.jpg", (repository.observeItems(null).first().single() as Word).imageFile)
        assertEquals(setOf("new.jpg"), imageStore.files)
    }

    @Test
    fun updateWordImage_toNull_removesImageAndFile() = runTest {
        val word = repository.createWord(null, "w", imageStore.add("old.jpg"))

        repository.updateWordImage(word.id, null)

        assertNull((repository.observeItems(null).first().single() as Word).imageFile)
        assertTrue(imageStore.files.isEmpty())
    }

    @Test
    fun updateWordImage_withSameFile_doesNotDeleteIt() = runTest {
        val word = repository.createWord(null, "w", imageStore.add("same.jpg"))

        repository.updateWordImage(word.id, "same.jpg")

        assertEquals(setOf("same.jpg"), imageStore.files)
    }

    @Test
    fun cleanUpOrphanFiles_removesOnlyUnreferencedFiles() = runTest {
        repository.createWord(null, "w", imageStore.add("used.jpg"))
        val deleted = repository.createWord(null, "x", imageStore.add("x.jpg"))
        imageStore.add("orphan.jpg")
        repository.deleteWord(deleted.id)
        imageStore.add("x.jpg") // jako by smazání souboru po pádu neproběhlo

        repository.cleanUpOrphanFiles()

        assertEquals(setOf("used.jpg"), imageStore.files)
    }

    @Test
    fun wordWithoutImage_deletesWithoutTouchingFiles() = runTest {
        imageStore.add("other.jpg")
        val word = repository.createWord(null, "bez obrázku")

        repository.deleteWord(word.id)

        assertEquals(setOf("other.jpg"), imageStore.files)
    }

    @Test
    fun alternateText_isStoredUpdatedAndRemoved() = runTest {
        val word = repository.createWord(null, "KD", alternateText = "ká dé")
        assertEquals("ká dé", (repository.observeItems(null).first().single() as Word).alternateText)

        repository.updateWordAlternateText(word.id, "kádé")
        assertEquals("kádé", (repository.observeItems(null).first().single() as Word).alternateText)

        repository.updateWordAlternateText(word.id, null)
        assertNull((repository.observeItems(null).first().single() as Word).alternateText)
    }

    // ---- vlastní MP3 (krok 5.2)

    private suspend fun audioOfOnlyWord() = (repository.observeItems(null).first().single() as Word).audioFile

    @Test
    fun audio_isStored_replacedAndRemoved_withoutLeavingFiles() = runTest {
        val word = repository.createWord(null, "w", audioFile = audioStore.add("a.mp3"))
        assertEquals("a.mp3", audioOfOnlyWord())

        repository.updateWordAudio(word.id, audioStore.add("b.mp3"))
        assertEquals("b.mp3", audioOfOnlyWord())
        assertEquals(setOf("b.mp3"), audioStore.files) // původní soubor je pryč

        repository.updateWordAudio(word.id, null)
        assertNull(audioOfOnlyWord())
        assertTrue(audioStore.files.isEmpty())
    }

    @Test
    fun updatingAudioToTheSameFile_keepsIt() = runTest {
        val word = repository.createWord(null, "w", audioFile = audioStore.add("same.mp3"))

        repository.updateWordAudio(word.id, "same.mp3")

        assertEquals(setOf("same.mp3"), audioStore.files)
    }

    @Test
    fun deleteWord_removesItsAudioAndImage() = runTest {
        val word = repository.createWord(null, "w", imageStore.add("w.jpg"), audioFile = audioStore.add("w.mp3"))
        val keep = repository.createWord(null, "k", audioFile = audioStore.add("k.mp3"))

        repository.deleteWord(word.id)

        assertEquals(setOf("k.mp3"), audioStore.files)
        assertTrue(imageStore.files.isEmpty())
        assertEquals("k.mp3", (repository.observeItems(null).first().single() as Word).audioFile)
        assertEquals(keep.id, repository.observeItems(null).first().single().id)
    }

    @Test
    fun deleteFolder_removesAudioOfWholeSubtree_only() = runTest {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        val other = repository.createFolder(null, "Jiná")
        repository.createWord(a.id, "a1", audioFile = audioStore.add("a1.mp3"))
        repository.createWord(b.id, "b1", imageStore.add("b1.jpg"), audioFile = audioStore.add("b1.mp3"))
        repository.createWord(b.id, "b2-bez-zvuku")
        repository.createWord(other.id, "o1", audioFile = audioStore.add("o1.mp3"))
        repository.createWord(null, "root", audioFile = audioStore.add("root.mp3"))

        repository.deleteFolder(a.id)

        assertEquals(setOf("o1.mp3", "root.mp3"), audioStore.files)
        assertTrue(imageStore.files.isEmpty())
    }

    @Test
    fun cleanUpOrphanFiles_removesOnlyUnreferencedAudio() = runTest {
        repository.createWord(null, "w", audioFile = audioStore.add("used.mp3"))
        val deleted = repository.createWord(null, "x", audioFile = audioStore.add("x.mp3"))
        repository.deleteWord(deleted.id)
        audioStore.add("orphan.mp3")

        repository.cleanUpOrphanFiles()

        assertEquals(setOf("used.mp3"), audioStore.files)
    }

    @Test
    fun deleteItems_deletesFoldersWithSubtreesAndWordsTogether_withTheirFiles() = runTest {
        val a = repository.createFolder(null, "A")
        val inner = repository.createFolder(a.id, "Vnitřní")
        repository.createWord(inner.id, "i1", imageStore.add("i1.jpg"), audioFile = audioStore.add("i1.mp3"))
        val word = repository.createWord(null, "w", audioFile = audioStore.add("w.mp3"))
        val keep = repository.createFolder(null, "Zůstane")
        repository.createWord(keep.id, "k1", imageStore.add("k1.jpg"))
        val keepWord = repository.createWord(null, "keep-word")

        repository.deleteItems(listOf(a.id, word.id, "neexistuje"))

        assertEquals(listOf("Zůstane", "keep-word"), titles(null))
        assertTrue(titles(a.id).isEmpty())
        assertTrue(titles(inner.id).isEmpty())
        assertEquals(listOf("k1"), titles(keep.id))
        assertEquals(setOf("k1.jpg"), imageStore.files)
        assertTrue(audioStore.files.isEmpty())
        assertEquals(keepWord.id, repository.observeItems(null).first().last().id)
    }
}
