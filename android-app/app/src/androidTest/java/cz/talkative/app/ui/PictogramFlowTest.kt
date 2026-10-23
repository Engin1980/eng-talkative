package cz.talkative.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.image.FileImageStore
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomDictionaryRepository
import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramDownloadResult
import cz.talkative.app.domain.pictogram.PictogramDownloader
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.domain.pictogram.PictogramSearchResult
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
import cz.talkative.app.testing.FakePictogramSearch
import cz.talkative.app.testing.NoSoftKeyboardRule
import cz.talkative.app.ui.admin.AdminRoute
import cz.talkative.app.ui.admin.AdminViewModel
import cz.talkative.app.ui.arasaac.ARASAAC_QUERY_ARG
import cz.talkative.app.ui.arasaac.ARASAAC_STATUS_TAG
import cz.talkative.app.ui.arasaac.ArasaacRoute
import cz.talkative.app.ui.arasaac.ArasaacViewModel
import cz.talkative.app.ui.arasaac.pictogramTag
import cz.talkative.app.ui.common.LocalImageResolver
import cz.talkative.app.ui.common.WORD_IMAGE_TAG
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
 * Celé napojení piktogramů na editaci slovíčka: administrace → výběr piktogramu → zpět do editace → uložení.
 * Skutečné jsou databáze, úložiště obrázků, oba view modely a navigace; síť je nahrazená.
 */
@RunWith(AndroidJUnit4::class)
class PictogramFlowTest {

    @get:Rule(order = 0)
    val noSoftKeyboard = NoSoftKeyboardRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val imagesDir get() = File(context.filesDir, "images")
    private lateinit var database: TalkativeDatabase
    private lateinit var repository: RoomDictionaryRepository
    private lateinit var imageStore: FileImageStore
    private lateinit var adminViewModel: AdminViewModel
    private lateinit var navController: TestNavHostController
    private val search = FakePictogramSearch()
    private val downloads = mutableListOf<Int>()
    private var downloadFailure: PictogramError? = null
    private val house = Pictogram(7, "dům", "http://invalid.test/7_300.png")

    /** Stahování nahrazené zápisem průhledného PNG (kruh na průhledném pozadí) do `cache/pictograms`. */
    private val downloader = object : PictogramDownloader {
        override suspend fun download(id: Int): PictogramDownloadResult {
            downloads += id
            downloadFailure?.let { return PictogramDownloadResult.Failed(it) }
            val file = File(File(context.cacheDir, "pictograms").also { it.mkdirs() }, "pictogram-$id.png")
            val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawCircle(250f, 250f, 100f, android.graphics.Paint().apply { color = Color.RED })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return PictogramDownloadResult.Downloaded(file)
        }
    }

