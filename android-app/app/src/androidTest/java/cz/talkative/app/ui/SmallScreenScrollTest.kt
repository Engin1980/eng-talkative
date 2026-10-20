package cz.talkative.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.R
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.Word
import cz.talkative.app.testing.NoSoftKeyboardRule
import cz.talkative.app.ui.about.AboutScreen
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.arasaac.ArasaacScreen
import cz.talkative.app.ui.arasaac.ArasaacUiState
import cz.talkative.app.ui.common.ScrollableColumn
import cz.talkative.app.ui.config.ConfigScreen
import cz.talkative.app.ui.config.ConfigUiState
import cz.talkative.app.ui.draw.DrawScreen
import cz.talkative.app.ui.draw.DrawUiState
import cz.talkative.app.ui.exchange.ExchangeScreen
import cz.talkative.app.ui.exchange.ExchangeUiState
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Obrazovky a posouvaný obsah dialogů na uměle nízkém okně (240 dp) a se zvětšeným písmem (150 %):
 * poslední prvek musí jít dosáhnout posouváním a klepnout na něj.
 */
@RunWith(AndroidJUnit4::class)
class SmallScreenScrollTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun scrollList() = composeRule.onNode(hasScrollToNodeAction())

    /** Okno 700 × 240 dp a písmo 150 %; obsah se musí vejít posouváním. */
    private fun showSmall(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                TalkativeTheme {
                    Box(Modifier.width(700.dp).height(240.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun admin_listAndHeaderAreReachableWhenWindowIsLow() {
        val words = (1..12).map { Word("w$it", "default", "Slovo $it", null, it, 0) }
        var backs = 0
        showSmall {
            AdminScreen(
                state = AdminUiState(items = words, isLoading = false),
                onEvent = {},
                onBackToClient = { backs++ },
            )
        }

        // Seznam je líný: položky mimo okno ještě neexistují, proto se posouvá k uzlu, ne „na uzel“.
        scrollList().performScrollToNode(hasText("Slovo 12"))
        composeRule.onNodeWithText("Slovo 12").assertIsDisplayed()
        scrollList().performScrollToNode(hasText(string(R.string.admin_back_to_client)))
        composeRule.onNodeWithText(string(R.string.admin_back_to_client)).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun config_resetButtonIsReachable() {
        var resets = 0
        showSmall {
            ConfigScreen(
                state = ConfigUiState(config = AppConfig(), isLoading = false),
                onEvent = { if (it is cz.talkative.app.ui.config.ConfigEvent.ResetRequested) resets++ },
                onBack = {},
            )
        }

        composeRule.onNodeWithText(string(R.string.config_reset)).performScrollTo().performClick()
        assertEquals(1, resets)
    }

    @Test
    fun exchange_importSectionIsReachable() {
        showSmall {
            ExchangeScreen(state = ExchangeUiState(), onEvent = {}, onBack = {}, onChooseExportFile = {}, onChooseArchive = {})
        }

        composeRule.onNodeWithText(string(R.string.exchange_choose_archive)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun about_privacyTextIsReachable() {
        showSmall { AboutScreen(versionName = "1.0", onBack = {}, onOpenLink = {}) }

        composeRule.onNodeWithText(string(R.string.about_privacy)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun arasaac_footerLinkIsReachable() {
        var opened = 0
        showSmall { ArasaacScreen(state = ArasaacUiState(), onEvent = {}, onOpenAbout = { opened++ }, onBack = {}) }

        scrollList().performScrollToNode(hasText(string(R.string.arasaac_about_link)))
        composeRule.onNodeWithText(string(R.string.arasaac_about_link)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun draw_closeButtonIsReachable() {
        var closed = 0
        showSmall {
            DrawScreen(state = DrawUiState(), pixels = IntArray(512 * 512), onEvent = {}, onClose = { closed++ })
        }

        composeRule.onNodeWithText(string(R.string.draw_close)).performScrollTo().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun scrollableColumn_lastDialogButtonIsReachable() {
        var clicks = 0
        showSmall {
            ScrollableColumn {
                repeat(10) { Text("Řádek $it") }
                Button(onClick = { clicks++ }) { Text("Poslední") }
            }
        }

        composeRule.onNodeWithText("Poslední").performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun scrollableColumn_doesNotScrollWhenContentFits() {
        val state = ScrollState(0)
        composeRule.setContent {
            TalkativeTheme {
                Box(Modifier.size(300.dp)) { ScrollableColumn(state = state) { Text("Krátký obsah") } }
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, state.maxValue)
    }

    @Test
    fun scrollableColumn_scrollsWhenContentIsTaller() {
        val state = ScrollState(0)
        composeRule.setContent {
            TalkativeTheme {
                Box(Modifier.size(100.dp)) {
                    ScrollableColumn(state = state) { Box(Modifier.size(400.dp)) }
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(state.maxValue > 0)
    }
}
