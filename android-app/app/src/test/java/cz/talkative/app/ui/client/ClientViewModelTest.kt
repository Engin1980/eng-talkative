package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.repository.ConfigRepository
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
        viewModel = ClientViewModel(ObserveFolderItemsUseCase(repository), ObserveConfigUseCase(configRepository))
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
}
