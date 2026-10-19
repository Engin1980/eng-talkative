package cz.talkative.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.ui.client.DRAGGED_WORD_TAG
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.ui.client.ClientEvent
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.client.SENTENCE_BAR_TAG
import cz.talkative.app.ui.client.SENTENCE_WORD_TAG
import cz.talkative.app.ui.common.LocalImageResolver
import cz.talkative.app.ui.common.WORD_IMAGE_TAG
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClientScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<ClientEvent>()

    private fun words(count: Int): List<DictionaryItem> =
        (1..count).map { Word("w$it", "default", "slovo$it", null, it, 0) }

    private fun visibleTiles(count: Int) = (1..count).count {
        composeRule.onAllNodesWithText("slovo$it").fetchSemanticsNodes().isNotEmpty()
    }

    private fun show(state: ClientUiState, isLandscape: Boolean = false) {
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(state, onEvent = { events += it }, onOpenAdmin = {}, isLandscape = isLandscape)
            }
        }
    }

    private fun loaded(items: List<DictionaryItem>, page: Int = 0) =
        ClientUiState(items = items, pageIndex = page, isLoading = false)

    @Test
    fun tenItemsInThreeByThree_showNineThenOne_withPageControls() {
        val state = mutableStateOf(loaded(words(10)))
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(state.value, onEvent = { events += it }, onOpenAdmin = {}, isLandscape = false)
            }
        }

        assertEquals(9, visibleTiles(10))
        composeRule.onNodeWithText("Stránka 1 z 2").assertIsDisplayed()

        composeRule.onNodeWithText("Další").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf<ClientEvent>(ClientEvent.PageChanged(1)), events)

        state.value = loaded(words(10), page = 1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("slovo10").assertIsDisplayed()
        assertEquals(1, visibleTiles(10))
        composeRule.onNodeWithText("Stránka 2 z 2").assertIsDisplayed()
    }

    @Test
    fun landscapeGrid_isBigger_soTenItemsFitOnOnePage() {
        show(loaded(words(10)), isLandscape = true)

        assertEquals(10, visibleTiles(10))
        assertEquals(0, composeRule.onAllNodesWithText("Další").fetchSemanticsNodes().size) // jedna stránka
    }

    @Test
    fun rotation_recomputesGrid_withoutChangingTheItems() {
        val landscape = mutableStateOf(false)
        composeRule.setContent {
            TalkativeTheme {
                ClientScreen(loaded(words(10)), onEvent = {}, onOpenAdmin = {}, isLandscape = landscape.value)
            }
        }
        assertEquals(9, visibleTiles(10))

        landscape.value = true
        composeRule.waitForIdle()
        assertEquals(10, visibleTiles(10))

        landscape.value = false
        composeRule.waitForIdle()
        assertEquals(9, visibleTiles(10))
    }

    @Test
    fun exactMultipleOfPage_hasNoExtraPage() {
        show(loaded(words(9)))

        assertEquals(9, visibleTiles(9))
        assertEquals(0, composeRule.onAllNodesWithText("Další").fetchSemanticsNodes().size)
    }

    @Test
    fun pageBeyondTheEnd_isClamped() {
        show(loaded(words(10), page = 7))

        composeRule.onNodeWithText("Stránka 2 z 2").assertIsDisplayed()
        composeRule.onNodeWithText("slovo10").assertIsDisplayed()
    }

    @Test
    fun emptyFolder_showsMessage() {
        show(loaded(emptyList()))

        composeRule.onNodeWithText("Tato složka je prázdná.").assertIsDisplayed()
    }

    @Test
    fun clickingFolder_opensIt() {
        val folder = Folder("f1", "default", "Jídlo", null, 0, 0)
        show(loaded(listOf(folder)))

        composeRule.onNodeWithText("Jídlo").performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.OpenFolder("f1")), events)
    }

    @Test
    fun previousButtonIsDisabledOnFirstPage() {
        show(loaded(words(10)))

        composeRule.onNodeWithText("Předchozí").assertIsDisplayed()
        composeRule.onNodeWithText("Předchozí").performClick()
        assertEquals(emptyList<ClientEvent>(), events)
    }

    private fun folder(id: String, title: String) = Folder(id, "default", title, null, 0, 0)

    @Test
    fun inRoot_backAndHomeAreDisabled_andOnlyRootCrumbIsShown() {
        show(loaded(words(1)))

        composeRule.onNodeWithText("Úvod").assertIsDisplayed()
        composeRule.onNodeWithText("Zpět").performClick()
        composeRule.onNodeWithText("Domů").performClick()
        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun breadcrumbs_showWholePath_andNavigationButtonsSendEvents() {
        val path = listOf(folder("a", "Jídlo"), folder("b", "Ovoce"))
        show(ClientUiState(path = path, items = words(1), isLoading = false))

        composeRule.onNodeWithText("Úvod").assertIsDisplayed()
        composeRule.onNodeWithText("Jídlo").assertIsDisplayed()
        composeRule.onNodeWithText("Ovoce").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Domů").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Jídlo").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onNodeWithText("Úvod").performClick()

        assertEquals(
            listOf(
                ClientEvent.NavigateUp,
                ClientEvent.GoHome,
                ClientEvent.GoToFolder("a"),
                ClientEvent.GoToFolder(null),
            ),
            events,
        )
    }

    private fun entries(vararg titles: String) = titles.mapIndexed { index, title ->
        SentenceEntry(index, Word("w$index", "default", title, null, index, 0))
    }

    private fun sentenceState(count: Int, hold: Long = 1_000L) = ClientUiState(
        items = emptyList(),
        sentence = entries(*Array(count) { "věta${it + 1}" }),
        config = AppConfig(removeHoldMillis = hold),
        isLoading = false,
    )

    @Test
    fun tappingGridWord_sendsWordSelected() {
        val word = Word("w1", "default", "chci", null, 0, 0)
        show(loaded(listOf(word)))

        composeRule.onNodeWithText("chci").performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.WordSelected(word)), events)
    }

    @Test
    fun emptySentence_showsHint_andClearButtonIsDisabled() {
        show(loaded(words(1)))

        composeRule.onNodeWithText("Klepnutím na slovo ho přidáte do věty.").assertIsDisplayed()
        composeRule.onNodeWithText("Smazat větu").performClick()
        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun clearButton_sendsClearSentence_andHasMinimumTouchTarget() {
        show(sentenceState(2))

        composeRule.onNodeWithText("Smazat větu").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.ClearSentence), events)
    }

    @Test
    fun shortTapOnSentenceWord_removesNothing() {
        show(sentenceState(1))

        composeRule.onNodeWithTag(SENTENCE_WORD_TAG).performClick()
        composeRule.mainClock.advanceTimeBy(5_000)

        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun holdingSentenceWordForConfiguredInterval_removesIt() {
        show(sentenceState(2, hold = 1_000L))
        composeRule.mainClock.autoAdvance = false

        composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG)[1].performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(900)
        assertEquals(emptyList<ClientEvent>(), events) // ještě nedrží dost dlouho

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(listOf<ClientEvent>(ClientEvent.RemoveFromSentence(1)), events)

        composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG)[1].performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(3_000)
        assertEquals(1, events.size) // jedno podržení = jedno odebrání
    }

    @Test
    fun releasingBeforeTheInterval_removesNothing() {
        show(sentenceState(1, hold = 1_000L))
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(SENTENCE_WORD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(800)
        composeRule.onNodeWithTag(SENTENCE_WORD_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(5_000)

        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun longSentence_staysInsideBarHeightLimit_andLastWordIsReachable() {
        show(sentenceState(40))
        composeRule.waitForIdle()

        val barHeight = composeRule.onNodeWithTag(SENTENCE_BAR_TAG).getUnclippedBoundsInRoot().height
        assertTrue("výška řádku věty: $barHeight", barHeight <= 176.dp)
        composeRule.onNodeWithText("věta40").assertIsDisplayed() // věta se sama posune na konec
        composeRule.onNodeWithText("Smazat větu").assertIsDisplayed() // ovládání zůstává na místě
    }

    @Test
    fun playButton_isDisabledForEmptySentence() {
        show(loaded(words(1)))

        composeRule.onNodeWithText("Přehrát").assertIsNotEnabled()
    }

    @Test
    fun playButton_sendsToggle_andHasMinimumTouchTarget() {
        show(sentenceState(2))

        composeRule.onNodeWithText("Přehrát").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.TogglePlayback), events)
    }

    @Test
    fun whileSpeaking_buttonIsStop_evenWithEmptySentence() {
        show(ClientUiState(isSpeaking = true, isLoading = false))

        composeRule.onNodeWithText("Zastavit").assertIsEnabled().performClick()

        assertEquals(0, composeRule.onAllNodesWithText("Přehrát").fetchSemanticsNodes().size)
        assertEquals(listOf<ClientEvent>(ClientEvent.TogglePlayback), events)
    }

    @Test
    fun speechErrors_showHelpfulMessage_andCanBeDismissed() {
        show(ClientUiState(speechError = SpeechError.LANGUAGE_MISSING, isLoading = false))

        composeRule.onNodeWithText("Čtení nahlas se nepodařilo").assertIsDisplayed()
        composeRule.onNodeWithText("Na tomto zařízení chybí český hlas.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Rozumím").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.DismissSpeechError), events)
    }

    @Test
    fun engineError_explainsWhatToDo() {
        show(ClientUiState(speechError = SpeechError.ENGINE_UNAVAILABLE, isLoading = false))

        composeRule.onNodeWithText("hlasový modul", substring = true).assertIsDisplayed()
    }

    @Test
    fun brokenCustomSound_explainsWhatHappened() {
        show(ClientUiState(speechError = SpeechError.AUDIO_FAILED, isLoading = false))

        composeRule.onNodeWithText("Vlastní zvuk jednoho ze slovíček se nepodařilo přehrát", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Čtení věty bylo zastaveno", substring = true).assertIsDisplayed()
    }

    // ---- obrázky (krok 4.3)

    private val appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Skutečný malý obrázek v cache; resolver ho nabídne pod názvem `ok.png`, ostatní názvy "neexistují". */
    private fun showWithImages(state: ClientUiState) {
        val file = java.io.File(appContext.cacheDir, "client-image-test.png")
        file.outputStream().use {
            android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalImageResolver provides { name -> file.takeIf { name == "ok.png" } },
            ) {
                TalkativeTheme {
                    ClientScreen(state, onEvent = { events += it }, onOpenAdmin = {}, isLandscape = true)
                }
            }
        }
    }

    private fun word(title: String, image: String? = null, index: Int = 0) =
        Word("w$index-$title", "default", title, null, index, 0, image)

    private fun imageNodesIn(ancestorTag: String?) =
        composeRule.onAllNodes(
            hasTestTag(WORD_IMAGE_TAG) and
                (if (ancestorTag == null) hasAnyAncestor(hasTestTag(SENTENCE_BAR_TAG)).not() else hasAnyAncestor(hasTestTag(ancestorTag))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

    private fun sentenceOf(vararg words: Word) = words.mapIndexed { index, word -> SentenceEntry(index, word) }

    @Test
    fun gridTile_withImage_showsImageAndCaption_withoutImageJustText() {
        showWithImages(
            loaded(listOf(word("jablko", "ok.png", 0), word("bez obrázku", null, 1), word("rozbitý odkaz", "chybi.png", 2))),
        )

        assertEquals(1, imageNodesIn(null)) // jen první slovíčko má existující obrázek
        composeRule.onNodeWithText("jablko").assertIsDisplayed()
        composeRule.onNodeWithText("bez obrázku").assertIsDisplayed()
        composeRule.onNodeWithText("rozbitý odkaz").assertIsDisplayed() // chybějící soubor = jen text, žádný pád
    }

    @Test
    fun sentence_showsSmallTilesWithImageAndCaption_byDefault() {
        showWithImages(
            ClientUiState(
                sentence = sentenceOf(word("jablko", "ok.png", 0), word("chci", null, 1)),
                isLoading = false,
            ),
        )

        assertEquals(1, imageNodesIn(SENTENCE_BAR_TAG))
        composeRule.onNodeWithText("jablko").assertIsDisplayed()
        composeRule.onNodeWithText("chci").assertIsDisplayed()
    }

    @Test
    fun hidingCaptions_hidesThemOnlyForWordsWithImage() {
        showWithImages(
            ClientUiState(
                sentence = sentenceOf(word("jablko", "ok.png", 0), word("chci", null, 1), word("rozbitý", "chybi.png", 2)),
                config = AppConfig(hideCaptionsInSentence = true),
                isLoading = false,
            ),
        )

        assertEquals(1, imageNodesIn(SENTENCE_BAR_TAG))
        assertEquals(0, composeRule.onAllNodesWithText("jablko").fetchSemanticsNodes().size) // obrázek stačí
        composeRule.onNodeWithText("chci").assertIsDisplayed() // bez obrázku se titulek neskrývá
        composeRule.onNodeWithText("rozbitý").assertIsDisplayed() // chybějící soubor = jako bez obrázku
    }

    @Test
    fun hidingCaptions_doesNotAffectTheGrid() {
        showWithImages(
            ClientUiState(
                items = listOf(word("jablko", "ok.png")),
                config = AppConfig(hideCaptionsInSentence = true),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithText("jablko").assertIsDisplayed()
    }

    @Test
    fun withoutAnyResolver_wordsStillShowAsText() {
        show(loaded(listOf(word("jablko", "ok.png"))))

        assertEquals(0, composeRule.onAllNodesWithTag(WORD_IMAGE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("jablko").assertIsDisplayed()
    }

    @Test
    fun imageTile_isStillClickable() {
        val jablko = word("jablko", "ok.png")
        showWithImages(loaded(listOf(jablko)))

        composeRule.onNodeWithText("jablko").performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.WordSelected(jablko)), events)
    }

    // --- režim tažení (7.3)

    private val dragWord = Word("w1", "default", "chci", null, 0, 0)

    private fun dragState(mode: AddWordMode, sentenceWords: Int = 0) = ClientUiState(
        items = listOf(dragWord),
        sentence = entries(*Array(sentenceWords) { "věta${it + 1}" }),
        config = AppConfig(addWordMode = mode),
        isLoading = false,
    )

    private fun dragBetween(from: SemanticsNodeInteraction, to: androidx.compose.ui.geometry.Offset) {
        val start = from.fetchSemanticsNode().boundsInRoot.center
        from.performTouchInput { swipe(center, center + (to - start), durationMillis = 400) }
    }

    private fun barCenter() = composeRule.onNodeWithTag(SENTENCE_BAR_TAG).fetchSemanticsNode().boundsInRoot.center

    private fun belowBar(): androidx.compose.ui.geometry.Offset {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return androidx.compose.ui.geometry.Offset(root.center.x, root.bottom - 20f)
    }

    @Test
    fun dragMode_draggingGridWordOntoTheSentenceBar_sendsWordDropped() {
        show(dragState(AddWordMode.DRAG))

        dragBetween(composeRule.onNodeWithText("chci"), barCenter())

        assertEquals(listOf<ClientEvent>(ClientEvent.WordDropped(dragWord)), events)
    }

    @Test
    fun dragMode_draggingGridWordElsewhere_doesNothing() {
        show(dragState(AddWordMode.DRAG))

        dragBetween(composeRule.onNodeWithText("chci"), belowBar())

        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun tapMode_draggingGridWordOntoTheBar_doesNothing() {
        show(dragState(AddWordMode.TAP))

        dragBetween(composeRule.onNodeWithText("chci"), barCenter())

        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun dragMode_tapOnGridWord_stillSendsWordSelected_andViewModelDecides() {
        show(dragState(AddWordMode.DRAG))

        composeRule.onNodeWithText("chci").performClick()

        assertEquals(listOf<ClientEvent>(ClientEvent.WordSelected(dragWord)), events)
    }

    @Test
    fun dragMode_whileDragging_aTileFollowsTheFinger_andDisappearsOnRelease() {
        show(dragState(AddWordMode.DRAG))
        val tile = composeRule.onNodeWithText("chci")

        tile.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 40f))
            moveBy(androidx.compose.ui.geometry.Offset(40f, 40f))
        }
        composeRule.onNodeWithTag(DRAGGED_WORD_TAG).assertIsDisplayed()

        composeRule.onAllNodesWithText("chci")[0].performTouchInput { up() } // [0] je dlaždice v mřížce, [1] ta tažená
        composeRule.onAllNodesWithTag(DRAGGED_WORD_TAG).assertCountEquals(0)
    }

    @Test
    fun dragMode_draggingSentenceWordAwayFromTheBar_removesIt() {
        show(dragState(AddWordMode.DRAG, sentenceWords = 2))

        dragBetween(composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG)[1], belowBar())

        assertEquals(listOf<ClientEvent>(ClientEvent.RemoveFromSentence(1)), events)
    }

    @Test
    fun tapMode_draggingSentenceWordAway_removesNothing() {
        show(dragState(AddWordMode.TAP, sentenceWords = 2))

        dragBetween(composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG)[1], belowBar())

        assertEquals(emptyList<ClientEvent>(), events)
    }

    @Test
    fun dragMode_holdingSentenceWordStillRemovesIt() {
        show(dragState(AddWordMode.DRAG, sentenceWords = 1))
        composeRule.mainClock.autoAdvance = false

        composeRule.onAllNodesWithTag(SENTENCE_WORD_TAG)[0].performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_200)

        assertEquals(listOf<ClientEvent>(ClientEvent.RemoveFromSentence(0)), events)
    }

    @Test
    fun dragMode_emptySentenceHintExplainsDragging() {
        show(dragState(AddWordMode.DRAG))
        composeRule.onNodeWithText("Přetažením slova sem ho přidáte do věty.").assertIsDisplayed()
    }

    @Test
    fun foldersAreNeverDraggable() {
        val folder = Folder("f1", "default", "Jídlo", null, 0, 0)
        show(ClientUiState(items = listOf(folder), config = AppConfig(addWordMode = AddWordMode.DRAG), isLoading = false))

        dragBetween(composeRule.onNodeWithText("Jídlo"), barCenter())

        assertEquals(emptyList<ClientEvent>(), events)
    }
}
