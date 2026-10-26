package cz.talkative.app.domain.integrity

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryIntegrityTest {

    private fun folder(id: String, parent: String? = null) = Folder(id, "default", id, parent, 0, 0)
    private fun word(id: String, parent: String? = null, image: String? = null, audio: String? = null) =
        Word(id, "default", id, parent, 0, 0, imageFile = image, audioFile = audio)

    // ---- chybějící soubor

    @Test
    fun wordWithoutFiles_hasNoProblem() {
        assertTrue(DictionaryIntegrity.fileProblems(word("w"), { false }, { false }).isEmpty())
    }

    @Test
    fun missingImageFile_isReported() {
        val problems = DictionaryIntegrity.fileProblems(word("w", image = "a.png"), { false }, { true })
        assertEquals(setOf(ItemProblem.MISSING_IMAGE), problems)
    }

    @Test
    fun missingAudioFile_isReported_andExistingOneIsNot() {
        val missing = DictionaryIntegrity.fileProblems(word("w", image = "a.png", audio = "a.m4a"), { true }, { false })
        assertEquals(setOf(ItemProblem.MISSING_AUDIO), missing)
        assertTrue(DictionaryIntegrity.fileProblems(word("w", image = "a.png", audio = "a.m4a"), { true }, { true }).isEmpty())
    }

    @Test
    fun bothFilesMissing_areBothReported() {
        val problems = DictionaryIntegrity.fileProblems(word("w", image = "a.png", audio = "a.m4a"), { false }, { false })
        assertEquals(setOf(ItemProblem.MISSING_IMAGE, ItemProblem.MISSING_AUDIO), problems)
    }

    // ---- neexistující rodič

    @Test
    fun healthyTree_hasNothingUnreachable() {
        val folders = listOf(folder("a"), folder("b", "a"))
        val words = listOf(word("w1"), word("w2", "b"))
        assertTrue(DictionaryIntegrity.unreachableIds(folders, words).isEmpty())
        assertTrue(DictionaryIntegrity.recoveryMoves(folders, words).isEmpty())
    }

    @Test
    fun wordInMissingFolder_isUnreachable_andMoved() {
        val words = listOf(word("ok"), word("sirotek", "neexistuje"))
        assertEquals(setOf("sirotek"), DictionaryIntegrity.unreachableIds(emptyList(), words))
        assertEquals(setOf("sirotek"), DictionaryIntegrity.recoveryMoves(emptyList(), words))
    }

    @Test
    fun orphanFolder_isMovedTogetherWithItsSubtree_onlyTheTopOneMoves() {
        val folders = listOf(folder("a", "smazana"), folder("b", "a"))
        val words = listOf(word("w", "b"))
        assertEquals(setOf("a", "b", "w"), DictionaryIntegrity.unreachableIds(folders, words))
        assertEquals(setOf("a"), DictionaryIntegrity.recoveryMoves(folders, words))
    }

    // ---- konflikt při synchronizaci: dvě zařízení si přesunou složky do sebe

    @Test
    fun cycleOfFolders_isBrokenByMovingTheSmallestId() {
        val folders = listOf(folder("b", "a"), folder("a", "b"), folder("c"))
        assertEquals(setOf("a", "b"), DictionaryIntegrity.unreachableIds(folders, emptyList()))
        assertEquals(setOf("a"), DictionaryIntegrity.recoveryMoves(folders, emptyList()))
    }

    @Test
    fun folderHangingOnACycle_doesNotBecomeTheCycleBreaker() {
        val folders = listOf(folder("a", "b"), folder("b", "a"), folder("0", "a"))
        assertEquals(setOf("a"), DictionaryIntegrity.recoveryMoves(folders, emptyList()))
    }

    @Test
    fun selfParentFolder_isACycle() {
        assertEquals(setOf("x"), DictionaryIntegrity.recoveryMoves(listOf(folder("x", "x")), emptyList()))
    }

    @Test
    fun recoveryResult_doesNotDependOnInputOrder() {
        val folders = listOf(folder("a", "b"), folder("b", "c"), folder("c", "a"), folder("d", "zadna"))
        assertEquals(
            DictionaryIntegrity.recoveryMoves(folders, emptyList()),
            DictionaryIntegrity.recoveryMoves(folders.reversed(), emptyList()),
        )
        assertEquals(setOf("a", "d"), DictionaryIntegrity.recoveryMoves(folders, emptyList()))
    }
}
