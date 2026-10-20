package cz.talkative.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.ConfigLimits
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.ui.config.ConfigEvent
import cz.talkative.app.ui.config.ConfigScreen
import cz.talkative.app.ui.config.ConfigUiState
import cz.talkative.app.ui.config.configValueTag
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy obrazovky konfigurace: co ukáže pro danou konfiguraci a jaké události vyvolá. */
@RunWith(AndroidJUnit4::class)
class ConfigScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ConfigEvent>()
    private var backs = 0

    private fun show(config: AppConfig = AppConfig(), confirmReset: Boolean = false) {
        composeRule.setContent {
            TalkativeTheme {
                ConfigScreen(
                    state = ConfigUiState(config = config, isLoading = false, confirmReset = confirmReset),
                    onEvent = { events += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun defaultValues_areShownAsInAssignment() {
        show()

        composeRule.onNodeWithText("Klepnutím").assertIsSelected()
        composeRule.onNodeWithText("Přetažením do věty").performScrollTo()
        composeRule.onNodeWithText("Povolit změnu pořadí slov ve větě").assertIsDisplayed()
        composeRule.onNodeWithTag(configValueTag("portrait-columns")).performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag(configValueTag("portrait-rows")).performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag(configValueTag("landscape-columns")).performScrollTo().assertTextEquals("4")
        composeRule.onNodeWithTag(configValueTag("landscape-rows")).performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithText("Čeština").performScrollTo().assertIsSelected()
    }

    @Test
    fun switches_reflectConfigAndSendEvents() {
        show(AppConfig(allowReorderInSentence = true, speakWordOnTap = false))

        composeRule.onNodeWithText("Povolit změnu pořadí slov ve větě").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithText("Přečíst slovo po klepnutí na něj").performScrollTo().performClick()
        composeRule.onNodeWithText("Skrýt titulky slov s obrázkem ve větě").performScrollTo().performClick()
        composeRule.onNodeWithText("Po přehrání věty ji smazat").performScrollTo().performClick()

        assertEquals(
            listOf<ConfigEvent>(
                ConfigEvent.AllowReorderChanged(false),
                ConfigEvent.SpeakWordOnTapChanged(true),
                ConfigEvent.HideCaptionsChanged(true),
                ConfigEvent.AutoClearChanged(true),
            ),
            events,
        )
    }

    @Test
    fun addMode_choicesSendEvents() {
        show(AppConfig(addWordMode = AddWordMode.DRAG))

        composeRule.onNodeWithText("Přetažením do věty").assertIsSelected()
        composeRule.onNodeWithText("Klepnutím").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<ConfigEvent>(ConfigEvent.AddWordModeChanged(AddWordMode.TAP)), events)
    }

    @Test
    fun gridSteppers_changeOneValueAtATime() {
        show(AppConfig(gridPortrait = GridSize(3, 3), gridLandscape = GridSize(4, 3)))

        // pořadí tlačítek na obrazovce: [0] podržení pro přidání (jen v režimu klepnutí), [1] podržení pro odebrání,
        // [2] sloupce a [3] řádky na výšku, [4] sloupce a [5] řádky na šířku, [6] podržení pro vstup do administrace
        composeRule.onAllNodesWithText("+")[2].performScrollTo().performClick()
        composeRule.onAllNodesWithText("−")[5].performScrollTo().performClick()

        assertEquals(
            listOf<ConfigEvent>(
                ConfigEvent.GridChanged(landscape = false, grid = GridSize(4, 3)),
                ConfigEvent.GridChanged(landscape = true, grid = GridSize(4, 2)),
            ),
            events,
        )
    }

    @Test
    fun steppers_areDisabledAtLimits() {
        show(
            AppConfig(
                gridPortrait = GridSize(ConfigLimits.MAX_GRID_SIDE, ConfigLimits.MIN_GRID_SIDE),
                removeHoldMillis = ConfigLimits.MIN_REMOVE_HOLD_MILLIS,
                adminHoldMillis = ConfigLimits.MAX_ADMIN_HOLD_MILLIS,
            ),
        )

        // pořadí steppérů: viz gridSteppers_changeOneValueAtATime
        composeRule.onAllNodesWithText("−")[1].performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithText("+")[1].performScrollTo().assertIsEnabled()
        composeRule.onAllNodesWithText("+")[2].performScrollTo().assertIsNotEnabled() // sloupce na výšku = max
        composeRule.onAllNodesWithText("−")[3].performScrollTo().assertIsNotEnabled() // řádky na výšku = min
        composeRule.onAllNodesWithText("+")[6].performScrollTo().assertIsNotEnabled() // vstup do administrace = max
    }

    @Test
    fun holdIntervals_areShownInSeconds_andStepByHalfOrWholeSeconds() {
        show(AppConfig(removeHoldMillis = 1_000, adminHoldMillis = 10_000))

        composeRule.onAllNodesWithText("+")[1].performScrollTo().performClick()
        composeRule.onAllNodesWithText("−")[6].performScrollTo().performClick()

        assertEquals(
            listOf<ConfigEvent>(ConfigEvent.RemoveHoldChanged(1_500), ConfigEvent.AdminHoldChanged(9_000)),
            events,
        )
    }

    @Test
    fun addHold_isShownInTapMode_startsAtImmediately_andStepsByHalfSecond() {
        show(AppConfig(addHoldMillis = 0L))

        composeRule.onNodeWithTag(configValueTag("add-hold")).performScrollTo().assertTextEquals("Hned")
        composeRule.onAllNodesWithText("−")[0].performScrollTo().assertIsNotEnabled() // 0 je dolní mez
        composeRule.onAllNodesWithText("+")[0].performScrollTo().performClick()

        assertEquals(listOf<ConfigEvent>(ConfigEvent.AddHoldChanged(500)), events)
    }

    @Test
    fun addHold_showsSeconds_andIsDisabledAtMaximum() {
        show(AppConfig(addHoldMillis = ConfigLimits.MAX_ADD_HOLD_MILLIS))

        composeRule.onNodeWithTag(configValueTag("add-hold")).performScrollTo().assertTextEquals(String.format(java.util.Locale.getDefault(), "%.1f s", 5.0))
        composeRule.onAllNodesWithText("+")[0].performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun addHold_isHiddenInDragMode() {
        show(AppConfig(addWordMode = AddWordMode.DRAG))

        composeRule.onAllNodesWithTag(configValueTag("add-hold")).assertCountEquals(0)
    }

    @Test
    fun collation_offersLanguages_andSendsChoice() {
        show()

        composeRule.onNodeWithText("Angličtina").performScrollTo().performClick()

        assertEquals(listOf<ConfigEvent>(ConfigEvent.CollationChanged("en")), events)
    }

    @Test
    fun unknownStoredCollation_isStillShownAndSelected() {
        show(AppConfig(collationLanguageTag = "fr"))
        composeRule.onNodeWithText("fr").performScrollTo().assertIsSelected()
    }

    @Test
    fun resetButton_asks_andDialogSendsConfirmOrDismiss() {
        show()
        composeRule.onNodeWithText("Obnovit výchozí nastavení").performScrollTo().assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf<ConfigEvent>(ConfigEvent.ResetRequested), events)
    }

    @Test
    fun resetDialog_confirmAndCancel() {
        show(confirmReset = true)

        composeRule.onNodeWithText("Obnovit výchozí nastavení?").assertIsDisplayed()
        composeRule.onNodeWithText("Obnovit").performClick()
        composeRule.onNodeWithText("Zrušit").performClick()

        assertEquals(listOf<ConfigEvent>(ConfigEvent.ResetConfirmed, ConfigEvent.ResetDismissed), events)
    }

    @Test
    fun backButton_callsCallback() {
        show()
        composeRule.onNodeWithText("Zpět do administrace").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(1, backs)
    }

    @Test
    fun screenStatesThatConfigurationIsLocal() {
        show()
        composeRule.onNodeWithText("Nastavení platí jen pro toto zařízení a nesynchronizuje se.").assertIsDisplayed()
    }
}
