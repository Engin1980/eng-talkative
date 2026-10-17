package cz.talkative.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.ui.client.ClientEvent
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ClientEvent>()

    private fun words(count: Int): List<DictionaryItem> =
        (1..count).map { Word("w$it", "default", "slovo$it", null, it, 0) }

    private fun visibleTiles(count: Int) = (1..count).count {
        composeRule.onAllNodesWithText("slovo$it").fetchSemanticsNodes().isNotEmpty()
    }

    private fun show(state: ClientUiState, isLandscape: Boolean = false) {
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(state, onEvent = { events += it }, onOpenAdmin = {}, isLandscape = isLandscape)
            }
        }
    }

    private fun loaded(items: List<DictionaryItem>, page: Int = 0) =
        ClientUiState(items = items, pageIndex = page, isLoading = false)

    @Test
    fun tenItemsInThreeByThree_showNineThenOne_withPageControls() {
        val state = mutableStateOf(loaded(words(10)))
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(state.value, onEvent = { events += it }, onOpenAdmin = {}, isLandscape = false)
            }
        }

        assertEquals(9, visibleTiles(10))
        composeRule.onNodeWithText("Stránka 1 z 2").assertIsDisplayed()

        composeRule.onNodeWithText("Další").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf<ClientEvent>(ClientEvent.PageChanged(1)), events)

        state.value = loaded(words(10), page = 1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("slovo10").assertIsDisplayed()
        assertEquals(1, visibleTiles(10))
        composeRule.onNodeWithText("Stránka 2 z 2").assertIsDisplayed()
    }

    @Test
    fun landscapeGrid_isBigger_soTenItemsFitOnOnePage() {
        show(loaded(words(10)), isLandscape = true)

        assertEquals(10, visibleTiles(10))
        assertEquals(0, composeRule.onAllNodesWithText("Další").fetchSemanticsNodes().size) // jedna stránka
    }

    @Test
    fun rotation_recomputesGrid_withoutChangingTheItems() {
        val landscape = mutableStateOf(false)
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(loaded(words(10)), onEvent = {}, onOpenAdmin = {}, isLandscape = landscape.value)
            }
        }
        assertEquals(9, visibleTiles(10))

        landscape.value = true
        composeRule.waitForIdle()
        assertEquals(10, visibleTiles(10))

        landscape.value = false
        composeRule.waitForIdle()
        assertEquals(9, visibleTiles(10))
    }

    @Test
    fun exactMultipleOfPage_hasNoExtraPage() {
        show(loaded(words(9)))

        assertEquals(9, visibleTiles(9))
        assertEquals(0, composeRule.onAllNodesWithText("Další").fetchSemanticsNodes().size)
    }

    @Test
    fun pageBeyondTheEnd_isClamped() {
        show(loaded(words(10), page = 7))

        composeRule.onNodeWithText("Stránka 2 z 2").assertIsDisplayed()
        composeRule.onNodeWithText("slovo10").assertIsDisplayed()
    }

    @Test
    fun emptyFolder_showsMessage() {
        show(loaded(emptyList()))

        composeRule.onNodeWithText("Tato složka je prázdná.").assertIsDisplayed()
    }

    @Test
    fun clickingFolder_opensIt_clickingWordDoesNotOpenAnything() {
        val folder = Folder("f1", "default", "Jídlo", null, 0, 0)
        show(loaded(listOf(folder) + words(1)))

        composeRule.onNodeWithText("slovo1").performClick()
        assertEquals(emptyList<ClientEvent>(), events)

        composeRule.onNodeWithText("Jídlo").performClick()
        assertEquals(listOf<ClientEvent>(ClientEvent.OpenFolder("f1")), events)
    }

    @Test
    fun previousButtonIsDisabledOnFirstPage() {
        show(loaded(words(10)))

        composeRule.onNodeWithText("Předchozí").assertIsDisplayed()
        composeRule.onNodeWithText("Předchozí").performClick()
        assertEquals(emptyList<ClientEvent>(), events)
    }

    private fun folder(id: String, title: String) = Folder(id, "default", title, null, 0, 0)

    @Test
    fun inRoot_backAndHomeAreDisabled_andOnlyRootCrumbIsShown() {
        show(loaded(words(1)))

        composeRule.onNodeWithText("Úvod").assertIsDisplayed()
        composeRule.onNodeWithText("Zpět").performClick()
        composeRule.onNodeWithText("Domů").performClick()
        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun breadcrumbs_showWholePath_andNavigationButtonsSendEvents() {
        val path = listOf(folder("a", "Jídlo"), folder("b", "Ovoce"))
        show(ClientUiState(path = path, items = words(1), isLoading = false))

        composeRule.onNodeWithText("Úvod").assertIsDisplayed()
        composeRule.onNodeWithText("Jídlo").assertIsDisplayed()
        composeRule.onNodeWithText("Ovoce").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Domů").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Jídlo").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Úvod").performClick()

        assertEquals(
            listOf(
                ClientEvent.NavigateUp,
                ClientEvent.GoHome,
                ClientEvent.GoToFolder("a"),
                ClientEvent.GoToFolder(null),
            ),
            events,
        )
    }
}
