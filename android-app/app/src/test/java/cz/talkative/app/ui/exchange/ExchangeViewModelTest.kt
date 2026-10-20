package cz.talkative.app.ui.exchange

import cz.talkative.app.domain.exchange.ArchiveLayout
import cz.talkative.app.domain.exchange.ArchiveReader
import cz.talkative.app.domain.exchange.ArchiveSource
import cz.talkative.app.domain.exchange.ArchiveSourceFactory
import cz.talkative.app.domain.exchange.ImportArchiveUseCase
import cz.talkative.app.domain.exchange.ExportDestination
import cz.talkative.app.domain.exchange.ExportDestinationFactory
import cz.talkative.app.domain.exchange.ExportDictionaryUseCase
import cz.talkative.app.domain.exchange.ExportError
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.testing.FakeAudioStore
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
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ExchangeViewModelTest {

    private class Destination(val output: OutputStream) : ExportDestination {
        var discarded = false
        override fun open() = output
        override suspend fun discard() {
            discarded = true
        }
    }

    private val repository = FakeDictionaryRepository()
    private val written = ByteArrayOutputStream()
    private var destination = Destination(written)
    private val requestedUris = mutableListOf<String>()
    private val archiveSources = mutableMapOf<String, ArchiveSource>()
    private lateinit var viewModel: ExchangeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val export = ExportDictionaryUseCase(repository, FakeImageStore(), FakeAudioStore(), FakeConfigRepository(AppConfig()))
        viewModel = ExchangeViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            export,
            ExportDestinationFactory { uri ->
                requestedUris += uri
                destination
            },
            ArchiveReader(),
            ImportArchiveUseCase(ArchiveReader(), repository, FakeImageStore(), FakeAudioStore(), FakeConfigRepository()),
            ArchiveSourceFactory { uri -> archiveSources.getValue(uri) },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    /** Export běží na vlákně IO, proto se čeká na skutečný konec, ne na virtuální čas. */
    private fun awaitFinished() = runBlocking {
        withTimeout(5_000) { viewModel.uiState.first { it.export !is ExportStatus.Running && it.export !is ExportStatus.Idle } }
    }

    private fun zipNames(): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(written.toByteArray())).use { zip ->
            while (true) names += (zip.nextEntry ?: break).name
        }
        return names
    }

    @Test
    fun startsWithWholeDictionaryAndNoConfig() {
        assertNull(state.exportFolder)
        assertEquals(false, state.includeConfig)
        assertEquals(ExportStatus.Idle, state.export)
    }

    @Test
    fun exportToFile_writesTheArchive_andReportsCounts() = runBlocking {
        val food = repository.createFolder(null, "Jídlo")
        repository.createWord(food.id, "chleba")
        repository.createWord(null, "chci")

        viewModel.onEvent(ExchangeEvent.ExportToFile("content://soubor"))
        awaitFinished()

        assertEquals(listOf("content://soubor"), requestedUris)
        assertEquals(ExportStatus.Done(folders = 1, words = 2, missingMedia = 0), state.export)
        assertEquals(listOf(ArchiveLayout.MANIFEST), zipNames())
    }

    @Test
    fun picker_choosesFolderToExport_andOnlyItsContentIsWritten() = runBlocking {
        val food = repository.createFolder(null, "Jídlo")
        val fruit = repository.createFolder(food.id, "Ovoce")
        repository.createWord(fruit.id, "jablko")
        repository.createWord(null, "chci")

        viewModel.onEvent(ExchangeEvent.ChooseExportFolder)
        assertEquals(listOf("Jídlo"), state.picker!!.folders.map { it.title })
        viewModel.onEvent(ExchangeEvent.BrowsePicker(food.id))
        assertEquals(listOf("Ovoce"), state.picker!!.folders.map { it.title })
        viewModel.onEvent(ExchangeEvent.BrowsePicker(fruit.id))
        viewModel.onEvent(ExchangeEvent.ConfirmPicker)

        assertNull(state.picker)
        assertEquals("Ovoce", state.exportFolder!!.title)

        viewModel.onEvent(ExchangeEvent.ExportToFile("content://soubor"))
        awaitFinished()
        assertEquals(ExportStatus.Done(folders = 1, words = 1, missingMedia = 0), state.export)
    }

    @Test
    fun picker_atRoot_doesNotChangeTheScope_andWholeDictionaryResetsIt() = runBlocking {
        val food = repository.createFolder(null, "Jídlo")

        viewModel.onEvent(ExchangeEvent.ChooseExportFolder)
        viewModel.onEvent(ExchangeEvent.ConfirmPicker) // v kořeni se nic nevybere
        assertNull(state.exportFolder)

        viewModel.onEvent(ExchangeEvent.BrowsePicker(food.id))
        viewModel.onEvent(ExchangeEvent.ConfirmPicker)
        assertEquals(food.id, state.exportFolder!!.id)

        viewModel.onEvent(ExchangeEvent.ExportWholeDictionary)
        assertNull(state.exportFolder)
    }

    @Test
    fun dismissedPicker_keepsTheScope() = runBlocking {
        repository.createFolder(null, "Jídlo")

        viewModel.onEvent(ExchangeEvent.ChooseExportFolder)
        viewModel.onEvent(ExchangeEvent.DismissPicker)

        assertNull(state.picker)
        assertNull(state.exportFolder)
    }

    @Test
    fun includeConfig_isPassedToTheExport() = runBlocking {
        viewModel.onEvent(ExchangeEvent.IncludeConfigChanged(true))
        viewModel.onEvent(ExchangeEvent.ExportToFile("content://soubor"))
        awaitFinished()

        val manifest = ZipInputStream(ByteArrayInputStream(written.toByteArray())).use { zip ->
            zip.nextEntry
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(manifest.contains("\"config\""))
    }

    @Test
    fun failedWrite_isReported_andThePartialFileDiscarded() = runBlocking {
        repository.createWord(null, "chci")
        destination = Destination(object : OutputStream() {
            override fun write(b: Int) = throw IOException("selhalo")
        })

        viewModel.onEvent(ExchangeEvent.ExportToFile("content://soubor"))
        awaitFinished()

        assertEquals(ExportStatus.Failed(ExportError.WRITE_FAILED), state.export)
        assertTrue(destination.discarded)
    }

    @Test
    fun changingOptionsAfterAnExport_clearsTheOldResult() = runBlocking {
        viewModel.onEvent(ExchangeEvent.ExportToFile("content://soubor"))
        awaitFinished()

        viewModel.onEvent(ExchangeEvent.IncludeConfigChanged(true))

        assertEquals(ExportStatus.Idle, state.export)
    }
}
