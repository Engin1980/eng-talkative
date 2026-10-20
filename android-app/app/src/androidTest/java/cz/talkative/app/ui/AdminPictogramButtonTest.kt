package cz.talkative.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.ui.admin.AdminDialog
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.admin.ItemKind
import cz.talkative.app.testing.NoSoftKeyboardRule
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tlačítko „Najít piktogram (ARASAAC)“ v editaci slovíčka. */
@RunWith(AndroidJUnit4::class)
class AdminPictogramButtonTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val requested = mutableListOf<String>()
    private var drawRequests = 0

    private fun show(dialog: AdminDialog.Edit) {
        composeRule.setContent {
            TalkativeTheme {
                AdminScreen(
                    state = AdminUiState(isLoading = false, dialog = dialog),
                    onEvent = {},
                    onBackToClient = {},
                    onFindPictogram = { requested += it },
                    onDraw = { drawRequests++ },
                )
            }
        }
    }

    private fun edit(kind: ItemKind = ItemKind.WORD, title: String = "", processing: Boolean = false) =
        AdminDialog.Edit(kind = kind, itemId = null, title = title, isProcessingImage = processing)

    @Test
    fun wordDialog_offersTheButton_andPassesTheTypedTitle() {
        show(edit(title = "dům"))

        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().assertHeightIsAtLeast(64.dp).assertIsEnabled().performClick()

        assertEquals(listOf("dům"), requested)
    }

    @Test
    fun wordDialog_withoutTitle_passesEmptyText() {
        show(edit(title = ""))

        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().performClick()

        assertEquals(listOf(""), requested)
    }

    @Test
    fun folderDialog_hasNoPictogramButton() {
        show(edit(kind = ItemKind.FOLDER))

        assertEquals(0, composeRule.onAllNodesWithText("Najít piktogram (ARASAAC)").fetchSemanticsNodes().size)
    }

    @Test
    fun buttonIsDisabled_whileAnImageIsBeingProcessed() {
        show(edit(processing = true))

        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun wordDialog_offersDrawing_asA64dpButton() {
        show(edit())

        composeRule.onNodeWithText("Nakreslit").performScrollTo().assertHeightIsAtLeast(64.dp).assertIsEnabled().performClick()

        assertEquals(1, drawRequests)
    }

    @Test
    fun folderDialog_hasNoDrawButton() {
        show(edit(kind = ItemKind.FOLDER))

        assertEquals(0, composeRule.onAllNodesWithText("Nakreslit").fetchSemanticsNodes().size)
    }

    @Test
    fun drawButton_isDisabledWhileAnImageIsBeingProcessed() {
        show(edit(processing = true))

        composeRule.onNodeWithText("Nakreslit").performScrollTo().assertIsNotEnabled()
    }
}
