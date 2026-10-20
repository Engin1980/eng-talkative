package cz.talkative.app.ui.exchange

import cz.talkative.app.domain.exchange.ArchiveLayout
import cz.talkative.app.domain.exchange.ArchiveReader
import cz.talkative.app.domain.exchange.ArchiveSource
import cz.talkative.app.domain.exchange.ArchiveSourceFactory
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ImportArchiveUseCase
import cz.talkative.app.domain.exchange.ImportError
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeArchiveNotice
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Import ve view modelu: výběr archivu, výběr složek, cíl, nahrazení a výsledek. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeViewModelImportTest {

    private val repository = FakeDictionaryRepository()
    private val archiveSources = mutableMapOf<String, ArchiveSource>()
    private lateinit var viewModel: ExchangeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val images = FakeImageStore()
        val audio = FakeAudioStore()
        val config = FakeConfigRepository()
        viewModel = ExchangeViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            ExportDictionaryUseCase(repository, images, audio, config, FakeArchiveNotice()),
            { error("export se tu nepoužívá") },
            ArchiveReader(),
            ImportArchiveUseCase(ArchiveReader(), repository, images, audio, config),
            ArchiveSourceFactory { uri -> archiveSources.getValue(uri) },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value
    private val importState get() = state.import

    private fun zipSource(manifestText: String): ArchiveSource {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ArchiveLayout.MANIFEST))
            zip.write(manifestText.toByteArray())
            zip.closeEntry()
        }
        val bytes = out.toByteArray()
        return object : ArchiveSource {
            override fun open() = ByteArrayInputStream(bytes)
        }
    }

    private val sampleManifest = """
        {"schemaVersion":1,
         "folders":[{"id":"food","title":"Jídlo"},{"id":"fruit","title":"Ovoce","parentId":"food"},{"id":"drink","title":"Pití","sortOrder":1}],
         "words":[{"id":"w1","title":"chci"},{"id":"w2","title":"jablko","parentId":"fruit"},{"id":"w3","title":"voda","parentId":"drink"}],
         "config":{"addWordMode":"DRAG"}}
    """.trimIndent()

    /** Čtení archivu běží na vlákně IO, proto se čeká na skutečný konec, ne na virtuální čas. */
    private fun awaitRead() = runBlocking {
        withTimeout(5_000) { viewModel.uiState.first { it.import.status != ImportStatus.Reading } }
    }

    private fun awaitImport() = runBlocking {
        withTimeout(5_000) {
            viewModel.uiState.first { it.import.status is ImportStatus.Done || it.import.status is ImportStatus.Failed }
        }
    }

    private fun pickArchive(manifestText: String = sampleManifest) {
        archiveSources["content://archiv"] = zipSource(manifestText)
        viewModel.onEvent(ExchangeEvent.ArchivePicked("content://archiv"))
        awaitRead()
    }

    private fun chooseTarget(folderId: String?) {
        viewModel.onEvent(ExchangeEvent.ChooseImportTarget)
        if (folderId != null) viewModel.onEvent(ExchangeEvent.BrowsePicker(folderId))
        viewModel.onEvent(ExchangeEvent.ConfirmPicker)
    }

    @Test
    fun pickedArchive_isCheckedAndSummarised() {
        pickArchive()

        val archive = importState.archive!!
        assertEquals(3, archive.folderCount)
        assertEquals(3, archive.wordCount)
        assertTrue(archive.hasRootWords)
        assertTrue(archive.hasConfig)
        assertEquals(listOf("Jídlo", "Ovoce", "Pití"), archive.folders.map { it.folder.title })
        assertEquals(3, importState.plannedFolders)
        assertEquals(3, importState.plannedWords)
        assertEquals(ImportStatus.Idle, importState.status)
        assertTrue(importState.canImport)
    }

    @Test
    fun rejectedArchive_showsTheErrorAndKeepsNothing() {
        archiveSources["content://zly"] = object : ArchiveSource {
            override fun open() = ByteArrayInputStream("tohle není zip".toByteArray())
        }

        viewModel.onEvent(ExchangeEvent.ArchivePicked("content://zly"))
        awaitRead()

        assertEquals(ImportStatus.Failed(ImportError.NOT_AN_ARCHIVE), importState.status)
        assertNull(importState.archive)
        assertEquals(false, importState.canImport)
    }

    @Test
    fun pickingAnotherArchive_replacesThePreviousOne() {
        pickArchive()
        viewModel.onEvent(ExchangeEvent.ImportSelectAllChanged(false))

        pickArchive("""{"schemaVersion":1,"folders":[],"words":[{"id":"x","title":"jen jedno"}]}""")

        assertEquals(1, importState.archive!!.wordCount)
        assertTrue(importState.selectAll) // výběr se vrátil na výchozí
    }

    @Test
    fun selectingFolders_updatesTheCounts_andSubfoldersAreCoveredByTheirParent() {
        pickArchive()

        viewModel.onEvent(ExchangeEvent.ImportSelectAllChanged(false))
        assertEquals(false, importState.canImport) // nic není vybrané
        viewModel.onEvent(ExchangeEvent.ToggleImportFolder("food"))

        assertTrue(importState.isCovered("food") && importState.isCovered("fruit"))
        assertEquals(false, importState.isCovered("drink"))
        assertEquals(2, importState.plannedFolders)
        assertEquals(1, importState.plannedWords)
        assertTrue(importState.canImport)

        viewModel.onEvent(ExchangeEvent.IncludeRootWordsChanged(true))
        assertEquals(2, importState.plannedWords)

        viewModel.onEvent(ExchangeEvent.ToggleImportFolder("food"))
        viewModel.onEvent(ExchangeEvent.IncludeRootWordsChanged(false))
        assertEquals(false, importState.canImport)
    }

    @Test
    fun import_addsToTheRoot_andReportsCounts_andKeepsOldData() = runBlocking {
        repository.createWord(null, "stará")
        pickArchive()

        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        assertEquals(ImportStatus.Done(folders = 3, words = 3, configImported = false), importState.status)
        assertEquals(setOf("stará", "chci", "jablko", "voda"), repository.allWords.map { it.title }.toSet())
    }

    @Test
    fun partialImport_bringsOnlyTheSelectedFolders() {
        pickArchive()
        viewModel.onEvent(ExchangeEvent.ImportSelectAllChanged(false))
        viewModel.onEvent(ExchangeEvent.ToggleImportFolder("drink"))

        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        assertEquals(ImportStatus.Done(folders = 1, words = 1, configImported = false), importState.status)
        assertEquals(listOf("voda"), repository.allWords.map { it.title })
        assertEquals(listOf("Pití"), repository.allFolders.map { it.title })
    }

    @Test
    fun import_intoChosenTargetFolder() = runBlocking {
        val target = repository.createFolder(null, "Cíl")
        pickArchive()

        viewModel.onEvent(ExchangeEvent.ChooseImportTarget)
        assertEquals(PickerPurpose.IMPORT_TARGET, state.picker!!.purpose)
        viewModel.onEvent(ExchangeEvent.BrowsePicker(target.id))
        assertEquals(PickerPurpose.IMPORT_TARGET, state.picker!!.purpose) // účel se při procházení nemění
        viewModel.onEvent(ExchangeEvent.ConfirmPicker)
        assertEquals(target.id, importState.target!!.id)

        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        assertEquals(target.id, repository.allWords.single { it.title == "chci" }.parentId)
    }

    @Test
    fun importTarget_canBeResetToTheRoot() = runBlocking {
        val target = repository.createFolder(null, "Cíl")
        pickArchive()
        chooseTarget(target.id)

        chooseTarget(null) // v kořeni se u importu smí potvrdit

        assertNull(importState.target)
        assertNull(state.picker)
    }

    @Test
    fun exportPickerAtRoot_stillDoesNotChangeTheExportScope() = runBlocking {
        repository.createFolder(null, "Jídlo")

        viewModel.onEvent(ExchangeEvent.ChooseExportFolder)
        viewModel.onEvent(ExchangeEvent.ConfirmPicker)

        assertNull(state.exportFolder)
    }

    @Test
    fun replacingTheDictionary_asksFirst_andOnlyConfirmationChangesData() = runBlocking {
        repository.createWord(null, "stará")
        pickArchive()
        viewModel.onEvent(ExchangeEvent.ReplaceAllChanged(true))

        viewModel.onEvent(ExchangeEvent.StartImport)
        assertTrue(importState.confirmReplace)
        assertEquals(listOf("stará"), repository.allWords.map { it.title })

        viewModel.onEvent(ExchangeEvent.DismissReplace)
        assertEquals(false, importState.confirmReplace)
        assertEquals(listOf("stará"), repository.allWords.map { it.title })

        viewModel.onEvent(ExchangeEvent.StartImport)
        viewModel.onEvent(ExchangeEvent.ConfirmReplace)
        awaitImport()

        assertEquals(setOf("chci", "jablko", "voda"), repository.allWords.map { it.title }.toSet())
        assertEquals(false, importState.replaceAll) // po nahrazení se volba vypne
    }

    @Test
    fun configIsImportedOnlyWhenTheUserAsksForIt() {
        pickArchive()
        viewModel.onEvent(ExchangeEvent.ImportConfigChanged(true))

        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        assertEquals(ImportStatus.Done(folders = 3, words = 3, configImported = true), importState.status)
    }

    @Test
    fun changingOptionsAfterAnImport_clearsTheOldResult() {
        pickArchive()
        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        viewModel.onEvent(ExchangeEvent.ImportSelectAllChanged(false))

        assertEquals(ImportStatus.Idle, importState.status)
    }

    @Test
    fun targetThatDisappeared_failsWithoutChanges() = runBlocking {
        val target = repository.createFolder(null, "Cíl")
        pickArchive()
        chooseTarget(target.id)
        repository.deleteFolder(target.id)

        viewModel.onEvent(ExchangeEvent.StartImport)
        awaitImport()

        assertEquals(ImportStatus.Failed(ImportError.TARGET_MISSING), importState.status)
        assertTrue(repository.allWords.isEmpty())
    }
}
