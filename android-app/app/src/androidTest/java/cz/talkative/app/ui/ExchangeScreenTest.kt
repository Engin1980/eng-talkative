package cz.talkative.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.exchange.ExportError
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.ui.exchange.EXPORT_SCOPE_TAG
import cz.talkative.app.ui.exchange.EXPORT_STATUS_TAG
import cz.talkative.app.ui.exchange.ExchangeEvent
import cz.talkative.app.ui.exchange.ExchangeScreen
import cz.talkative.app.ui.exchange.ExchangeUiState
import cz.talkative.app.ui.exchange.ExportStatus
import cz.talkative.app.ui.exchange.FolderPickerState
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy obrazovky exportu: co ukáže pro daný stav a jaké události vyvolá. */
@RunWith(AndroidJUnit4::class)
class ExchangeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ExchangeEvent>()
    private var backs = 0
    private var fileRequests = 0
    private var archiveRequests = 0

    private val food = Folder("f1", "default", "Jídlo", null, 0, 0)

    private fun show(state: ExchangeUiState = ExchangeUiState()) {
        composeRule.setContent {
            TalkativeTheme {
                ExchangeScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = { backs++ },
                    onChooseExportFile = { fileRequests++ },
                    onChooseArchive = { archiveRequests++ },
                )
            }
        }
    }

    @Test
    fun defaultScope_isWholeDictionary_withoutConfig() {
        show()

        composeRule.onNodeWithTag(EXPORT_SCOPE_TAG).assertTextEquals("Exportuje se celý slovník")
        composeRule.onNodeWithText("Celý slovník").assertIsNotEnabled() // už je vybraný
        composeRule.onNodeWithText("Přidat i nastavení tohoto zařízení").assertIsDisplayed()
        composeRule.onAllNodesWithTag(EXPORT_STATUS_TAG).assertCountEquals(0)
    }

    @Test
    fun selectedFolder_isNamedInTheScope() {
        show(ExchangeUiState(exportFolder = food))

        composeRule.onNodeWithTag(EXPORT_SCOPE_TAG).assertTextEquals("Exportuje se složka „Jídlo“ včetně podsložek")
        composeRule.onNodeWithText("Celý slovník").performClick()
        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ExportWholeDictionary), events)
    }

    @Test
    fun exportButton_asksForTheTargetFile_andButtonsAreBigEnough() {
        show()

        composeRule.onNodeWithText("Exportovat do souboru…").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Vybrat složku…").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Zpět do administrace").performClick()

        assertEquals(1, fileRequests)
        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ChooseExportFolder), events)
        assertEquals(1, backs)
    }

    @Test
    fun configSwitch_sendsTheNewValue() {
        show()

        composeRule.onNodeWithText("Přidat i nastavení tohoto zařízení").performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.IncludeConfigChanged(true)), events)
    }

    @Test
    fun configSwitch_reflectsTheState() {
        show(ExchangeUiState(includeConfig = true))

        composeRule.onNodeWithText("Přidat i nastavení tohoto zařízení").assertIsOn()
    }

    @Test
    fun runningExport_showsProgress_andDisablesTheControls() {
        show(ExchangeUiState(export = ExportStatus.Running(done = 2, total = 5)))

        composeRule.onNodeWithTag(EXPORT_STATUS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Ukládám obrázky a zvuky: 2 z 5").assertIsDisplayed()
        composeRule.onNodeWithText("Exportovat do souboru…").assertIsNotEnabled()
        composeRule.onNodeWithText("Vybrat složku…").assertIsNotEnabled()
        composeRule.onNodeWithText("Přidat i nastavení tohoto zařízení").assertIsNotEnabled()
    }

    @Test
    fun runningExportWithoutFiles_showsPlainMessage() {
        show(ExchangeUiState(export = ExportStatus.Running()))

        composeRule.onNodeWithText("Ukládám archiv…").assertIsDisplayed()
    }

    @Test
    fun finishedExport_showsCounts_andWarnsAboutMissingMedia() {
        show(ExchangeUiState(export = ExportStatus.Done(folders = 3, words = 12, missingMedia = 2)))

        composeRule.onNodeWithText("Hotovo. Uloženo složek: 3, slovíček: 12.").assertIsDisplayed()
        composeRule.onNodeWithText("U 2 obrázků nebo zvuků chyběl soubor v zařízení, v archivu nejsou.").assertIsDisplayed()
    }

    @Test
    fun finishedExport_withoutMissingMedia_hasNoWarning() {
        show(ExchangeUiState(export = ExportStatus.Done(folders = 0, words = 1, missingMedia = 0)))

        composeRule.onNodeWithText("Hotovo. Uloženo složek: 0, slovíček: 1.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(EXPORT_STATUS_TAG).assertCountEquals(1)
    }

    @Test
    fun storageFull_isShown() {
        show(ExchangeUiState(export = ExportStatus.Failed(ExportError.STORAGE_FULL)))

        composeRule.onNodeWithText("Na zařízení není dost místa. Soubor nebyl vytvořen.").assertIsDisplayed()
    }

    @Test
    fun writeFailure_isShown() {
        show(ExchangeUiState(export = ExportStatus.Failed(ExportError.WRITE_FAILED)))

        composeRule.onNodeWithText("Archiv se nepodařilo zapsat. Soubor nebyl vytvořen.").assertIsDisplayed()
    }

    @Test
    fun missingFolder_isShown() {
        show(ExchangeUiState(export = ExportStatus.Failed(ExportError.FOLDER_MISSING)))

        composeRule.onNodeWithText("Vybraná složka už neexistuje. Soubor nebyl vytvořen.").assertIsDisplayed()
    }

    @Test
    fun picker_atRoot_listsSubfolders_andCannotSelectTheRoot() {
        show(ExchangeUiState(picker = FolderPickerState(path = emptyList(), folders = listOf(food))))

        composeRule.onNodeWithText("Vyberte složku").assertIsDisplayed()
        composeRule.onNodeWithText("Vybrat tuto složku").assertIsNotEnabled()
        composeRule.onNodeWithText("Jídlo").performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.BrowsePicker("f1")), events)
    }

    @Test
    fun picker_insideFolder_confirmsIt_andCrumbsGoBack() {
        show(ExchangeUiState(picker = FolderPickerState(path = listOf(food), folders = emptyList())))

        composeRule.onNodeWithText("Tato složka nemá žádné podsložky.").assertIsDisplayed()
        composeRule.onNodeWithText("Vybrat tuto složku").performClick()
        composeRule.onNodeWithContentDescription("Domů").performClick()
        composeRule.onNodeWithText("Zrušit").performClick()

        assertEquals(
            listOf<ExchangeEvent>(ExchangeEvent.ConfirmPicker, ExchangeEvent.BrowsePicker(null), ExchangeEvent.DismissPicker),
            events,
        )
    }

    @Test
    fun exportSection_explainsTheLicenseFileAndTheNonCommercialRule() {
        show()

        composeRule.onNodeWithText(
            "Do archivu se přidá soubor s uvedením autora a licencí ARASAAC. Archiv, který obsahuje piktogramy ARASAAC, " +
                "lze šířit jen nekomerčně, pod stejnou licencí a se stejným uvedením.",
        ).assertIsDisplayed()
    }
}
