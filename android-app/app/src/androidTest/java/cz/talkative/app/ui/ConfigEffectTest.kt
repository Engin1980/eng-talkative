package cz.talkative.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.UpdateConfigUseCase
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.config.ConfigScreen
import cz.talkative.app.ui.config.ConfigViewModel
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Změna v obrazovce konfigurace se hned projeví v klientovi (sdílená konfigurace, skutečný view model nastavení). */
@RunWith(AndroidJUnit4::class)
class ConfigEffectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeConfigRepository()
    private val words = (1..12).map { Word("w$it", "default", "slovo$it", null, it, 0) }

    @Test
    fun biggerGrid_changesThePagesInClientAfterReturningFromSettings() {
        val viewModel = ConfigViewModel(ObserveConfigUseCase(repository), UpdateConfigUseCase(repository))
        composeRule.setContent {
            TalkativeTheme {
                var inConfig by remember { mutableStateOf(true) }
                val config by repository.config.collectAsState(initial = AppConfig())
                val configState by viewModel.uiState.collectAsState()
                if (inConfig) {
                    ConfigScreen(state = configState, onEvent = viewModel::onEvent, onBack = { inConfig = false })
                } else {
                    ClientScreen(
                        state = ClientUiState(items = words, config = config, isLoading = false),
                        onEvent = {},
                        onOpenAdmin = {},
                        isLandscape = false,
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("+")[3].performScrollTo().performClick() // sloupce na výšku: 3 -> 4, tj. 4×3 = 12 slov
        composeRule.onNodeWithText("Zpět do administrace").performClick()

        composeRule.onNodeWithText("slovo12").assertExists() // 12 slov se vejde na jednu stránku
        composeRule.onAllNodesWithText("Další").assertCountEquals(0)
    }
}
