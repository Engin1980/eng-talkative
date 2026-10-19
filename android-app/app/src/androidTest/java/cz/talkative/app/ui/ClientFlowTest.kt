package cz.talkative.app.ui

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.testing.FakeSpeechProvider
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientViewModel
import cz.talkative.app.ui.client.SENTENCE_BAR_TAG
import cz.talkative.app.ui.client.SENTENCE_WORD_TAG
import cz.talkative.app.ui.theme.TalkativeTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Klient nad skutečnou (in-memory) databází: z dat vytvořených administrací se skládá věta. */
@RunWith(AndroidJUnit4::class)
class ClientFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: TalkativeDatabase
    private val speech = FakeSpeechProvider()
    private val configFlow = MutableStateFlow(AppConfig())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        val repository = RoomDictionaryRepository(database.dictionaryDao(), { System.currentTimeMillis() }, FakeImageStore(), FakeAudioStore())
        runBlocking {
            val food = repository.createFolder(null, "Jídlo")
            val fruit = repository.createFolder(food.id, "Ovoce")
            repository.createWord(null, "chci")
            repository.createWord(food.id, "chleba")
            repository.createWord(fruit.id, "jablko")
            repository.createWord(fruit.id, "hruška")
        }
        val configRepository = object : ConfigRepository {
            override val config: Flow<AppConfig> = configFlow
            override suspend fun update(transform: (AppConfig) -> AppConfig) {
                configFlow.value = transform(configFlow.value)
            }
        }
        val viewModel = ClientViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            ObserveConfigUseCase(configRepository),
            speech,
            FakeAudioPlayer(),
        )
        composeRule.setContent {
            TalkativeTheme {
                val state by viewModel.uiState.collectAsState()
                ClientScreen(state = state, onEvent = viewModel::onEvent, onOpenAdmin = {}, isLandscape = true)
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

    /** Dlaždice v mřížce (ne stejnojmenné slovo ve větě). */
    private fun tile(text: String) =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(SENTENCE_BAR_TAG)).not())

    private fun sentenceWordCount() = composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG).fetchSemanticsNodes().size

    private fun waitForSentenceWords(count: Int) = composeRule.waitUntil(5_000) { sentenceWordCount() == count }

    @Test
    fun wholeTreeCanBeBrowsed_andSentenceBuiltFromWordsOnDifferentLevels() {
        waitForText("Jídlo")
        tile("chci").performClick()
        waitForSentenceWords(1)

        composeRule.onNodeWithText("Jídlo").performClick()
        waitForText("chleba")
        tile("chleba").performClick()
        composeRule.onNodeWithText("Ovoce").performClick()
        waitForText("jablko")
        tile("jablko").performClick()
        tile("jablko").performClick() // stejné slovo podruhé
        waitForSentenceWords(4)

        // drobečky odpovídají poloze a „Domů“ vede do kořene; věta zůstala
        composeRule.onNodeWithText("Úvod").assertIsDisplayed()
        composeRule.onNodeWithText("Domů").performClick()
        waitForText("chci")
        assertEquals(4, sentenceWordCount())
    }

    @Test
    fun backGoesOneLevelUp_andClearEmptiesTheSentence() {
        waitForText("Jídlo")
        composeRule.onNodeWithText("Jídlo").performClick()
        waitForText("Ovoce")
        composeRule.onNodeWithText("Ovoce").performClick()
        waitForText("hruška")

        composeRule.onNodeWithText("Zpět").performClick()
        waitForText("chleba")

        tile("hruška").assertDoesNotExist()
        tile("chleba").performClick()
        waitForSentenceWords(1)
        composeRule.onNodeWithText("Smazat větu").performClick()
        waitForSentenceWords(0)
    }

    @Test
    fun speakOnTap_readsTheTappedWord_onlyWhenEnabled() {
        waitForText("Jídlo")
        tile("chci").performClick()
        waitForSentenceWords(1)
        assertEquals(emptyList<String>(), speech.spoken)

        configFlow.value = AppConfig(speakWordOnTap = true)
        composeRule.waitForIdle()
        tile("chci").performClick()
        waitForSentenceWords(2)

        assertEquals(listOf("chci"), speech.spoken)
    }

    @Test
    fun autoClear_emptiesTheSentenceAfterPlayback_butNotAfterStop() {
        configFlow.value = AppConfig(autoClearAfterPlayback = true)
        waitForText("Jídlo")
        tile("chci").performClick()
        waitForSentenceWords(1)

        composeRule.onNodeWithText("Přehrát").performClick()
        composeRule.waitUntil(5_000) { speech.spoken.size == 1 }
        composeRule.onNodeWithText("Zastavit").performClick()
        speech.finishSpeaking()
        composeRule.waitForIdle()
        assertEquals(1, sentenceWordCount()) // po Stopu věta zůstala

        composeRule.onNodeWithText("Přehrát").performClick()
        composeRule.waitUntil(5_000) { speech.spoken.size == 2 }
        speech.finishSpeaking()
        waitForSentenceWords(0) // po dohrání se smazala
    }

    @Test
    fun withoutAutoClear_sentenceStaysAfterPlayback() {
        waitForText("Jídlo")
        tile("chci").performClick()
        waitForSentenceWords(1)

        composeRule.onNodeWithText("Přehrát").performClick()
        composeRule.waitUntil(5_000) { speech.spoken.size == 1 }
        speech.finishSpeaking()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Přehrát").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(1, sentenceWordCount())
    }
}
