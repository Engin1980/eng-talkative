package cz.talkative.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.DiscardAudioUseCase
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.SaveAudioUseCase
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.ui.admin.AdminEvent
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminViewModel
import cz.talkative.app.ui.common.WORD_IMAGE_TAG
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import cz.talkative.app.testing.NoSoftKeyboardRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Celá administrace nad skutečnou (in-memory) databází: UI → ViewModel → use case → Room.
 * Ověřuje scénáře kroku 1.4 z pohledu uživatele.
 */
@RunWith(AndroidJUnit4::class)
class AdminFlowTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private lateinit var database: TalkativeDatabase
    private val imageStore = FakeImageStore()
    private val audioStore = FakeAudioStore()
    private val audioPlayer = FakeAudioPlayer()
    private lateinit var viewModel: AdminViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        val repository = RoomDictionaryRepository(database.dictionaryDao(), { System.currentTimeMillis() }, imageStore, audioStore)
        viewModel = AdminViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            SaveFolderUseCase(repository),
            SaveWordUseCase(repository),
            CountFolderContentsUseCase(repository),
            DeleteFolderUseCase(repository),
            DeleteWordUseCase(repository),
            SaveImageUseCase(imageStore),
            DiscardImageUseCase(imageStore),
            SaveAudioUseCase(audioStore),
            DiscardAudioUseCase(audioStore),
            audioPlayer,
        )
        composeRule.setContent {
            TalkativeTheme {
                val state by viewModel.uiState.collectAsState()
                AdminScreen(state = state, onEvent = viewModel::onEvent, onBackToClient = {})
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForNoText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    private fun create(button: String, title: String) {
        composeRule.onNodeWithText(button).performClick()
        composeRule.onNodeWithText("Titulek").performTextInput(title)
        composeRule.onNodeWithText("Uložit").performClick()
        waitForText(title)
    }

    private fun addTwoLevelsDeep() {
        waitForText("Tato složka je prázdná")
        create("Nová složka", "Úroveň 1")
        composeRule.onNodeWithText("Úroveň 1").performClick()
        waitForText("Tato složka je prázdná")
        create("Nová složka", "Úroveň 2")
        composeRule.onNodeWithText("Úroveň 2").performClick()
        waitForText("Tato složka je prázdná")
        create("Nová složka", "Úroveň 3")
        create("Nové slovíčko", "Slovo")
    }

    @Test
    fun threeLevelsOfFoldersWithWords_canBeBrowsedBackAndForth() {
        addTwoLevelsDeep()

        composeRule.onNodeWithText("Nahoru").performClick()
        waitForNoText("Slovo")
        waitForText("Úroveň 2")
        composeRule.onNodeWithText("Domů").performClick()
        waitForText("Úroveň 1")
    }

    @Test
    fun emptyTitle_isRejectedWithMessage() {
        waitForText("Tato složka je prázdná")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Uložit").performClick()

        waitForText("Titulek nesmí být prázdný.")
        composeRule.onNodeWithText("Zrušit").performClick()
        waitForText("Tato složka je prázdná")
    }

    @Test
    fun twoWordsWithTheSameTitle_canExist() {
        waitForText("Tato složka je prázdná")
        create("Nové slovíčko", "Ahoj")
        create("Nové slovíčko", "Ahoj")

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Ahoj").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun editTitle_changesItInTheList() {
        waitForText("Tato složka je prázdná")
        create("Nové slovíčko", "Staré")

        composeRule.onNodeWithContentDescription("Upravit Staré").performClick()
        composeRule.onNodeWithText("Titulek").performTextClearance()
        composeRule.onNodeWithText("Titulek").performTextInput("Nové")
        composeRule.onNodeWithText("Uložit").performClick()

        waitForText("Nové")
        waitForNoText("Staré")
    }

    @Test
    fun deleteNonEmptyFolder_showsCount_cancelKeepsEverything_confirmRemovesSubtree() {
        waitForText("Tato složka je prázdná")
        create("Nová složka", "Kořenová")
        composeRule.onNodeWithText("Kořenová").performClick()
        waitForText("Tato složka je prázdná")
        create("Nové slovíčko", "w1")
        create("Nová složka", "Pod")
        composeRule.onNodeWithText("Pod").performClick()
        waitForText("Tato složka je prázdná")
        create("Nové slovíčko", "w2")
        composeRule.onNodeWithText("Domů").performClick()
        waitForText("Kořenová")

        composeRule.onNodeWithContentDescription("Smazat Kořenová").performClick()
        waitForText("Smazat složku?")
        composeRule.onNodeWithText("Počet položek uvnitř včetně podsložek: 3.", substring = true).assertExists()

        composeRule.onNodeWithText("Zrušit").performClick()
        waitForNoText("Smazat složku?")
        assertEquals(1, composeRule.onAllNodesWithText("Kořenová").fetchSemanticsNodes().size)

        composeRule.onNodeWithContentDescription("Smazat Kořenová").performClick()
        waitForText("Smazat složku?")
        composeRule.onNodeWithText("Smazat").performClick()

        waitForText("Tato složka je prázdná")
        assertEquals(0, composeRule.onAllNodesWithText("Kořenová").fetchSemanticsNodes().size)
    }

    @Test
    fun deleteWord_afterConfirmation() {
        waitForText("Tato složka je prázdná")
        create("Nové slovíčko", "Pryč")

        composeRule.onNodeWithContentDescription("Smazat Pryč").performClick()
        waitForText("Smazat slovíčko?")
        composeRule.onNodeWithText("Smazat").performClick()

        waitForText("Tato složka je prázdná")
    }

    @Test
    fun wordWithImage_survivesInDatabase_andReplacingOrRemovingCleansFiles() {
        waitForText("Tato složka je prázdná")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Titulek").performTextInput("jablko")
        viewModel.onEvent(AdminEvent.ImagePicked("content://foto-1"))
        composeRule.waitUntil(5_000) { imageStore.files.size == 1 }
        composeRule.onNodeWithText("Odebrat obrázek").assertExists()
        composeRule.onNodeWithText("Uložit").performClick()
        waitForText("jablko")
        val first = imageStore.files.single()

        // výměna: nový soubor přibude, starý se smaže
        composeRule.onNodeWithContentDescription("Upravit jablko").performClick()
        viewModel.onEvent(AdminEvent.ImagePicked("content://foto-2"))
        composeRule.waitUntil(5_000) { imageStore.files.size == 2 }
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { imageStore.files.size == 1 && first !in imageStore.files }

        // odebrání: smaže se i poslední soubor
        composeRule.onNodeWithContentDescription("Upravit jablko").performClick()
        // Dialog je delší než okno, tlačítko je pod zalomením a před kliknutím se musí odrolovat.
        composeRule.onNodeWithText("Odebrat obrázek").performScrollTo().performClick()
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { imageStore.files.isEmpty() }
    }

    @Test
    fun deletingFolderWithImagedWords_removesAllTheirFiles() {
        waitForText("Tato složka je prázdná")
        create("Nová složka", "Obrázky")
        composeRule.onNodeWithText("Obrázky").performClick()
        waitForText("Tato složka je prázdná")
        for (title in listOf("a", "b")) {
            composeRule.onNodeWithText("Nové slovíčko").performClick()
            composeRule.onNodeWithText("Titulek").performTextInput(title)
            viewModel.onEvent(AdminEvent.ImagePicked("content://$title"))
            composeRule.waitUntil(5_000) { imageStore.files.size == (if (title == "a") 1 else 2) }
            composeRule.onNodeWithText("Uložit").performClick()
            waitForText(title)
        }
        composeRule.onNodeWithText("Domů").performClick()
        waitForText("Obrázky")

        composeRule.onNodeWithContentDescription("Smazat Obrázky").performClick()
        waitForText("Smazat složku?")
        composeRule.onNodeWithText("Smazat").performClick()

        composeRule.waitUntil(5_000) { imageStore.files.isEmpty() }
    }

    @Test
    fun wordWithAudio_survivesInDatabase_andReplacingOrRemovingCleansFiles() {
        waitForText("Tato složka je prázdná")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Titulek").performTextInput("zvonek")
        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk-1"))
        composeRule.waitUntil(5_000) { audioStore.files.size == 1 }
        composeRule.onNodeWithText("Přehrát zvuk").assertExists()
        composeRule.onNodeWithText("Uložit").performClick()
        waitForText("zvonek")
        val first = audioStore.files.single()

        // výměna: nový soubor přibude, starý se smaže
        composeRule.onNodeWithContentDescription("Upravit zvonek").performClick()
        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk-2"))
        composeRule.waitUntil(5_000) { audioStore.files.size == 2 }
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { audioStore.files.size == 1 && first !in audioStore.files }

        // náhled používá přehrávač; zavření dialogu ho uvolní
        composeRule.onNodeWithContentDescription("Upravit zvonek").performClick()
        composeRule.onNodeWithText("Přehrát zvuk").performClick()
        composeRule.waitUntil(5_000) { audioPlayer.activePlays == 1 }
        composeRule.onNodeWithText("Zastavit zvuk").assertExists()
        composeRule.onNodeWithText("Zrušit").performClick()
        composeRule.waitUntil(5_000) { audioPlayer.activePlays == 0 }

        // odebrání: smaže se i poslední soubor
        composeRule.onNodeWithContentDescription("Upravit zvonek").performClick()
        composeRule.onNodeWithText("Odebrat zvuk").performClick()
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { audioStore.files.isEmpty() }
    }

    @Test
    fun deletingWordWithAudio_removesItsFile() {
        waitForText("Tato složka je prázdná")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Titulek").performTextInput("zvonek")
        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk"))
        composeRule.waitUntil(5_000) { audioStore.files.size == 1 }
        composeRule.onNodeWithText("Uložit").performClick()
        waitForText("zvonek")

        composeRule.onNodeWithContentDescription("Smazat zvonek").performClick()
        waitForText("Smazat slovíčko?")
        composeRule.onNodeWithText("Smazat").performClick()

        composeRule.waitUntil(5_000) { audioStore.files.isEmpty() }
    }
}
