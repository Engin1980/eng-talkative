package cz.talkative.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.drawing.CacheDrawingStorage
import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteItemsUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.DiscardAudioUseCase
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.MoveItemInOrderUseCase
import cz.talkative.app.domain.usecase.MoveItemsUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveAudioUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
import cz.talkative.app.domain.usecase.SortAlphabeticallyUseCase
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.testing.NoSoftKeyboardRule
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.admin.AdminViewModel
import cz.talkative.app.ui.common.LocalImageResolver
import cz.talkative.app.ui.common.WORD_IMAGE_TAG
import cz.talkative.app.ui.draw.DRAW_CANVAS_TAG
import cz.talkative.app.ui.draw.DrawRoute
import cz.talkative.app.ui.draw.DrawViewModel
import cz.talkative.app.ui.navigation.Routes
import cz.talkative.app.ui.navigation.TalkativeNavHost
import cz.talkative.app.ui.theme.TalkativeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Celé napojení kreslení na editaci slovíčka: administrace → kreslení → zpět do editace → uložení slovíčka.
 * Skutečné jsou databáze, úložiště obrázků, view modely, ukládání kresby do cache a navigace.
 */
@RunWith(AndroidJUnit4::class)
class DrawingFlowTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imagesDir get() = File(context.filesDir, "images")
    private val drawingsDir get() = File(context.cacheDir, "drawings")
    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository
    private lateinit var imageStore: FileImageStore
    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        imagesDir.deleteRecursively()
        drawingsDir.deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        imageStore = FileImageStore(context)
        val audioStore = FakeAudioStore()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { System.currentTimeMillis() }, imageStore, audioStore)
        val config = FakeConfigRepository()
        val adminViewModel = AdminViewModel(
            ObserveFolderItemsUseCase(repository),
            GetFolderPathUseCase(repository),
            SaveFolderUseCase(repository),
            SaveWordUseCase(repository),
            CountFolderContentsUseCase(repository),
            DeleteFolderUseCase(repository),
            DeleteWordUseCase(repository),
            DeleteItemsUseCase(repository),
            MoveItemsUseCase(repository),
            MoveItemInOrderUseCase(repository),
            SortAlphabeticallyUseCase(repository, config),
            SaveImageUseCase(imageStore),
            DiscardImageUseCase(imageStore),
            SaveAudioUseCase(audioStore),
            DiscardAudioUseCase(audioStore),
            FakeAudioPlayer(),
            cz.talkative.app.testing.FakeAudioRecorder(),
            cz.talkative.app.domain.usecase.SaveRecordingUseCase(audioStore),
        )
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            TalkativeTheme {
                CompositionLocalProvider(LocalImageResolver provides { imageStore.resolve(it) }) {
                    TalkativeNavHost(
                        navController = navController,
                        client = {},
                        admin = { navigation ->
                            AdminRoute(
                                onBackToClient = navigation.backToClient,
                                onOpenConfig = navigation.openConfig,
                                onOpenExchange = navigation.openExchange,
                                onOpenAbout = navigation.openAbout,
                                onOpenArasaac = navigation.openArasaac,
                                onOpenDraw = navigation.openDraw,
                                pickedImageUri = navigation.pickedImageUri,
                                onPickedImageConsumed = navigation.onPickedImageConsumed,
                                viewModel = adminViewModel,
                            )
                        },
                        draw = { onSaved, close ->
                            val viewModel = remember { DrawViewModel(CacheDrawingStorage(context.cacheDir)) }
                            DrawRoute(onSaved = onSaved, onClose = close, viewModel = viewModel)
                        },
                    )
                }
            }
        }
        composeRule.runOnUiThread { navController.navigate(Routes.ADMIN) }
    }

    @After
    fun tearDown() {
        database.close()
        imagesDir.deleteRecursively()
        drawingsDir.deleteRecursively()
    }

    private fun route() = navController.currentBackStackEntry?.destination?.route

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForRoute(route: String) = composeRule.waitUntil(5_000) { route() == route }

    private fun allWords() = runBlocking { repository.snapshot().words }

    private fun openDrawingForNewWord(title: String) {
        waitForText("Nové slovíčko")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Titulek").performTextInput(title)
        composeRule.onNodeWithText("Nakreslit").performScrollTo().performClick()
        waitForRoute(Routes.DRAW)
    }

    private fun tapCanvasCenter() {
        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    @Test
    fun drawnIcon_becomesTheWordsImage_andIsStoredLikeAnyOther() {
        openDrawingForNewWord("dům")
        tapCanvasCenter()

        composeRule.onNodeWithText("Uložit").performClick()
        waitForRoute(Routes.ADMIN)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(WORD_IMAGE_TAG).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("dům").assertIsDisplayed() // rozepsaný titulek zůstal
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { allWords().isNotEmpty() }

        val word = allWords().single()
        val stored = imageStore.resolve(word.imageFile!!)
        assertNotNull(stored)
        val bitmap = BitmapFactory.decodeFile(stored!!.absolutePath)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertEquals(Color.WHITE, bitmap.getPixel(5, 5)) // pozadí je bílé, nic neprůhledné
        assertTrue("střed má být tmavý (tečka)", Color.red(bitmap.getPixel(256, 256)) < 80)
        assertEquals(1, imagesDir.listFiles()!!.size)
    }

    @Test
    fun closingAnUnchangedCanvas_returnsWithoutAsking_andChangesNothing() {
        openDrawingForNewWord("dům")

        composeRule.onNodeWithText("Zavřít").performClick()

        waitForRoute(Routes.ADMIN)
        composeRule.onNodeWithText("dům").assertIsDisplayed()
        assertTrue(imagesDir.listFiles().isNullOrEmpty())
        assertTrue(drawingsDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun discardingADrawing_leavesNoTraceAnywhere() {
        openDrawingForNewWord("dům")
        tapCanvasCenter()

        composeRule.onNodeWithText("Zavřít").performClick()
        composeRule.onNodeWithText("Zahodit kresbu?").assertIsDisplayed()
        assertEquals(Routes.DRAW, route()) // dokud se nepotvrdí, kreslení běží dál
        composeRule.onNodeWithText("Zahodit").performClick()

        waitForRoute(Routes.ADMIN)
        assertTrue(imagesDir.listFiles().isNullOrEmpty())
        assertTrue("v cache/drawings nemá nic zůstat", drawingsDir.listFiles().isNullOrEmpty())
        assertEquals(0, composeRule.onAllNodesWithTag(WORD_IMAGE_TAG).fetchSemanticsNodes().size)
        assertTrue(allWords().isEmpty())
    }

    @Test
    fun replacingAnImageWithADrawing_thenCancellingTheEdit_leavesNoOrphanFile() {
        val original = runBlocking {
            val source = File(context.cacheDir, "orig-${System.nanoTime()}.png")
            source.outputStream().use { Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val saved = imageStore.saveFromUri(Uri.fromFile(source).toString()) as ImageSaveResult.Saved
            source.delete()
            saved.fileName
        }
        runBlocking { repository.createWord(null, "dům", imageFile = original) }
        waitForText("dům")
        composeRule.onNodeWithContentDescription("Upravit dům").performClick()
        composeRule.onNodeWithText("Nakreslit").performScrollTo().performClick()
        waitForRoute(Routes.DRAW)
        tapCanvasCenter()
        composeRule.onNodeWithText("Uložit").performClick()
        waitForRoute(Routes.ADMIN)
        composeRule.waitUntil(5_000) { imagesDir.listFiles()!!.size == 2 } // původní a nová (zatím nepřiřazená) kresba

        composeRule.onNodeWithText("Zrušit").performClick()
        composeRule.waitUntil(5_000) { imagesDir.listFiles()!!.size == 1 }

        assertEquals(listOf(original), imagesDir.listFiles()!!.map { it.name })
        assertEquals(original, allWords().single().imageFile)
    }

    @Test
    fun drawButton_isOnlyOfferedForWords() {
        waitForText("Nová složka")
        composeRule.onNodeWithText("Nová složka").performClick()
        composeRule.onNodeWithText("Titulek").assertIsDisplayed()

        assertEquals(0, composeRule.onAllNodesWithText("Nakreslit").fetchSemanticsNodes().size)
    }
}
