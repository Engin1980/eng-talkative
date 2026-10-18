package cz.talkative.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.ui.admin.AdminDialog
import cz.talkative.app.ui.admin.AdminEvent
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.admin.ImageProblem
import cz.talkative.app.ui.admin.ItemKind
import cz.talkative.app.ui.common.LocalImageResolver
import cz.talkative.app.ui.common.WORD_IMAGE_TAG
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import java.io.File
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import cz.talkative.app.testing.NoSoftKeyboardRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy obrazovky: co zobrazí pro daný stav a jaké události vyvolá. */
@RunWith(AndroidJUnit4::class)
class AdminScreenTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

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

    // ---- obrázky (krok 4.2)

    private fun wordDialog(
        image: String? = null,
        processing: Boolean = false,
        problem: ImageProblem? = null,
        kind: ItemKind = ItemKind.WORD,
    ) = AdminDialog.Edit(
        kind = kind,
        itemId = null,
        title = "",
        imageFile = image,
        isProcessingImage = processing,
        imageProblem = problem,
    )

    @Test
    fun wordDialog_offersGalleryAndCamera_withoutRemoveWhenNoImage() {
        show(state(dialog = wordDialog()))

        composeRule.onNodeWithText("Vybrat z galerie").performScrollTo().assertHeightIsAtLeast(64.dp).assertIsEnabled()
        composeRule.onNodeWithText("Vyfotit").performScrollTo().assertHeightIsAtLeast(64.dp).assertIsEnabled()
        // Dialog se při otevřené klávesnici posouvá, proto se na prvky v dolní části nejdřív doscrolluje.
        composeRule.onNodeWithText("Slovíčko zatím nemá obrázek.").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTextCount("Odebrat obrázek"))
        composeRule.onNodeWithTag(WORD_IMAGE_TAG).assertDoesNotExist()
    }

    @Test
    fun wordDialog_withImage_showsPreviewAndRemoveButton() {
        val file = File(composeRule.activity.cacheDir, "preview-test.png")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        composeRule.setContent {
            CompositionLocalProvider(LocalImageResolver provides { name -> file.takeIf { name == "preview.png" } }) {
                TalkativeTheme {
                    AdminScreen(
                        state = state(dialog = wordDialog(image = "preview.png")),
                        onEvent = { events += it },
                        onBackToClient = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(WORD_IMAGE_TAG).assertExists()
        composeRule.onNodeWithText("Odebrat obrázek").performScrollTo().assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<AdminEvent>(AdminEvent.RemoveImage), events)
        file.delete()
    }

    @Test
    fun missingImageFile_showsNoPreviewInsteadOfCrashing() {
        show(state(dialog = wordDialog(image = "neexistuje.png")))

        composeRule.onNodeWithTag(WORD_IMAGE_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Slovíčko zatím nemá obrázek.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun processingImage_showsProgress_andDisablesButtons() {
        show(state(dialog = wordDialog(processing = true)))

        composeRule.onNodeWithText("Zpracovávám obrázek…").assertIsDisplayed()
        composeRule.onNodeWithText("Vybrat z galerie").assertIsNotEnabled()
        composeRule.onNodeWithText("Vyfotit").assertIsNotEnabled()
        composeRule.onNodeWithText("Uložit").assertIsNotEnabled()
    }

    @Test
    fun imageProblems_showUnderstandableMessages() {
        val expected = mapOf(
            ImageProblem.UNREADABLE to "Obrázek se nepodařilo načíst.",
            ImageProblem.STORAGE_FULL to "Na zařízení není dost místa",
            ImageProblem.NO_GALLERY to "chybí aplikace pro výběr obrázků",
            ImageProblem.NO_CAMERA to "Fotoaparát není dostupný.",
        )
        val problem = androidx.compose.runtime.mutableStateOf(ImageProblem.UNREADABLE)
        composeRule.setContent {
            TalkativeTheme {
                AdminScreen(
                    state = state(dialog = wordDialog(problem = problem.value)),
                    onEvent = {},
                    onBackToClient = {},
                )
            }
        }

        for ((kind, text) in expected) {
            problem.value = kind
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text, substring = true).assertExists() // nad klávesnicí může být vidět jen část dialogu
        }
    }

    @Test
    fun folderDialog_hasNoImageSection() {
        show(state(dialog = wordDialog(kind = ItemKind.FOLDER)))

        assertEquals(0, composeRule.onAllNodesWithTextCount("Vybrat z galerie"))
        assertEquals(0, composeRule.onAllNodesWithTextCount("Vyfotit"))
    }

    @Test
    fun wordRowInList_showsThumbnailOnlyWhenFileExists() {
        val file = File(composeRule.activity.cacheDir, "thumb-test.png")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        composeRule.setContent {
            CompositionLocalProvider(LocalImageResolver provides { name -> file.takeIf { name == "ok.png" } }) {
                TalkativeTheme {
                    AdminScreen(
                        state = state(
                            Word("w1", "default", "s obrázkem", null, 0, 0, imageFile = "ok.png"),
                            Word("w2", "default", "bez obrázku", null, 1, 0),
                            Word("w3", "default", "rozbitý odkaz", null, 2, 0, imageFile = "chybi.png"),
                        ),
                        onEvent = {},
                        onBackToClient = {},
                    )
                }
            }
        }

        assertEquals(1, composeRule.onAllNodesWithTag(WORD_IMAGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("rozbitý odkaz").assertIsDisplayed() // chybějící soubor nezpůsobí pád
        file.delete()
    }

    // ---- jiný text pro čtení (krok 5.1)

    @Test
    fun wordDialog_hasAlternateTextField_showingStoredValue() {
        show(state(dialog = wordDialog().copy(alternateText = "ká dé")))

        composeRule.onNodeWithText("Jiný text pro čtení").assertIsDisplayed()
        composeRule.onNodeWithText("ká dé").assertIsDisplayed()
        composeRule.onNodeWithText("Nepovinné. Když zůstane prázdné, čte se titulek.").assertIsDisplayed()
    }

    @Test
    fun typingAlternateText_sendsEvent() {
        show(state(dialog = wordDialog()))

        composeRule.onNodeWithText("Jiný text pro čtení").performTextInput("ká dé")

        assertEquals(listOf<AdminEvent>(AdminEvent.AlternateTextChanged("ká dé")), events)
    }

    @Test
    fun folderDialog_hasNoAlternateTextField() {
        show(state(dialog = wordDialog(kind = ItemKind.FOLDER)))

        assertEquals(0, composeRule.onAllNodesWithTextCount("Jiný text pro čtení"))
    }
}
