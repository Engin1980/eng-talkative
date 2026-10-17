package cz.talkative.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.ui.admin.AdminDialog
import cz.talkative.app.ui.admin.AdminEvent
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.admin.ItemKind
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy obrazovky: co zobrazí pro daný stav a jaké události vyvolá. */
@RunWith(AndroidJUnit4::class)
class AdminScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<AdminEvent>()
    private var backToClient = 0

    private fun folder(id: String, title: String, order: Int = 0, parent: String? = null) =
        Folder(id, "default", title, parent, order, 0)

    private fun word(id: String, title: String, order: Int = 0) = Word(id, "default", title, null, order, 0)

    private fun show(state: AdminUiState) {
        composeRule.setContent {
            TalkativeTheme {
                AdminScreen(state = state, onEvent = { events += it }, onBackToClient = { backToClient++ })
            }
        }
    }

    private fun state(vararg items: DictionaryItem, path: List<Folder> = emptyList(), dialog: AdminDialog? = null) =
        AdminUiState(path = path, items = items.toList(), isLoading = false, dialog = dialog)

    @Test
    fun backButton_callsCallback_andHasMinimumTouchTarget() {
        show(state())

        composeRule.onNodeWithText("Zpět do klienta")
            .assertHeightIsAtLeast(64.dp)
            .performClick()

        assertEquals(1, backToClient)
    }

    @Test
    fun emptyFolder_showsHint() {
        show(state())

        composeRule.onNodeWithText("Tato složka je prázdná").assertIsDisplayed()
        composeRule.onNodeWithText("Přidejte složku nebo slovíčko tlačítky nahoře.").assertIsDisplayed()
    }

    @Test
    fun listsFoldersAndWords_andOpensFolderOnClick() {
        val food = folder("f1", "Jídlo")
        show(state(food, word("w1", "Chci", order = 1)))

        composeRule.onNodeWithText("Chci").assertIsDisplayed()
        composeRule.onNodeWithText("Jídlo").performClick()

        assertEquals(listOf<AdminEvent>(AdminEvent.GoToFolder("f1")), events)
    }

    @Test
    fun breadcrumbs_showPath_andClickingAncestorGoesThere() {
        val a = folder("a", "A")
        val b = folder("b", "B", parent = "a")
        show(state(path = listOf(a, b)))

        composeRule.onNodeWithText("Domů").assertIsDisplayed()
        composeRule.onNodeWithText("B").assertIsDisplayed()
        composeRule.onNodeWithText("A").performClick()
        composeRule.onNodeWithText("Domů").performClick()

        assertEquals(listOf<AdminEvent>(AdminEvent.GoToFolder("a"), AdminEvent.GoToFolder(null)), events)
    }

    @Test
    fun upButton_isOnlyVisibleOutsideRoot() {
        show(state(path = listOf(folder("a", "A"))))

        composeRule.onNodeWithText("Nahoru").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<AdminEvent>(AdminEvent.NavigateUp), events)
    }

    @Test
    fun rootHasNoUpButton() {
        show(state())

        assertTrue(composeRule.onAllNodesWithTextCount("Nahoru") == 0)
    }

    @Test
    fun actionButtons_haveMinimumTouchTarget_andSendEvents() {
        val item = word("w1", "Chci")
        show(state(item))

        composeRule.onNodeWithText("Nová složka").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Nové slovíčko").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithContentDescription("Upravit Chci")
            .assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithContentDescription("Smazat Chci")
            .assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(64.dp).performClick()

        assertEquals(
            listOf(AdminEvent.AddFolder, AdminEvent.AddWord, AdminEvent.EditItem(item), AdminEvent.DeleteItem(item)),
            events,
        )
    }

    @Test
    fun editDialog_showsEmptyTitleError() {
        show(state(dialog = AdminDialog.Edit(ItemKind.WORD, null, "", showEmptyError = true)))

        composeRule.onNodeWithText("Titulek nesmí být prázdný.").assertIsDisplayed()
        composeRule.onNodeWithText("Uložit").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(2, composeRule.onAllNodesWithTextCount("Nové slovíčko")) // tlačítko + nadpis dialogu
        assertEquals(listOf<AdminEvent>(AdminEvent.ConfirmEdit), events)
    }

    @Test
    fun deleteDialog_forFolderShowsItemCount() {
        val dialog = AdminDialog.ConfirmDelete(folder("f1", "Jídlo"), contentCount = 3)
        show(state(dialog = dialog))

        composeRule.onNodeWithText("Smazat složku?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Složka „Jídlo“ není prázdná. Počet položek uvnitř včetně podsložek: 3. Smaže se vše a nelze to vrátit zpět.",
        ).assertIsDisplayed()
    }

    @Test
    fun deleteDialog_forEmptyFolderAndWordHasNoCount() {
        show(state(dialog = AdminDialog.ConfirmDelete(folder("f1", "A"), contentCount = 0)))
        composeRule.onNodeWithText("Složka „A“ bude smazána. Nelze to vrátit zpět.").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
