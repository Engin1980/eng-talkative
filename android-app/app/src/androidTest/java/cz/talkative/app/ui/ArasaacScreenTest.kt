package cz.talkative.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.ui.arasaac.ARASAAC_DOWNLOAD_TAG
import cz.talkative.app.ui.arasaac.ARASAAC_SEARCH_FIELD_TAG
import cz.talkative.app.ui.arasaac.ARASAAC_STATUS_TAG
import cz.talkative.app.ui.arasaac.ArasaacEvent
import cz.talkative.app.ui.arasaac.ArasaacScreen
import cz.talkative.app.ui.arasaac.ArasaacUiState
import cz.talkative.app.ui.arasaac.SearchStatus
import cz.talkative.app.ui.arasaac.pictogramTag
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy obrazovky výběru piktogramu ARASAAC. Náhledy se v testech nenačítají (adresy jsou neplatné). */
@RunWith(AndroidJUnit4::class)
class ArasaacScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ArasaacEvent>()
    private var backs = 0
    private var aboutOpened = 0

    private val house = Pictogram(1, "dům", "http://invalid.test/1_300.png")
    private val home = Pictogram(2, "domov", "http://invalid.test/2_300.png")

    private fun show(state: ArasaacUiState) {
        composeRule.setContent {
            TalkativeTheme {
                ArasaacScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpenAbout = { aboutOpened++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun startState_showsPrefilledQueryHintAndCredit() {
        show(ArasaacUiState(query = "dům"))

        composeRule.onNodeWithTag(ARASAAC_SEARCH_FIELD_TAG).assertTextEquals("Hledané slovo", "dům")
        composeRule.onNodeWithText("Napište slovo a klepněte na Hledat. Klepnutím na nabídnutý piktogram ho vyberete.").assertIsDisplayed()
        composeRule.onNodeWithText("Autor piktogramů:", substring = true).assertIsDisplayed()
    }

    @Test
    fun searchButton_andTyping_sendEvents() {
        show(ArasaacUiState(query = ""))

        composeRule.onNodeWithTag(ARASAAC_SEARCH_FIELD_TAG).performTextInput("kočka")
        composeRule.onNodeWithText("Hledat").assertHeightIsAtLeast(64.dp).performClick()

        // Stav je v testu pevný, proto pole po zadání vrátí původní text a pošle ještě jednu změnu.
        assertEquals(ArasaacEvent.QueryChanged("kočka"), events.first())
        assertEquals(ArasaacEvent.Search, events.last())
    }

    @Test
    fun searching_showsProgress() {
        show(ArasaacUiState(query = "dům", search = SearchStatus.Searching))

        composeRule.onNodeWithText("Hledám…").assertIsDisplayed()
    }

    @Test
    fun results_areShownAsTilesWithTitles_andTapSelects() {
        show(ArasaacUiState(query = "dům", search = SearchStatus.Results(listOf(house, home))))

        composeRule.onNodeWithTag(pictogramTag(1)).assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag(pictogramTag(1)).assertTextContains("dům") // titulek je ve sloučeném uzlu dlaždice
        composeRule.onNodeWithTag(pictogramTag(2)).assertTextContains("domov")

        composeRule.onNodeWithTag(pictogramTag(2)).performClick()

        assertEquals(listOf<ArasaacEvent>(ArasaacEvent.Select(home)), events)
    }

    @Test
    fun nothingFound_suggestsAnotherWord() {
        show(ArasaacUiState(query = "xyz", search = SearchStatus.Nothing))

        composeRule.onNodeWithText("Nic nenalezeno. Zkuste jiné slovo, například základní tvar nebo bližší pojem.").assertIsDisplayed()
    }

    private fun assertFailure(error: PictogramError, text: String) {
        show(ArasaacUiState(query = "dům", search = SearchStatus.Failed(error)))
        composeRule.onNodeWithText(text).assertIsDisplayed()
        composeRule.onNodeWithText("Zkusit znovu").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf<ArasaacEvent>(ArasaacEvent.Retry), events)
    }

    @Test
    fun noNetwork_explainsHowToFixIt_andOffersRetry() =
        assertFailure(PictogramError.NO_NETWORK, "Chybí připojení k internetu. Zapněte Wi-Fi nebo mobilní data a zkuste to znovu.")

    @Test
    fun timeout_isExplained() =
        assertFailure(PictogramError.TIMEOUT, "Server ARASAAC neodpověděl včas. Zkuste to znovu.")

    @Test
    fun serverError_isExplained() =
        assertFailure(PictogramError.SERVER_ERROR, "Server ARASAAC je nedostupný nebo odpověděl chybou. Zkuste to později.")

    @Test
    fun badResponse_isExplained() =
        assertFailure(PictogramError.BAD_RESPONSE, "Odpověď serveru ARASAAC se nepodařilo přečíst. Zkuste to znovu.")

    @Test
    fun downloading_showsProgress_andTilesCannotBeTappedAgain() {
        show(ArasaacUiState(query = "dům", search = SearchStatus.Results(listOf(house)), downloadingId = 1))

        composeRule.onNodeWithTag(ARASAAC_DOWNLOAD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Stahuji piktogram…").assertIsDisplayed()
        composeRule.onNodeWithTag(pictogramTag(1)).assertIsNotEnabled()
    }

    @Test
    fun downloadError_isShownAboveTheResults_andTheTileStaysUsable() {
        show(
            ArasaacUiState(
                query = "dům",
                search = SearchStatus.Results(listOf(house)),
                downloadError = PictogramError.NO_NETWORK,
            ),
        )

        composeRule.onNodeWithTag(ARASAAC_DOWNLOAD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(pictogramTag(1)).performClick()

        assertEquals(listOf<ArasaacEvent>(ArasaacEvent.Select(house)), events)
    }

    @Test
    fun noStatusRowsWhenIdle() {
        show(ArasaacUiState(query = "dům", search = SearchStatus.Results(listOf(house))))

        composeRule.onAllNodesWithTag(ARASAAC_DOWNLOAD_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(ARASAAC_STATUS_TAG).assertCountEquals(0)
    }

    @Test
    fun aboutLinkAndBackButton_work() {
        show(ArasaacUiState())

        composeRule.onNodeWithText("O aplikaci a licenci piktogramů").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Zpět").performClick()

        assertEquals(1, aboutOpened)
        assertEquals(1, backs)
    }
}
