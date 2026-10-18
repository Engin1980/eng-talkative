package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeSpeechProvider
import cz.talkative.app.ui.admin.FakeDictionaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModelTest {

    private val repository = FakeDictionaryRepository()
    private val configFlow = MutableStateFlow(AppConfig())
    private val configRepository = object : ConfigRepository {
        override val config: Flow<AppConfig> = configFlow
        override suspend fun update(transform: (AppConfig) -> AppConfig) {
            configFlow.value = transform(configFlow.value)
        }
    }
    private val speech = FakeSpeechProvider()
    private val audioPlayer = FakeAudioPlayer()
    private lateinit var viewModel: ClientViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ClientViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            ObserveConfigUseCase(configRepository),
            speech,
            audioPlayer,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    /** `stateIn(WhileSubscribed)` pracuje jen při odběru, proto se stav v testu odebírá na pozadí. */
    private fun TestScope.subscribe() {
        viewModel.uiState.launchIn(backgroundScope)
    }

    private fun titles() = viewModel.uiState.value.items.map { it.title }

    @Test
    fun initialState_isLoading() {
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun showsRootContentInManualOrder() = runVmTest {
        repository.createFolder(null, "Jídlo")
        repository.createWord(null, "Chci")
        subscribe()

        assertEquals(listOf("Jídlo", "Chci"), titles())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun openFolder_showsItsContent_andResetsPage() = runVmTest {
        val food = repository.createFolder(null, "Jídlo")
        repository.createWord(food.id, "Chleba")
        repository.createWord(null, "Ahoj")
        subscribe()
        viewModel.onEvent(ClientEvent.PageChanged(1))

        viewModel.onEvent(ClientEvent.OpenFolder(food.id))

        assertEquals(listOf("Chleba"), titles())
        assertEquals(0, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun pageChange_isKeptInState() = runVmTest {
        subscribe()

        viewModel.onEvent(ClientEvent.PageChanged(2))

        assertEquals(2, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun newItemsInCurrentFolder_appearLive() = runVmTest {
        subscribe()
        assertTrue(titles().isEmpty())

        repository.createWord(null, "Nové")

        assertEquals(listOf("Nové"), titles())
    }

    @Test
    fun config_isTakenFromRepository_andFollowsChanges() = runVmTest {
        subscribe()
        assertEquals(10_000L, viewModel.uiState.value.config.adminHoldMillis)

        configFlow.value = AppConfig(adminHoldMillis = 3_000L)

        assertEquals(3_000L, viewModel.uiState.value.config.adminHoldMillis)
    }

    @Test
    fun foldersAreOpenedByTheirId() = runVmTest {
        val a = repository.createFolder(null, "A")
        subscribe()

        assertTrue(viewModel.uiState.value.items.single() is Folder)
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))

        assertTrue(titles().isEmpty())
    }

    private fun path() = viewModel.uiState.value.path.map { it.title }

    /** Strom: Úvod › A › B › C, v každé složce jedno slovo. */
    private suspend fun buildTree(): List<Folder> {
        val a = repository.createFolder(null, "A")
        val b = repository.createFolder(a.id, "B")
        val c = repository.createFolder(b.id, "C")
        repository.createWord(a.id, "slovoA")
        repository.createWord(c.id, "slovoC")
        return listOf(a, b, c)
    }

    @Test
    fun breadcrumbs_followTheCurrentPath() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        assertTrue(path().isEmpty())

        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        assertEquals(listOf("A"), path())
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        assertEquals(listOf("A", "B", "C"), path())
        assertEquals(listOf("slovoC"), titles())
    }

    @Test
    fun home_goesToRootFromAnyDepth_andResetsPage() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))
        viewModel.onEvent(ClientEvent.PageChanged(3))

        viewModel.onEvent(ClientEvent.GoHome)

        assertTrue(path().isEmpty())
        assertEquals(listOf("A"), titles())
        assertEquals(0, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun back_goesUpExactlyOneLevel_andStaysInRoot() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        viewModel.onEvent(ClientEvent.NavigateUp)
        assertEquals(listOf("A", "B"), path())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertEquals(listOf("A"), path())
        assertEquals(listOf("B", "slovoA"), titles())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertTrue(path().isEmpty())
        viewModel.onEvent(ClientEvent.NavigateUp)
        assertTrue(path().isEmpty())
    }

    @Test
    fun clickingAncestorCrumb_jumpsThere() = runVmTest {
        val (a, b, c) = buildTree()
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(a.id))
        viewModel.onEvent(ClientEvent.OpenFolder(b.id))
        viewModel.onEvent(ClientEvent.OpenFolder(c.id))

        viewModel.onEvent(ClientEvent.GoToFolder(a.id))
        assertEquals(listOf("A"), path())

        viewModel.onEvent(ClientEvent.GoToFolder(null))
        assertTrue(path().isEmpty())
    }

    private fun sentence() = viewModel.uiState.value.sentence.map { it.word.title }

    private fun wordOf(item: cz.talkative.app.domain.model.DictionaryItem) =
        item as cz.talkative.app.domain.model.Word

    @Test
    fun newViewModel_startsWithEmptySentence() = runVmTest {
        subscribe()

        assertTrue(viewModel.uiState.value.sentence.isEmpty())
    }

    @Test
    fun selectedWordsAreAppendedInOrder_includingRepeats() = runVmTest {
        val chci = repository.createWord(null, "chci")
        val pit = repository.createWord(null, "pít")
        subscribe()

        viewModel.onEvent(ClientEvent.WordSelected(chci))
        viewModel.onEvent(ClientEvent.WordSelected(pit))
        viewModel.onEvent(ClientEvent.WordSelected(chci))

        assertEquals(listOf("chci", "pít", "chci"), sentence())
        val ids = viewModel.uiState.value.sentence.map { it.entryId }
        assertEquals(3, ids.toSet().size) // každý výskyt má vlastní identitu
    }

    @Test
    fun removeFromSentence_removesOnlyThatOccurrence() = runVmTest {
        val a = repository.createWord(null, "a")
        val b = repository.createWord(null, "b")
        subscribe()
        viewModel.onEvent(ClientEvent.WordSelected(a))
        viewModel.onEvent(ClientEvent.WordSelected(b))
        viewModel.onEvent(ClientEvent.WordSelected(a))
        val secondEntry = viewModel.uiState.value.sentence[2].entryId

        viewModel.onEvent(ClientEvent.RemoveFromSentence(secondEntry))

        assertEquals(listOf("a", "b"), sentence())
    }

    @Test
    fun clearSentence_removesEverything() = runVmTest {
        val a = repository.createWord(null, "a")
        subscribe()
        viewModel.onEvent(ClientEvent.WordSelected(a))
        viewModel.onEvent(ClientEvent.WordSelected(a))

        viewModel.onEvent(ClientEvent.ClearSentence)

        assertTrue(viewModel.uiState.value.sentence.isEmpty())
    }

    @Test
    fun sentenceSurvivesNavigationAndPaging() = runVmTest {
        val folder = repository.createFolder(null, "Jídlo")
        val word = repository.createWord(folder.id, "chleba")
        subscribe()
        viewModel.onEvent(ClientEvent.OpenFolder(folder.id))
        viewModel.onEvent(ClientEvent.WordSelected(word))

        viewModel.onEvent(ClientEvent.GoHome)
        viewModel.onEvent(ClientEvent.PageChanged(2))

        assertEquals(listOf("chleba"), sentence())
    }

    @Test
    fun sentenceKeepsSnapshotOfWord_whenWordIsRenamedLater() = runVmTest {
        val word = repository.createWord(null, "stare")
        subscribe()
        viewModel.onEvent(ClientEvent.WordSelected(word))

        repository.updateWordTitle(word.id, "nove")

        assertEquals(listOf("stare"), sentence())
        assertEquals("nove", wordOf(viewModel.uiState.value.items.single()).title)
    }

    @Test
    fun onCleared_releasesSpeechProvider() {
        assertEquals(false, speech.isShutdown)

        val onCleared = ClientViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(viewModel)

        assertEquals(true, speech.isShutdown)
    }

    private suspend fun buildSentence(vararg titles: String) {
        titles.forEach { viewModel.onEvent(ClientEvent.WordSelected(repository.createWord(null, it))) }
    }

    @Test
    fun play_readsWholeSentenceAsOneUtterance() = runVmTest {
        subscribe()
        buildSentence("chci", "jablko", "prosím")

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertEquals(listOf("chci jablko prosím"), speech.spoken)
        assertTrue(viewModel.uiState.value.isSpeaking)
    }

    @Test
    fun play_readsAlternateTextInsteadOfTitle() = runVmTest {
        subscribe()
        viewModel.onEvent(ClientEvent.WordSelected(repository.createWord(null, "chci")))
        viewModel.onEvent(ClientEvent.WordSelected(repository.createWord(null, "KD", alternateText = "ká dé")))

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertEquals(listOf("chci ká dé"), speech.spoken)
    }

    @Test
    fun playWithEmptySentence_doesNothing() = runVmTest {
        subscribe()

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertTrue(speech.spoken.isEmpty())
        assertEquals(false, viewModel.uiState.value.isSpeaking)
    }

    @Test
    fun secondToggleWhileSpeaking_stopsSpeech_andPlayWorksAgain() = runVmTest {
        subscribe()
        buildSentence("ahoj")
        viewModel.onEvent(ClientEvent.TogglePlayback)

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertEquals(1, speech.stopCount)
        assertEquals(false, viewModel.uiState.value.isSpeaking)
        assertEquals(1, speech.spoken.size) // zastavení nespustilo novou řeč

        viewModel.onEvent(ClientEvent.TogglePlayback)
        assertEquals(listOf("ahoj", "ahoj"), speech.spoken)
        assertTrue(viewModel.uiState.value.isSpeaking)
    }

    @Test
    fun speechThatFinishesOnItsOwn_returnsButtonToPlay_andSentenceStays() = runVmTest {
        subscribe()
        buildSentence("ahoj")
        viewModel.onEvent(ClientEvent.TogglePlayback)

        speech.finishSpeaking()

        assertEquals(false, viewModel.uiState.value.isSpeaking)
        assertEquals(listOf("ahoj"), sentence())
    }

    @Test
    fun changingSentenceWhileSpeaking_doesNotRestartSpeech_andNextToggleStops() = runVmTest {
        subscribe()
        buildSentence("ahoj")
        viewModel.onEvent(ClientEvent.TogglePlayback)

        buildSentence("světe")
        viewModel.onEvent(ClientEvent.RemoveFromSentence(viewModel.uiState.value.sentence.first().entryId))
        assertEquals(listOf("ahoj"), speech.spoken) // rozmluvená řeč se nemění
        assertTrue(viewModel.uiState.value.isSpeaking)

        viewModel.onEvent(ClientEvent.TogglePlayback)
        assertEquals(1, speech.stopCount)
        assertEquals(false, viewModel.uiState.value.isSpeaking)
    }

    @Test
    fun speechError_isShownUntilDismissed() = runVmTest {
        subscribe()

        speech.fail(SpeechError.LANGUAGE_MISSING)
        assertEquals(SpeechError.LANGUAGE_MISSING, viewModel.uiState.value.speechError)

        viewModel.onEvent(ClientEvent.DismissSpeechError)
        assertEquals(null, viewModel.uiState.value.speechError)
    }

    // ---- vlastní MP3 ve větě (krok 5.3)

    private suspend fun pick(title: String, audioFile: String? = null) =
        viewModel.onEvent(ClientEvent.WordSelected(repository.createWord(null, title, audioFile = audioFile)))

    @Test
    fun sentenceWithMp3_playsTtsThenMp3ThenTts_withoutOverlap() = runVmTest {
        subscribe()
        pick("A")
        pick("zvonek", audioFile = "zvonek.mp3")
        pick("B")
        pick("C")

        viewModel.onEvent(ClientEvent.TogglePlayback)
        assertEquals(listOf("A"), speech.spoken)
        assertTrue(viewModel.uiState.value.isSpeaking)

        speech.finishSpeaking()
        assertEquals(listOf("zvonek.mp3"), audioPlayer.played)
        assertTrue(viewModel.uiState.value.isSpeaking) // mezi úseky tlačítko zůstává Stop

        audioPlayer.finishPlaying()
        assertEquals(listOf("A", "B C"), speech.spoken)

        speech.finishSpeaking()
        assertEquals(false, viewModel.uiState.value.isSpeaking)
    }

    @Test
    fun stopButton_duringMp3_stopsEverything() = runVmTest {
        subscribe()
        pick("zvonek", audioFile = "zvonek.mp3")
        pick("B")
        viewModel.onEvent(ClientEvent.TogglePlayback)
        assertEquals(1, audioPlayer.activePlays)

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertEquals(false, viewModel.uiState.value.isSpeaking)
        assertEquals(0, audioPlayer.activePlays)
        assertTrue(speech.spoken.isEmpty())
    }

    @Test
    fun brokenMp3_showsErrorWithoutBlockingPlayButton() = runVmTest {
        subscribe()
        audioPlayer.failingFiles += "rozbite.mp3"
        pick("zvonek", audioFile = "rozbite.mp3")
        pick("B")

        viewModel.onEvent(ClientEvent.TogglePlayback)

        assertEquals(cz.talkative.app.domain.speech.SpeechError.AUDIO_FAILED, viewModel.uiState.value.speechError)
        assertEquals(false, viewModel.uiState.value.isSpeaking)
        assertTrue(speech.spoken.isEmpty())

        viewModel.onEvent(ClientEvent.DismissSpeechError)
        assertNull(viewModel.uiState.value.speechError)

        viewModel.onEvent(ClientEvent.ClearSentence)
        pick("C")
        viewModel.onEvent(ClientEvent.TogglePlayback) // tlačítko dál funguje
        assertEquals(listOf("C"), speech.spoken)
    }

    @Test
    fun viewModelRelease_stopsRunningMp3() = runVmTest {
        subscribe()
        pick("zvonek", audioFile = "zvonek.mp3")
        viewModel.onEvent(ClientEvent.TogglePlayback)
        assertEquals(1, audioPlayer.activePlays)

        val onCleared = ClientViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(viewModel)

        assertEquals(0, audioPlayer.activePlays)
        assertTrue(speech.isShutdown)
    }
}