    @Before
    fun setUp() {
        imagesDir.deleteRecursively()
        File(context.cacheDir, "pictograms").deleteRecursively()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
        imageStore = FileImageStore(context)
        val audioStore = FakeAudioStore()
        repository = RoomDictionaryRepository(database.dictionaryDao(), { System.currentTimeMillis() }, imageStore, audioStore)
        val config = FakeConfigRepository()
        adminViewModel = AdminViewModel(
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
        search.result = PictogramSearchResult.Found(listOf(house))
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            TalkativeTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalImageResolver provides { imageStore.resolve(it) }) {
                    TalkativeNavHost(
                        navController = navController,
                        client = {},
                        admin = { navigation ->
                            AdminRoute(
                                onBackToClient = navigation.backToClient,
                                onOpenConfig = navigation.openConfig,
                                onOpenExchange = navigation.openExchange,
                                onOpenAbout = navigation.openAbout,
                                onOpenSync = {},
                                onOpenArasaac = navigation.openArasaac,
                                onOpenDraw = navigation.openDraw,
                                pickedImageUri = navigation.pickedImageUri,
                                onPickedImageConsumed = navigation.onPickedImageConsumed,
                                viewModel = adminViewModel,
                            )
                        },
                        arasaac = { onPicked, openAbout, back ->
                            val query = navController.currentBackStackEntry?.arguments?.getString(ARASAAC_QUERY_ARG).orEmpty()
                            val viewModel = remember { ArasaacViewModel(SavedStateHandle(mapOf(ARASAAC_QUERY_ARG to query)), search, downloader) }
                            ArasaacRoute(onPicked = onPicked, onOpenAbout = openAbout, onBack = back, viewModel = viewModel)
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
        File(context.cacheDir, "pictograms").deleteRecursively()
    }

    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun route() = navController.currentBackStackEntry?.destination?.route

    private fun openPictogramPickerForNewWord(title: String) {
        waitForText("Nové slovíčko")
        composeRule.onNodeWithText("Nové slovíčko").performClick()
        composeRule.onNodeWithText("Titulek").performTextInput(title)
        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { route() == Routes.ARASAAC }
    }

    private fun search() {
        composeRule.onNodeWithText("Hledat").performClick()
        waitForTag(pictogramTag(7))
    }

    private fun allWords() = runBlocking { repository.snapshot().words }

    @Test
    fun pictogramPickedFromTheEditor_becomesTheWordsImage_andKeepsTransparency() {
        openPictogramPickerForNewWord("dům")
        waitForText("Hledané slovo") // obrazovka výběru, hledané slovo je předvyplněné titulkem
        composeRule.onNodeWithText("dům").assertIsDisplayed()

        search()
        composeRule.onNodeWithTag(pictogramTag(7)).performClick()

        // zpět v editaci: titulek zůstal a obrázek je zpracovaný
        composeRule.waitUntil(5_000) { route() == Routes.ADMIN }
        waitForTag(WORD_IMAGE_TAG)
        composeRule.onNodeWithText("dům").assertIsDisplayed()
        composeRule.onNodeWithText("Uložit").performClick()
        composeRule.waitUntil(5_000) { allWords().isNotEmpty() }

        assertEquals(listOf(7), downloads)
        val word = allWords().single()
        assertEquals("dům", word.title)
        val stored = imageStore.resolve(word.imageFile!!)
        assertNotNull(stored)
        val bitmap = BitmapFactory.decodeFile(stored!!.absolutePath)
        assertTrue("PNG s průhledností: ${word.imageFile}", word.imageFile!!.endsWith(".png"))
        assertEquals(0, Color.alpha(bitmap.getPixel(5, 5)))
        assertEquals(1, imagesDir.listFiles()!!.size)
    }

    @Test
    fun replacingAnImage_thenCancelling_leavesNoOrphanFile() {
        val original = runBlocking {
            val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
            val source = File(context.cacheDir, "orig-${System.nanoTime()}.png")
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val saved = imageStore.saveFromUri(android.net.Uri.fromFile(source).toString())
            source.delete()
            (saved as cz.talkative.app.domain.image.ImageSaveResult.Saved).fileName
        }
        runBlocking { repository.createWord(null, "dům", imageFile = original) }
        waitForText("dům")
        composeRule.onNodeWithContentDescription("Upravit dům").performClick()
        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { route() == Routes.ARASAAC }
        search()
        composeRule.onNodeWithTag(pictogramTag(7)).performClick()
        composeRule.waitUntil(5_000) { route() == Routes.ADMIN }
        composeRule.waitUntil(5_000) { imagesDir.listFiles()!!.size == 2 } // původní a právě uložený (zatím nepřiřazený)

        composeRule.onNodeWithText("Zrušit").performClick()
        composeRule.waitUntil(5_000) { imagesDir.listFiles()!!.size == 1 }

        assertEquals(listOf(original), imagesDir.listFiles()!!.map { it.name })
        assertEquals(original, allWords().single().imageFile)
    }

    @Test
    fun withoutNetwork_theMessageIsShown_andTheEditorStaysUsable() {
        search.result = PictogramSearchResult.Failed(PictogramError.NO_NETWORK)
        openPictogramPickerForNewWord("dům")

        composeRule.onNodeWithText("Hledat").performClick()
        waitForTag(ARASAAC_STATUS_TAG)
        composeRule.onNodeWithText("Chybí připojení k internetu. Zapněte Wi-Fi nebo mobilní data a zkuste to znovu.").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět").performClick()
        composeRule.waitUntil(5_000) { route() == Routes.ADMIN }

        composeRule.onNodeWithText("dům").assertIsDisplayed() // rozepsaný titulek zůstal
        composeRule.onNodeWithText("Vyfotit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Najít piktogram (ARASAAC)").performScrollTo().assertIsDisplayed()
        assertTrue(imagesDir.listFiles().isNullOrEmpty())
        assertTrue(downloads.isEmpty())
    }

    @Test
    fun downloadFailure_keepsTheUserOnThePicker_andNothingIsStored() {
        downloadFailure = PictogramError.TIMEOUT
        openPictogramPickerForNewWord("dům")
        search()

        composeRule.onNodeWithTag(pictogramTag(7)).performClick()

        waitForText("Server ARASAAC neodpověděl včas. Zkuste to znovu.")
        assertEquals(Routes.ARASAAC, route())
        assertTrue(imagesDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun pictogramButton_isOnlyOfferedForWords_notForFolders() {
        waitForText("Nová složka")
        composeRule.onNodeWithText("Nová složka").performClick()
        composeRule.onNodeWithText("Titulek").assertIsDisplayed()

        assertEquals(0, composeRule.onAllNodesWithText("Najít piktogram (ARASAAC)").fetchSemanticsNodes().size)
    }
}
