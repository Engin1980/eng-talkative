package cz.talkative.app.domain.exchange

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlanTest {

    private fun manifest(folders: List<ArchiveFolder> = emptyList(), words: List<ArchiveWord> = emptyList()) =
        ArchiveManifest(ARCHIVE_SCHEMA_VERSION, folders, words)

    /** chci(0) · Jídlo(1){ chleba(0), Ovoce(1){ jablko(0) } } · Pití(2){ voda(5) } · ahoj(3) */
    private val sample = manifest(
        folders = listOf(
            ArchiveFolder("food", "Jídlo", null, 1),
            ArchiveFolder("fruit", "Ovoce", "food", 1),
            ArchiveFolder("drink", "Pití", null, 2),
        ),
        words = listOf(
            ArchiveWord("chci", "chci", null, 0),
            ArchiveWord("bread", "chleba", "food", 0),
            ArchiveWord("apple", "jablko", "fruit", 0),
            ArchiveWord("water", "voda", "drink", 5),
            ArchiveWord("hi", "ahoj", null, 3),
        ),
    )

    private fun ImportPlan.folderLayout() = folders.map { Triple(it.key, it.parentKey, it.sortOrder) }
    private fun ImportPlan.wordLayout() = words.map { Triple(it.word.id, it.parentKey, it.sortOrder) }

    @Test
    fun all_keepsTheWholeTree_andRenumbersEachFolderFromZero() {
        val plan = planImport(sample, ImportSelection.All)

        assertEquals(setOf("food", "fruit", "drink"), plan.folders.map { it.key }.toSet())
        assertEquals(5, plan.words.size)
        assertEquals(Triple("fruit", "food", 1), plan.folderLayout().single { it.first == "fruit" })
        val top = (plan.folders.filter { it.parentKey == null }.map { it.key to it.sortOrder } +
            plan.words.filter { it.parentKey == null }.map { it.word.id to it.sortOrder }).sortedBy { it.second }
        assertEquals(listOf("chci", "food", "drink", "hi"), top.map { it.first })
        assertEquals(listOf(0, 1, 2, 3), top.map { it.second })
        assertEquals(0, plan.words.single { it.word.id == "water" }.sortOrder) // 5 -> 0, jediné slovo ve složce
    }

    @Test
    fun orderTies_putFoldersBeforeWords_thenFollowFileOrder() {
        val tied = manifest(
            folders = listOf(ArchiveFolder("f", "F", null, 0)),
            words = listOf(ArchiveWord("w1", "a", null, 0), ArchiveWord("w2", "b", null, 0)),
        )

        val plan = planImport(tied, ImportSelection.All)

        assertEquals(0, plan.folders.single().sortOrder)
        assertEquals(listOf("w1" to 1, "w2" to 2), plan.words.map { it.word.id to it.sortOrder })
    }

    @Test
    fun selectedFolder_bringsItsSubtree_andBecomesTopLevel() {
        val plan = planImport(sample, ImportSelection.Folders(setOf("food")))

        assertEquals(setOf("food", "fruit"), plan.folders.map { it.key }.toSet())
        assertEquals(setOf("bread", "apple"), plan.words.map { it.word.id }.toSet())
        assertEquals(Triple("food", null, 0), plan.folderLayout().single { it.first == "food" })
    }

    @Test
    fun selectedSubfolder_withoutItsParent_becomesTopLevel() {
        val plan = planImport(sample, ImportSelection.Folders(setOf("fruit")))

        assertEquals(listOf(Triple("fruit", null, 0)), plan.folderLayout())
        assertEquals(listOf(Triple("apple", "fruit", 0)), plan.wordLayout())
    }

    @Test
    fun parentAndChildBothSelected_importsTheChildOnlyInsideTheParent() {
        val plan = planImport(sample, ImportSelection.Folders(setOf("fruit", "food")))

        assertEquals(setOf("food", "fruit"), plan.folders.map { it.key }.toSet())
        assertEquals("food", plan.folders.single { it.key == "fruit" }.parentKey)
        assertEquals(1, plan.folders.count { it.parentKey == null })
    }

    @Test
    fun severalSelectedFolders_keepTheirOrderInTheArchive() {
        val plan = planImport(sample, ImportSelection.Folders(setOf("drink", "food")))

        assertEquals(listOf("food" to 0, "drink" to 1), plan.folders.filter { it.parentKey == null }.map { it.key to it.sortOrder })
    }

    @Test
    fun rootWords_areIncludedOnlyOnRequest() {
        val without = planImport(sample, ImportSelection.Folders(setOf("drink")))
        val with = planImport(sample, ImportSelection.Folders(setOf("drink"), includeRootWords = true))

        assertEquals(setOf("water"), without.words.map { it.word.id }.toSet())
        assertEquals(setOf("water", "chci", "hi"), with.words.map { it.word.id }.toSet())
        // chci(0) < Pití(2) < ahoj(3) podle pořadí v archivu
        val top = (with.folders.filter { it.parentKey == null }.map { it.key to it.sortOrder } +
            with.words.filter { it.parentKey == null }.map { it.word.id to it.sortOrder }).sortedBy { it.second }
        assertEquals(listOf("chci", "drink", "hi"), top.map { it.first })
    }

    @Test
    fun unknownFolderIds_areIgnored() {
        val plan = planImport(sample, ImportSelection.Folders(setOf("neni")))

        assertEquals(ImportPlan(emptyList(), emptyList()), plan)
    }

    @Test
    fun emptyArchive_givesEmptyPlan() {
        assertEquals(ImportPlan(emptyList(), emptyList()), planImport(manifest(), ImportSelection.All))
    }

    @Test
    fun listFolders_isDepthFirstWithDepth() {
        assertEquals(
            listOf("food" to 0, "fruit" to 1, "drink" to 0),
            listFolders(sample).map { it.folder.id to it.depth },
        )
    }
}
