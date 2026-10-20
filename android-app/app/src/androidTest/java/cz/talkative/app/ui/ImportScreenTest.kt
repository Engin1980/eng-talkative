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
import cz.talkative.app.domain.exchange.ArchiveFolder
import cz.talkative.app.domain.exchange.ArchiveFolderRow
import cz.talkative.app.domain.exchange.ImportError
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.ui.exchange.ExchangeEvent
import cz.talkative.app.ui.exchange.ExchangeScreen
import cz.talkative.app.ui.exchange.ExchangeUiState
import cz.talkative.app.ui.exchange.FolderPickerState
import cz.talkative.app.ui.exchange.IMPORT_PLAN_TAG
import cz.talkative.app.ui.exchange.IMPORT_STATUS_TAG
import cz.talkative.app.ui.exchange.IMPORT_TARGET_TAG
import cz.talkative.app.ui.exchange.ImportArchiveInfo
import cz.talkative.app.ui.exchange.ImportStatus
import cz.talkative.app.ui.exchange.ImportUiState
import cz.talkative.app.ui.exchange.PickerPurpose
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Bezstavové testy části importu na obrazovce exportu a importu. */
@RunWith(AndroidJUnit4::class)
class ImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ExchangeEvent>()
    private var archiveRequests = 0

    private val rows = listOf(
        ArchiveFolderRow(ArchiveFolder("food", "Jídlo"), depth = 0),
        ArchiveFolderRow(ArchiveFolder("fruit", "Ovoce", parentId = "food"), depth = 1),
        ArchiveFolderRow(ArchiveFolder("drink", "Pití", sortOrder = 1), depth = 0),
    )

    private fun info(hasRootWords: Boolean = true, hasConfig: Boolean = true) =
        ImportArchiveInfo(rows, folderCount = 3, wordCount = 7, hasRootWords = hasRootWords, hasConfig = hasConfig)

    private fun import(
        archive: ImportArchiveInfo? = info(),
        selectAll: Boolean = true,
        selected: Set<String> = emptySet(),
        status: ImportStatus = ImportStatus.Idle,
        replaceAll: Boolean = false,
        target: Folder? = null,
        confirmReplace: Boolean = false,
        includeRootWords: Boolean = false,
        importConfig: Boolean = false,
    ) = ImportUiState(
        archive = archive,
        selectAll = selectAll,
        selectedFolderIds = selected,
        includeRootWords = includeRootWords,
        target = target,
        replaceAll = replaceAll,
        importConfig = importConfig,
        plannedFolders = 3,
        plannedWords = 7,
        confirmReplace = confirmReplace,
        status = status,
    )

    private fun show(state: ExchangeUiState) {
        composeRule.setContent {
            TalkativeTheme {
                ExchangeScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    onChooseExportFile = {},
                    onChooseArchive = { archiveRequests++ },
                )
            }
        }
    }

    private fun showImport(import: ImportUiState) = show(ExchangeUiState(import = import))

    @Test
    fun withoutArchive_onlyTheChooseButtonIsShown() {
        showImport(import(archive = null))

        composeRule.onNodeWithText("Vybrat archiv…").performScrollTo().assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(1, archiveRequests)
        composeRule.onAllNodesWithText("Importovat").assertCountEquals(0)
        composeRule.onAllNodesWithTag(IMPORT_PLAN_TAG).assertCountEquals(0)
    }

    @Test
    fun loadedArchive_showsContentsPlanAndDefaults() {
        showImport(import())

        composeRule.onNodeWithText("Archiv obsahuje složek: 3, slovíček: 7.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Celý archiv").assertIsSelected()
        composeRule.onNodeWithTag(IMPORT_TARGET_TAG).performScrollTo().assertTextEquals("Vloží se do kořene slovníku")
        composeRule.onNodeWithTag(IMPORT_PLAN_TAG).performScrollTo().assertTextEquals("Přidá se složek: 3, slovíček: 7.")
        composeRule.onNodeWithText("Importovat").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Načíst i nastavení zařízení z archivu").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun configOption_isOnlyOfferedWhenTheArchiveHasConfig() {
        showImport(import(archive = info(hasConfig = false)))

        composeRule.onAllNodesWithText("Načíst i nastavení zařízení z archivu").assertCountEquals(0)
    }

    @Test
    fun configSwitch_sendsTheNewValue_andReflectsTheState() {
        showImport(import(importConfig = true))

        composeRule.onNodeWithText("Načíst i nastavení zařízení z archivu").performScrollTo().assertIsOn().performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ImportConfigChanged(false)), events)
    }

    @Test
    fun foldersAreOnlyListed_whenChoosingSome() {
        showImport(import(selectAll = true))
        composeRule.onAllNodesWithText("Ovoce").assertCountEquals(0)
    }

    @Test
    fun choosingSomeFolders_listsThemAll_andTogglesOnClick() {
        showImport(import(selectAll = false, selected = setOf("drink")))

        composeRule.onNodeWithText("Jen vybrané složky").assertIsSelected()
        composeRule.onNodeWithText("Jídlo").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Ovoce").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Pití").performScrollTo().assertIsEnabled()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ToggleImportFolder("food")), events)
    }

    @Test
    fun subfolderOfASelectedFolder_isShownChecked_andCannotBeToggledSeparately() {
        showImport(import(selectAll = false, selected = setOf("food")))

        composeRule.onNodeWithText("Ovoce").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Jídlo").performScrollTo().assertIsEnabled()
    }

    @Test
    fun rootWordsOption_isOnlyOfferedWhenTheArchiveHasRootWords() {
        showImport(import(archive = info(hasRootWords = false), selectAll = false))
        composeRule.onAllNodesWithText("Slovíčka z kořene archivu").assertCountEquals(0)
    }

    @Test
    fun rootWordsOption_sendsTheChange() {
        showImport(import(selectAll = false))

        composeRule.onNodeWithText("Slovíčka z kořene archivu").performScrollTo().performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.IncludeRootWordsChanged(true)), events)
    }

    @Test
    fun noSelection_disablesTheImportButton() {
        showImport(import(selectAll = false, selected = emptySet()))

        composeRule.onNodeWithText("Importovat").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun radioChoices_sendEvents() {
        showImport(import())

        composeRule.onNodeWithText("Jen vybrané složky").performScrollTo().performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ImportSelectAllChanged(false)), events)
    }

    @Test
    fun target_isNamed_andCanBeChanged() {
        showImport(import(target = Folder("t", "default", "Cíl", null, 0, 0)))

        composeRule.onNodeWithTag(IMPORT_TARGET_TAG).performScrollTo().assertTextEquals("Vloží se do složky „Cíl“")
        composeRule.onNodeWithText("Změnit…").performScrollTo().assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ChooseImportTarget), events)
    }

    @Test
    fun replaceAll_hidesTheTarget_andAsksForConfirmation() {
        showImport(import(replaceAll = true))

        composeRule.onAllNodesWithTag(IMPORT_TARGET_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("Nahradit celý dosavadní slovník").performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Importovat").performScrollTo().performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.StartImport), events)
    }

    @Test
    fun replaceConfirmation_warnsAboutLosingTheOldDictionary_andOffersBothWays() {
        showImport(import(replaceAll = true, confirmReplace = true))

        composeRule.onNodeWithText("Nahradit celý slovník?").assertIsDisplayed()
        composeRule.onNodeWithText("Všechny dosavadní složky a slovíčka se smažou a nahradí se obsahem archivu. Nelze to vrátit zpět.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Nahradit").performClick()
        composeRule.onNodeWithText("Zrušit").performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ConfirmReplace, ExchangeEvent.DismissReplace), events)
    }

    @Test
    fun runningImport_disablesEverything() {
        showImport(import(status = ImportStatus.Running))

        composeRule.onNodeWithTag(IMPORT_STATUS_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Importuji…").assertIsDisplayed()
        composeRule.onNodeWithText("Importovat").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Vybrat archiv…").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Nahradit celý dosavadní slovník").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun readingArchive_showsProgress() {
        showImport(import(archive = null, status = ImportStatus.Reading))

        composeRule.onNodeWithText("Kontroluji archiv…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vybrat archiv…").assertIsNotEnabled()
    }

    @Test
    fun finishedImport_showsCounts_andConfigNote() {
        showImport(import(status = ImportStatus.Done(folders = 2, words = 5, configImported = true)))

        composeRule.onNodeWithText("Hotovo. Přidáno složek: 2, slovíček: 5.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nastavení zařízení bylo také načteno.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finishedImport_withoutConfig_hasNoConfigNote() {
        showImport(import(status = ImportStatus.Done(folders = 2, words = 5, configImported = false)))

        composeRule.onNodeWithText("Hotovo. Přidáno složek: 2, slovíček: 5.").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Nastavení zařízení bylo také načteno.").assertCountEquals(0)
    }

    private fun assertError(error: ImportError, text: String) {
        showImport(import(archive = null, status = ImportStatus.Failed(error)))
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notAnArchive_isExplained() = assertError(ImportError.NOT_AN_ARCHIVE, "Tohle není archiv vytvořený touto aplikací. Nic se nezměnilo.")

    @Test
    fun newerVersion_isExplained() = assertError(
        ImportError.NEWER_VERSION,
        "Archiv vytvořila novější verze aplikace. Aktualizujte aplikaci a zkuste to znovu. Nic se nezměnilo.",
    )

    @Test
    fun invalidArchive_isExplained() = assertError(ImportError.INVALID_ARCHIVE, "Archiv je poškozený nebo neúplný. Nic se nezměnilo.")

    @Test
    fun unreadableFile_isExplained() = assertError(ImportError.UNREADABLE, "Soubor se nepodařilo přečíst. Nic se nezměnilo.")

    @Test
    fun storageFull_isExplained() = assertError(ImportError.STORAGE_FULL, "Na zařízení není dost místa. Nic se nezměnilo.")

    @Test
    fun missingTarget_isExplained() = assertError(ImportError.TARGET_MISSING, "Cílová složka už neexistuje. Nic se nezměnilo.")

    @Test
    fun unexpectedFailure_isExplained() = assertError(ImportError.FAILED, "Import se nepodařil. Nic se nezměnilo.")

    @Test
    fun targetPicker_allowsChoosingTheRoot() {
        val folder = Folder("f", "default", "Jídlo", null, 0, 0)
        show(
            ExchangeUiState(
                import = import(),
                picker = FolderPickerState(path = emptyList(), folders = listOf(folder), purpose = PickerPurpose.IMPORT_TARGET),
            ),
        )

        composeRule.onNodeWithText("Vložit sem").assertIsEnabled().performClick()

        assertEquals(listOf<ExchangeEvent>(ExchangeEvent.ConfirmPicker), events)
    }
}
