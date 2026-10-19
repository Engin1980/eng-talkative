package cz.talkative.app.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.audio.AudioPlayer
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveConfigUseCase
import cz.talkative.app.domain.speech.SpeechProvider
import cz.talkative.app.domain.speech.PlaybackPlanner
import cz.talkative.app.domain.speech.SentencePlayer
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClientViewModel @Inject constructor(
    observeItems: ObserveFolderItemsUseCase,
    private val getPath: GetFolderPathUseCase,
    observeConfig: ObserveConfigUseCase,
    /** Instance patří tomuto view modelu a uvolní se v [onCleared]. */
    speech: SpeechProvider,
    audioPlayer: AudioPlayer,
) : ViewModel() {

    /** Věta se přehrává po úsecích (TTS a MP3); drží se tu kvůli jednomu hlasu na obrazovku a uvolnění v [onCleared]. */
    private val player = SentencePlayer(speech, audioPlayer, viewModelScope)

    /** Aktuální složka; `null` je kořen. Nezávisí na orientaci displeje, ta mění jen velikost mřížky. */
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val pageIndex = MutableStateFlow(0)
    private val sentence = MutableStateFlow<List<SentenceEntry>>(emptyList())
    private var nextEntryId = 0

    /** Aktuální konfigurace i pro obsluhu událostí (např. čtení slova po klepnutí). */
    private val config = observeConfig().stateIn(viewModelScope, SharingStarted.Eagerly, AppConfig())

    private val browsingState: Flow<ClientUiState> = combine(
        currentFolderId.flatMapLatest { folderId ->
            observeItems(folderId).map { items -> getPath(folderId) to items }
        },
        pageIndex,
        sentence,
        config,
    ) { (path, items), page, sentenceEntries, currentConfig ->
        ClientUiState(
            path = path,
            items = items,
            pageIndex = page,
            sentence = sentenceEntries,
            config = currentConfig,
            isLoading = false,
        )
    }

    val uiState: StateFlow<ClientUiState> = combine(browsingState, player.state) { state, speechState ->
        state.copy(isSpeaking = speechState.isSpeaking, speechError = speechState.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientUiState())

    fun onEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.OpenFolder -> goTo(event.folderId)
            is ClientEvent.GoToFolder -> goTo(event.folderId)
            ClientEvent.GoHome -> goTo(null)
            ClientEvent.NavigateUp -> viewModelScope.launch {
                val path = getPath(currentFolderId.value)
                goTo(path.getOrNull(path.size - 2)?.id)
            }
            is ClientEvent.PageChanged -> pageIndex.value = event.pageIndex
            is ClientEvent.WordSelected -> onWordTapped(event.word)
            is ClientEvent.WordDropped -> onWordDropped(event.word)
            is ClientEvent.RemoveFromSentence -> sentence.value = sentence.value.filterNot { it.entryId == event.entryId }
            ClientEvent.ClearSentence -> sentence.value = emptyList()
            ClientEvent.TogglePlayback -> togglePlayback()
            ClientEvent.DismissSpeechError -> player.clearError()
        }
    }

    /**
     * Klepnutí na slovo: v režimu klepnutí ho přidá do věty a je-li to zapnuté, přečte ho
     * (čtení platí i v režimu tažení, kde klepnutí slovo nepřidává).
     */
    private fun onWordTapped(word: Word) {
        val entry = SentenceEntry(nextEntryId++, word)
        if (config.value.addWordMode == AddWordMode.TAP) sentence.value += entry
        if (config.value.speakWordOnTap) player.play(PlaybackPlanner.plan(listOf(entry)))
    }

    /** Přetažení slova do věty přidává jen v režimu tažení; v režimu klepnutí se ignoruje. */
    private fun onWordDropped(word: Word) {
        if (config.value.addWordMode != AddWordMode.DRAG) return
        sentence.value += SentenceEntry(nextEntryId++, word)
    }

    /** Při přehrávání tlačítko zastaví; jinak přehraje větu po úsecích (prázdná věta se nepřehrává). */
    private fun togglePlayback() {
        if (player.isPlaying) {
            player.stop()
        } else {
            player.play(PlaybackPlanner.plan(sentence.value)) {
                // Jen po dohrání celé věty: po Stopu nebo chybě věta zůstane.
                if (config.value.autoClearAfterPlayback) sentence.value = emptyList()
            }
        }
    }

    override fun onCleared() {
        player.shutdown()
    }

    private fun goTo(folderId: String?) {
        currentFolderId.value = folderId
        pageIndex.value = 0
    }
}
