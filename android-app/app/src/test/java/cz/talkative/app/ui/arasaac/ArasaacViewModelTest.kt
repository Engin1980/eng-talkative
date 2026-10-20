package cz.talkative.app.ui.arasaac

import androidx.lifecycle.SavedStateHandle
import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.domain.pictogram.PictogramSearchResult
import cz.talkative.app.testing.FakePictogramDownloader
import cz.talkative.app.testing.FakePictogramSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArasaacViewModelTest {

    private val search = FakePictogramSearch()
    private val downloader = FakePictogramDownloader()
    private val house = Pictogram(1, "dům", "http://x/1_300.png")
    private val home = Pictogram(2, "domov", "http://x/2_300.png")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(initialQuery: String? = "dům") = ArasaacViewModel(
        SavedStateHandle(if (initialQuery == null) emptyMap() else mapOf(ARASAAC_QUERY_ARG to initialQuery)),
        search,
        downloader,
    )

    private val ArasaacViewModel.state get() = uiState.value

    /** URI souboru (na Windows s písmenem disku, proto se kontroluje jen začátek a konec). */
    private fun assertPicked(viewModel: ArasaacViewModel) {
        val uri = viewModel.state.pickedImageUri
        assertTrue("URI: $uri", uri != null && uri.startsWith("file:") && uri.endsWith("/cache/pictograms/pictogram-1.png"))
    }

    @Test
    fun queryIsPrefilledWithTheWordTitle_andNothingIsSearchedYet() {
        val viewModel = viewModel("dům")

        assertEquals("dům", viewModel.state.query)
        assertEquals(SearchStatus.NotSearched, viewModel.state.search)
        assertTrue(search.queries.isEmpty()) // hledá se až po potvrzení
    }

    @Test
    fun missingTitle_startsWithEmptyQuery() {
        assertEquals("", viewModel(null).state.query)
    }

    @Test
    fun typing_doesNotSearch_butSearchEventDoes() {
        val viewModel = viewModel("")
        search.result = PictogramSearchResult.Found(listOf(house, home))

        viewModel.onEvent(ArasaacEvent.QueryChanged("d"))
        viewModel.onEvent(ArasaacEvent.QueryChanged("dům"))
        assertTrue(search.queries.isEmpty())

        viewModel.onEvent(ArasaacEvent.Search)

        assertEquals(listOf("dům"), search.queries)
        assertEquals(SearchStatus.Results(listOf(house, home)), viewModel.state.search)
    }

    @Test
    fun blankQuery_isNotSearched() {
        val viewModel = viewModel("   ")

        viewModel.onEvent(ArasaacEvent.Search)

        assertTrue(search.queries.isEmpty())
        assertEquals(SearchStatus.NotSearched, viewModel.state.search)
    }

    @Test
    fun blankQueryAfterResults_clearsThem() {
        val viewModel = viewModel("dům")
        search.result = PictogramSearchResult.Found(listOf(house))
        viewModel.onEvent(ArasaacEvent.Search)

        viewModel.onEvent(ArasaacEvent.QueryChanged(" "))
        viewModel.onEvent(ArasaacEvent.Search)

        assertEquals(SearchStatus.NotSearched, viewModel.state.search)
    }

    @Test
    fun searching_isShownWhileWaiting_thenResults() {
        val viewModel = viewModel("dům")
        val gate = CompletableDeferred<Unit>()
        search.gate = gate
        search.result = PictogramSearchResult.Found(listOf(house))

        viewModel.onEvent(ArasaacEvent.Search)
        assertEquals(SearchStatus.Searching, viewModel.state.search)

        gate.complete(Unit)
        assertEquals(SearchStatus.Results(listOf(house)), viewModel.state.search)
    }

    @Test
    fun emptyResult_meansNothingFound() {
        val viewModel = viewModel("xyz")
        search.result = PictogramSearchResult.Found(emptyList())

        viewModel.onEvent(ArasaacEvent.Search)

        assertEquals(SearchStatus.Nothing, viewModel.state.search)
    }

    @Test
    fun failure_isShown_andRetrySearchesAgainWithTheSameText() {
        val viewModel = viewModel("dům")
        search.result = PictogramSearchResult.Failed(PictogramError.NO_NETWORK)
        viewModel.onEvent(ArasaacEvent.Search)
        assertEquals(SearchStatus.Failed(PictogramError.NO_NETWORK), viewModel.state.search)

        viewModel.onEvent(ArasaacEvent.QueryChanged("něco jiného")) // úprava pole Zkusit znovu neovlivní
        search.result = PictogramSearchResult.Found(listOf(house))
        viewModel.onEvent(ArasaacEvent.Retry)

        assertEquals(listOf("dům", "dům"), search.queries)
        assertEquals(SearchStatus.Results(listOf(house)), viewModel.state.search)
    }

    @Test
    fun retryWithoutAnyEarlierSearch_doesNothing() {
        val viewModel = viewModel("dům")

        viewModel.onEvent(ArasaacEvent.Retry)

        assertTrue(search.queries.isEmpty())
    }

    @Test
    fun selecting_downloadsExactlyOnce_andHandsOverTheFile() {
        val viewModel = viewModel("dům")
        val gate = CompletableDeferred<Unit>()
        downloader.gate = gate

        viewModel.onEvent(ArasaacEvent.Select(house))
        assertEquals(1, viewModel.state.downloadingId)
        viewModel.onEvent(ArasaacEvent.Select(house)) // zdvojené klepnutí
        viewModel.onEvent(ArasaacEvent.Select(home)) // jiný piktogram během stahování
        gate.complete(Unit)

        assertEquals(listOf(1), downloader.downloaded)
        assertNull(viewModel.state.downloadingId)
        assertPicked(viewModel)
    }

    @Test
    fun pickedFile_isHandedOverOnlyOnce() {
        val viewModel = viewModel("dům")
        viewModel.onEvent(ArasaacEvent.Select(house))

        viewModel.onEvent(ArasaacEvent.PickedConsumed)

        assertNull(viewModel.state.pickedImageUri)
    }

    @Test
    fun downloadFailure_isShown_andAnotherAttemptIsPossible() {
        val viewModel = viewModel("dům")
        downloader.failure = PictogramDownloadResult.Failed(PictogramError.TIMEOUT)

        viewModel.onEvent(ArasaacEvent.Select(house))

        assertEquals(PictogramError.TIMEOUT, viewModel.state.downloadError)
        assertNull(viewModel.state.downloadingId)
        assertNull(viewModel.state.pickedImageUri)

        downloader.failure = null
        viewModel.onEvent(ArasaacEvent.Select(house))

        assertNull(viewModel.state.downloadError)
        assertEquals(listOf(1, 1), downloader.downloaded)
        assertPicked(viewModel)
    }

    @Test
    fun newSearch_clearsTheOldDownloadError() {
        val viewModel = viewModel("dům")
        downloader.failure = PictogramDownloadResult.Failed(PictogramError.NO_NETWORK)
        viewModel.onEvent(ArasaacEvent.Select(house))

        viewModel.onEvent(ArasaacEvent.Search)

        assertNull(viewModel.state.downloadError)
    }
}
