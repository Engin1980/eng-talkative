package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModelTest {

    private val repository = FakeDictionaryRepository()
    private val configFlow = MutableStateFlow(AppConfig())
    private val configRepository = object : ConfigRepository {
        override val config: Flow<AppConfig> = configFlow
        override suspend fun update(transform: (AppConfig) -> AppConfig) {
            configFlow.value = transform(configFlow.value)
        }
    }
    private lateinit var viewModel: ClientViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ClientViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            ObserveConfigUseCase(configRepository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    /** `stateIn(WhileSubscribed)` pracuje jen při odběru, proto se stav v testu odebírá na pozadí. */
    private fun TestScope.subscribe() {
        viewModel.uiState.launchIn(backgroundScope)
    }

    private fun titles() = viewModel.uiState.value.items.map { it.title }

    @Test
    fun initialState_isLoading() {
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun showsRootContentInManualOrder() = runVmTest {
        repository.createFolder(null, "Jídlo")
        repository.createWord(null, "Chci")
        subscribe()

        assertEquals(listOf("Jídlo", "Chci"), titles())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun openFolder_showsItsContent_andResetsPage() = runVmTest {
        val food = repository.createFolder(null, "Jídlo")
        repository.createWord(food.id, "Chleba")
        repository.createWord(null, "Ahoj")
        subscribe()
        viewModel.onEvent(ClientEvent.PageChanged(1))

        viewModel.onEvent(ClientEvent.OpenFolder(food.id))

        assertEquals(listOf("Chleba"), titles())
        assertEquals(0, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun pageChange_isKeptInState() = runVmTest {
        subscribe()

        viewModel.onEvent(ClientEvent.PageChanged(2))

        assertEquals(2, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun newItemsInCurrentFolder_appearLive() = runVmTest {
        subscribe()
        assertTrue(titles().isEmpty())

        repository.createWord(null, "Nové")

        assertEquals(listOf("Nové"), titles())
    }

    @Test
    fun config_isTakenFromRepository_andFollowsChanges() = runVmTest {
        subscribe()
        assertEquals(10_000L, viewModel.uiState.value.config.adminHoldMillis)

        configFlow.value = AppConfig(adminHoldMillis = 3_000L)

        assertEquals(3_000L, viewModel.uiState.value.config.adminHoldMillis)
    }

    @Test
    fun foldersAreOpenedByTheirId() = runVmTest {
        val a = repository.createFolder(null, "A")
        subscribe()

        assertTrue(viewModel.uiState.value.items.single() is Folder)
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))

        assertTrue(titles().isEmpty())
    }

    private fun path() = viewModel.uiState.value.path.map { it.title }

    /** Strom: Úvod › A › B › C, v každé složce jedno slovo. */
    private suspend fun buildTree(): List<Folder> {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        repository.createWord(a.id, "slovoA")
        repository.createWord(c.id, "slovoC")
        return listOf(a, b, c)
    }

    @Test
    fun breadcrumbs_followTheCurrentPath() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        assertTrue(path().isEmpty())

        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        assertEquals(listOf("A"), path())
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        assertEquals(listOf("A", "B", "C"), path())
        assertEquals(listOf("slovoC"), titles())
    }

    @Test
    fun home_goesToRootFromAnyDepth_andResetsPage() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))
        viewModel.onEvent(ClientEvent.PageChanged(3))

        viewModel.onEvent(ClientEvent.GoHome)

        assertTrue(path().isEmpty())
        assertEquals(listOf("A"), titles())
        assertEquals(0, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun back_goesUpExactlyOneLevel_andStaysInRoot() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        viewModel.onEvent(ClientEvent.NavigateUp)
        assertEquals(listOf("A", "B"), path())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertEquals(listOf("A"), path())
        assertEquals(listOf("B", "slovoA"), titles())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertTrue(path().isEmpty())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertTrue(path().isEmpty())
    }

    @Test
    fun clickingAncestorCrumb_jumpsThere() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        viewModel.onEvent(ClientEvent.GoToFolder(a.id))
        assertEquals(listOf("A"), path())

        viewModel.onEvent(ClientEvent.GoToFolder(null))
        assertTrue(path().isEmpty())
    }
}
