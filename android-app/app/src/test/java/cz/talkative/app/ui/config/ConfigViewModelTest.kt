package cz.talkative.app.ui.config

import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.ConfigLimits
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.UpdateConfigUseCase
import cz.talkative.app.testing.FakeConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {

    private val repository = FakeConfigRepository()
    private lateinit var viewModel: ConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ConfigViewModel(ObserveConfigUseCase(repository), UpdateConfigUseCase(repository))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.subscribe() {
        viewModel.uiState.launchIn(backgroundScope)
    }

    @Test
    fun startsWithDefaults() = runVmTest {
        subscribe()

        assertEquals(AppConfig(), viewModel.uiState.value.config)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun everySetting_isStoredInConfiguration() = runVmTest {
        subscribe()

        viewModel.onEvent(ConfigEvent.AddWordModeChanged(AddWordMode.DRAG))
        viewModel.onEvent(ConfigEvent.AllowReorderChanged(false))
        viewModel.onEvent(ConfigEvent.RemoveHoldChanged(2_000))
        viewModel.onEvent(ConfigEvent.AdminHoldChanged(5_000))
        viewModel.onEvent(ConfigEvent.SpeakWordOnTapChanged(true))
        viewModel.onEvent(ConfigEvent.GridChanged(landscape = false, grid = GridSize(2, 4)))
        viewModel.onEvent(ConfigEvent.GridChanged(landscape = true, grid = GridSize(5, 2)))
        viewModel.onEvent(ConfigEvent.CollationChanged("sk"))
        viewModel.onEvent(ConfigEvent.HideCaptionsChanged(true))
        viewModel.onEvent(ConfigEvent.AutoClearChanged(true))

        assertEquals(
            AppConfig(
                addWordMode = AddWordMode.DRAG,
                allowReorderInSentence = false,
                removeHoldMillis = 2_000,
                adminHoldMillis = 5_000,
                speakWordOnTap = true,
                gridPortrait = GridSize(2, 4),
                gridLandscape = GridSize(5, 2),
                collationLanguageTag = "sk",
                hideCaptionsInSentence = true,
                autoClearAfterPlayback = true,
            ),
            repository.current,
        )
        assertEquals(repository.current, viewModel.uiState.value.config)
    }

    @Test
    fun valuesOutsideLimits_areClampedBeforeStoring() = runVmTest {
        subscribe()

        viewModel.onEvent(ConfigEvent.GridChanged(landscape = false, grid = GridSize(0, 50)))
        viewModel.onEvent(ConfigEvent.RemoveHoldChanged(1))
        viewModel.onEvent(ConfigEvent.AdminHoldChanged(1_000_000))

        assertEquals(GridSize(ConfigLimits.MIN_GRID_SIDE, ConfigLimits.MAX_GRID_SIDE), repository.current.gridPortrait)
        assertEquals(ConfigLimits.MIN_REMOVE_HOLD_MILLIS, repository.current.removeHoldMillis)
        assertEquals(ConfigLimits.MAX_ADMIN_HOLD_MILLIS, repository.current.adminHoldMillis)
    }

    @Test
    fun reset_asksFirst_cancelKeepsValues_confirmRestoresDefaults() = runVmTest {
        subscribe()
        viewModel.onEvent(ConfigEvent.SpeakWordOnTapChanged(true))
        viewModel.onEvent(ConfigEvent.GridChanged(landscape = true, grid = GridSize(6, 6)))

        viewModel.onEvent(ConfigEvent.ResetRequested)
        assertTrue(viewModel.uiState.value.confirmReset)
        assertTrue(repository.current.speakWordOnTap) // zatím se nic nezměnilo

        viewModel.onEvent(ConfigEvent.ResetDismissed)
        assertFalse(viewModel.uiState.value.confirmReset)
        assertTrue(repository.current.speakWordOnTap)

        viewModel.onEvent(ConfigEvent.ResetRequested)
        viewModel.onEvent(ConfigEvent.ResetConfirmed)
        assertEquals(AppConfig(), repository.current)
        assertFalse(viewModel.uiState.value.confirmReset)
    }
}
