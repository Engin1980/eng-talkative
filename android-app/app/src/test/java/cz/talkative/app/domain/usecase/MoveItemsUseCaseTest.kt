package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.MoveResult
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveItemsUseCaseTest {

    private val repository = FakeDictionaryRepository()
    private val move = MoveItemsUseCase(repository)

    private suspend fun titles(parentId: String?) = repository.observeItems(parentId).first().map { it.title }

    @Test
    fun wordsAndFolders_moveTogetherToTheTargetFolder() = runTest {
        val target = repository.createFolder(null, "Cíl")
        val folder = repository.createFolder(null, "Složka")
        val word = repository.createWord(null, "Slovo")
        repository.createWord(null, "Zůstane")

        val result = move(setOf(folder.id, word.id), target.id)

        assertEquals(MoveResult.Moved, result)
        assertEquals(listOf("Cíl", "Zůstane"), titles(null))
        assertEquals(listOf("Složka", "Slovo"), titles(target.id))
    }

    @Test
    fun movedItems_keepTheirOrder_notTheOrderOfTheSelection() = runTest {
        val target = repository.createFolder(null, "Cíl")
        val a = repository.createWord(null, "a")
        val b = repository.createWord(null, "b")
        val c = repository.createWord(null, "c")
        repository.createWord(target.id, "už tam je")

        move(setOf(c.id, a.id, b.id), target.id)

        assertEquals(listOf("už tam je", "a", "b", "c"), titles(target.id))
    }

    @Test
    fun moveToRoot_works() = runTest {
        val outer = repository.createFolder(null, "Vnější")
        val word = repository.createWord(outer.id, "slovo")

        assertEquals(MoveResult.Moved, move(setOf(word.id), null))

        assertEquals(listOf("Vnější", "slovo"), titles(null))
        assertTrue(titles(outer.id).isEmpty())
    }

    @Test
    fun folderIntoItself_isRejected_andNothingChanges() = runTest {
        val a = repository.createFolder(null, "A")
        val word = repository.createWord(null, "w")

        val result = move(setOf(a.id, word.id), a.id)

        assertEquals(MoveResult.TargetInsideMovedFolder, result)
        assertEquals(listOf("A", "w"), titles(null))
        assertTrue(titles(a.id).isEmpty())
    }

    @Test
    fun folderIntoItsDeepDescendant_isRejected_andNothingChanges() = runTest {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        val word = repository.createWord(null, "w")

        val result = move(setOf(word.id, a.id), c.id)

        assertEquals(MoveResult.TargetInsideMovedFolder, result)
        assertEquals(listOf("A", "w"), titles(null)) // ani slovo se nepřesunulo
        assertEquals(listOf("B"), titles(a.id))
        assertEquals(listOf("C"), titles(b.id))
        assertTrue(titles(c.id).isEmpty())
    }

    @Test
    fun folderIntoItsSiblingOrIntoItsParent_isAllowed() = runTest {
        val a = repository.createFolder(null, "A")
        val sibling = repository.createFolder(null, "Sourozenec")
        val child = repository.createFolder(a.id, "Dítě")

        assertEquals(MoveResult.Moved, move(setOf(a.id), sibling.id))
        assertEquals(MoveResult.Moved, move(setOf(child.id), null))

        assertEquals(listOf("Sourozenec", "Dítě"), titles(null))
        assertEquals(listOf("A"), titles(sibling.id))
    }

    @Test
    fun missingTarget_isRejected_andNothingChanges() = runTest {
        val word = repository.createWord(null, "w")

        assertEquals(MoveResult.TargetNotFound, move(setOf(word.id), "neexistuje"))

        assertEquals(listOf("w"), titles(null))
    }
}
