package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteItemsUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.MoveItemInOrderUseCase
import cz.talkative.app.domain.usecase.MoveItemsUseCase
import cz.talkative.app.domain.usecase.SortAlphabeticallyUseCase
import cz.talkative.app.testing.FakeConfigRepository
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.testing.FakeAudioPlayer
import cz.talkative.app.testing.FakeAudioStore
import cz.talkative.app.testing.FakeImageStore
import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.usecase.DiscardAudioUseCase
import cz.talkative.app.domain.usecase.SaveAudioUseCase
import kotlinx.coroutines.CompletableDeferred
import cz.talkative.app.domain.usecase.GetFolderPathUseCase
import cz.talkative.app.domain.usecase.ObserveFolderItemsUseCase
import cz.talkative.app.domain.usecase.SaveFolderUseCase
import cz.talkative.app.domain.usecase.SaveWordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val repository = FakeDictionaryRepository()
    private val imageStore = FakeImageStore()
    private val audioStore = FakeAudioStore()
    private val audioPlayer = FakeAudioPlayer()
    private val config = FakeConfigRepository()
    private lateinit var viewModel: AdminViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AdminViewModel(
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
            audioPlayer,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** `stateIn(WhileSubscribed)` pracuje jen při odběru, proto se stav v testu odebírá na pozadí. */
    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.subscribe() {
        viewModel.uiState.launchIn(backgroundScope)
    }

    private fun create(kind: AdminEvent, title: String) {
        viewModel.onEvent(kind)
        viewModel.onEvent(AdminEvent.TitleChanged(title))
        viewModel.onEvent(AdminEvent.ConfirmEdit)
    }

    private fun titles() = viewModel.uiState.value.items.map { it.title }

    @Test
    fun startsInRootWithEmptyContent() = runVmTest {
        subscribe()

        val state = viewModel.uiState.value
        assertTrue(state.path.isEmpty())
        assertTrue(state.items.isEmpty())
        assertEquals(false, state.isLoading)
    }

    @Test
    fun createdItemsAppearInOrderOfCreation() = runVmTest {
        subscribe()

        create(AdminEvent.AddFolder, "Jídlo")
        create(AdminEvent.AddWord, "Chci")
        create(AdminEvent.AddFolder, "Pití")

        assertEquals(listOf("Jídlo", "Chci", "Pití"), titles())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun emptyTitle_keepsDialogOpenWithErrorAndCreatesNothing() = runVmTest {
        subscribe()

        create(AdminEvent.AddWord, "   ")

        val dialog = viewModel.uiState.value.dialog as AdminDialog.Edit
        assertTrue(dialog.showEmptyError)
        assertTrue(titles().isEmpty())

        viewModel.onEvent(AdminEvent.TitleChanged("Ano"))
        assertEquals(false, (viewModel.uiState.value.dialog as AdminDialog.Edit).showEmptyError)
    }

    @Test
    fun titlesNeedNotBeUnique() = runVmTest {
        subscribe()

        create(AdminEvent.AddWord, "Ahoj")
        create(AdminEvent.AddWord, "Ahoj")

        assertEquals(listOf("Ahoj", "Ahoj"), titles())
    }

    @Test
    fun navigatesDownAndUp_pathFollowsPosition() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        val a = viewModel.uiState.value.items.single()
        viewModel.onEvent(AdminEvent.GoToFolder(a.id))
        create(AdminEvent.AddFolder, "B")
        val b = viewModel.uiState.value.items.single()
        viewModel.onEvent(AdminEvent.GoToFolder(b.id))

        assertEquals(listOf("A", "B"), viewModel.uiState.value.path.map { it.title })

        viewModel.onEvent(AdminEvent.NavigateUp)
        assertEquals(listOf("A"), viewModel.uiState.value.path.map { it.title })
        assertEquals(listOf("B"), titles())

        viewModel.onEvent(AdminEvent.NavigateUp)
        assertTrue(viewModel.uiState.value.path.isEmpty())
        assertEquals(listOf("A"), titles())

        viewModel.onEvent(AdminEvent.NavigateUp) // v kořeni zůstane v kořeni
        assertTrue(viewModel.uiState.value.path.isEmpty())
    }

    @Test
    fun itemsAreCreatedInCurrentFolder() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        viewModel.onEvent(AdminEvent.GoToFolder(viewModel.uiState.value.items.single().id))

        create(AdminEvent.AddWord, "uvnitř")

        assertEquals(listOf("uvnitř"), titles())
        viewModel.onEvent(AdminEvent.GoToFolder(null))
        assertEquals(listOf("A"), titles())
    }

    @Test
    fun editRenamesItem() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "Stary")
        val word = viewModel.uiState.value.items.single()

        viewModel.onEvent(AdminEvent.EditItem(word))
        assertEquals("Stary", (viewModel.uiState.value.dialog as AdminDialog.Edit).title)
        viewModel.onEvent(AdminEvent.TitleChanged("Novy"))
        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertEquals(listOf("Novy"), titles())
    }

    @Test
    fun deleteFolderWithContent_asksWithCountAndDeletesSubtreeOnConfirm() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        viewModel.onEvent(AdminEvent.GoToFolder(viewModel.uiState.value.items.single().id))
        create(AdminEvent.AddWord, "w1")
        create(AdminEvent.AddFolder, "B")
        viewModel.onEvent(AdminEvent.GoToFolder(viewModel.uiState.value.items.last().id))
        create(AdminEvent.AddWord, "w2")
        viewModel.onEvent(AdminEvent.GoToFolder(null))
        val a = viewModel.uiState.value.items.single()

        viewModel.onEvent(AdminEvent.DeleteItem(a))

        val dialog = viewModel.uiState.value.dialog as AdminDialog.ConfirmDelete
        assertEquals(3, dialog.contentCount) // w1, B, w2
        assertEquals(listOf("A"), titles())

        viewModel.onEvent(AdminEvent.ConfirmDelete)

        assertTrue(titles().isEmpty())
        assertNull(viewModel.uiState.value.dialog)
        assertTrue(repository.allFolders.isEmpty())
        assertTrue(repository.allWords.isEmpty())
    }

    @Test
    fun cancelledDelete_changesNothing() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        val a = viewModel.uiState.value.items.single() as Folder

        viewModel.onEvent(AdminEvent.DeleteItem(a))
        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(listOf("A"), titles())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun deleteWord_removesOnlyThatWord() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "keep")
        create(AdminEvent.AddWord, "drop")
        val drop = viewModel.uiState.value.items.last()

        viewModel.onEvent(AdminEvent.DeleteItem(drop))
        assertEquals(0, (viewModel.uiState.value.dialog as AdminDialog.ConfirmDelete).contentCount)
        viewModel.onEvent(AdminEvent.ConfirmDelete)

        assertEquals(listOf("keep"), titles())
    }

    // ---- obrázky (krok 4.2)

    private fun editDialog() = viewModel.uiState.value.dialog as AdminDialog.Edit

    private fun imageOf(title: String) =
        (viewModel.uiState.value.items.first { it.title == title } as cz.talkative.app.domain.model.Word).imageFile

    @Test
    fun pickedImage_isShownInDialog_andAssignedToNewWordOnSave() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("jablko"))

        viewModel.onEvent(AdminEvent.ImagePicked("content://foto"))

        val image = editDialog().imageFile
        assertEquals(false, editDialog().isProcessingImage)
        assertTrue(image != null && image in imageStore.files)

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertEquals(image, imageOf("jablko"))
        assertEquals(setOf(image), imageStore.files) // soubor zůstal, je přiřazen slovíčku
    }

    @Test
    fun cancellingDialogAfterPick_discardsTheNewFile() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.ImagePicked("content://foto"))
        assertEquals(1, imageStore.files.size)

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertTrue(imageStore.files.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun pickingAnotherImage_discardsThePreviousPendingOne() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.ImagePicked("content://a"))
        val first = editDialog().imageFile

        viewModel.onEvent(AdminEvent.ImagePicked("content://b"))

        val second = editDialog().imageFile
        assertTrue(first != second)
        assertEquals(setOf(second), imageStore.files)
    }

    @Test
    fun editingWordWithImage_removeThenSave_clearsImageOnWord() = runVmTest {
        val original = imageStore.add("puvodni.jpg")
        repository.createWord(null, "slovo", original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))
        assertEquals(original, editDialog().imageFile)

        viewModel.onEvent(AdminEvent.RemoveImage)
        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertNull(imageOf("slovo"))
        // původní soubor maže repozitář (viz DictionaryRepositoryTest), view model na něj nesahá
        assertEquals(setOf(original), imageStore.files)
    }

    @Test
    fun editingWordWithImage_removeThenCancel_keepsEverything() = runVmTest {
        val original = imageStore.add("puvodni.jpg")
        repository.createWord(null, "slovo", original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))

        viewModel.onEvent(AdminEvent.RemoveImage)
        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(original, imageOf("slovo"))
        assertEquals(setOf(original), imageStore.files)
    }

    @Test
    fun replacingImageOfExistingWord_cancelDiscardsOnlyTheNewOne() = runVmTest {
        val original = imageStore.add("puvodni.jpg")
        repository.createWord(null, "slovo", original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))

        viewModel.onEvent(AdminEvent.ImagePicked("content://nova"))
        assertEquals(2, imageStore.files.size)
        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(setOf(original), imageStore.files)
        assertEquals(original, imageOf("slovo"))
    }

    @Test
    fun replacingImageOfExistingWord_saveAssignsNewOne() = runVmTest {
        val original = imageStore.add("puvodni.jpg")
        repository.createWord(null, "slovo", original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))

        viewModel.onEvent(AdminEvent.ImagePicked("content://nova"))
        val picked = editDialog().imageFile
        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertEquals(picked, imageOf("slovo"))
    }

    @Test
    fun unreadableImage_showsProblem_andKeepsDialogUsable() = runVmTest {
        subscribe()
        imageStore.failWith = ImageError.UNREADABLE
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))

        viewModel.onEvent(AdminEvent.ImagePicked("content://rozbite"))

        assertEquals(ImageProblem.UNREADABLE, editDialog().imageProblem)
        assertNull(editDialog().imageFile)
        assertEquals(false, editDialog().isProcessingImage)
        assertTrue(imageStore.files.isEmpty())

        viewModel.onEvent(AdminEvent.DismissImageProblem)
        assertNull(editDialog().imageProblem)
        viewModel.onEvent(AdminEvent.ConfirmEdit) // slovíčko jde uložit i bez obrázku
        assertNull(imageOf("slovo"))
    }

    @Test
    fun fullStorage_isReportedAsStorageFull() = runVmTest {
        subscribe()
        imageStore.failWith = ImageError.STORAGE_FULL
        viewModel.onEvent(AdminEvent.AddWord)

        viewModel.onEvent(AdminEvent.ImagePicked("content://velke"))

        assertEquals(ImageProblem.STORAGE_FULL, editDialog().imageProblem)
    }

    @Test
    fun launcherFailures_areShownAsProblems() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)

        viewModel.onEvent(AdminEvent.ImageSelectionFailed(ImageProblem.NO_CAMERA))

        assertEquals(ImageProblem.NO_CAMERA, editDialog().imageProblem)
    }

    @Test
    fun saveIsIgnoredWhileImageIsBeingProcessed() = runVmTest {
        subscribe()
        imageStore.gate = CompletableDeferred()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))
        viewModel.onEvent(AdminEvent.ImagePicked("content://pomale"))
        assertTrue(editDialog().isProcessingImage)

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertNotNull(viewModel.uiState.value.dialog)

        imageStore.gate!!.complete(Unit)
        assertEquals(false, editDialog().isProcessingImage)
    }

    @Test
    fun imageFinishingAfterDialogWasCancelled_isDiscarded() = runVmTest {
        subscribe()
        imageStore.gate = CompletableDeferred()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.ImagePicked("content://pomale"))

        viewModel.onEvent(AdminEvent.DismissDialog)
        imageStore.gate!!.complete(Unit)

        assertTrue(imageStore.files.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun imagesAreIgnoredForFolders() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddFolder)

        viewModel.onEvent(AdminEvent.ImagePicked("content://foto"))

        assertTrue(imageStore.files.isEmpty())
        assertNull(editDialog().imageFile)
    }

    // ---- jiný text pro čtení (krok 5.1)

    private fun wordOf(title: String) = viewModel.uiState.value.items.first { it.title == title } as cz.talkative.app.domain.model.Word

    @Test
    fun newWord_savesAlternateText_trimmed() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("KD"))
        viewModel.onEvent(AdminEvent.AlternateTextChanged("  ká dé "))

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertEquals("ká dé", wordOf("KD").alternateText)
    }

    @Test
    fun blankAlternateText_isStoredAsNone() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))
        viewModel.onEvent(AdminEvent.AlternateTextChanged("   "))

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertNull(wordOf("slovo").alternateText)
    }

    @Test
    fun editingWord_showsStoredAlternateText_andClearingRemovesIt() = runVmTest {
        repository.createWord(null, "KD", alternateText = "ká dé")
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))
        assertEquals("ká dé", editDialog().alternateText)

        viewModel.onEvent(AdminEvent.AlternateTextChanged(""))
        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertNull(wordOf("KD").alternateText)
    }

    @Test
    fun cancellingEdit_keepsAlternateText() = runVmTest {
        repository.createWord(null, "KD", alternateText = "ká dé")
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))
        viewModel.onEvent(AdminEvent.AlternateTextChanged("jiné"))

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals("ká dé", wordOf("KD").alternateText)
    }

    // ---- vlastní MP3 (krok 5.2)

    private fun audioOf(title: String) = wordOf(title).audioFile

    private suspend fun pickAudio(title: String = "slovo"): String {
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged(title))
        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk"))
        return editDialog().audioFile!!
    }

    @Test
    fun pickedAudio_isShownInDialog_andAssignedToNewWordOnSave() = runVmTest {
        subscribe()
        val audio = pickAudio()
        assertEquals(false, editDialog().isProcessingAudio)
        assertEquals(setOf(audio), audioStore.files)

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertEquals(audio, audioOf("slovo"))
        assertEquals(setOf(audio), audioStore.files)
    }

    @Test
    fun wordWithAudio_doesNotKeepAlternateText() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))
        viewModel.onEvent(AdminEvent.AlternateTextChanged("jiný text"))
        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk"))

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertNull(wordOf("slovo").alternateText)
        assertNotNull(audioOf("slovo"))
    }

    @Test
    fun removingAudioInNewDialog_deletesFile_andAlternateTextWorksAgain() = runVmTest {
        subscribe()
        pickAudio()

        viewModel.onEvent(AdminEvent.RemoveAudio)

        assertNull(editDialog().audioFile)
        assertTrue(audioStore.files.isEmpty())
        viewModel.onEvent(AdminEvent.AlternateTextChanged("text"))
        viewModel.onEvent(AdminEvent.ConfirmEdit)
        assertEquals("text", wordOf("slovo").alternateText)
        assertNull(audioOf("slovo"))
    }

    @Test
    fun cancellingDialogAfterPickingAudio_discardsTheNewFile() = runVmTest {
        subscribe()
        pickAudio()

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertTrue(audioStore.files.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun pickingAnotherAudio_discardsThePreviousPendingOne() = runVmTest {
        subscribe()
        val first = pickAudio()

        viewModel.onEvent(AdminEvent.AudioPicked("content://druhy"))

        val second = editDialog().audioFile
        assertTrue(first != second)
        assertEquals(setOf(second), audioStore.files)
    }

    @Test
    fun editingWordWithAudio_removeThenSave_clearsAudioOnWord() = runVmTest {
        val original = audioStore.add("puvodni.mp3")
        repository.createWord(null, "slovo", audioFile = original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))
        assertEquals(original, editDialog().audioFile)

        viewModel.onEvent(AdminEvent.RemoveAudio)
        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertNull(audioOf("slovo"))
        // původní soubor maže repozitář (viz DictionaryRepositoryTest), view model na něj nesahá
        assertEquals(setOf(original), audioStore.files)
    }

    @Test
    fun editingWordWithAudio_removeThenCancel_keepsEverything() = runVmTest {
        val original = audioStore.add("puvodni.mp3")
        repository.createWord(null, "slovo", audioFile = original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))

        viewModel.onEvent(AdminEvent.RemoveAudio)
        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(original, audioOf("slovo"))
        assertEquals(setOf(original), audioStore.files)
    }

    @Test
    fun replacingAudioOfExistingWord_cancelDiscardsOnlyTheNewOne() = runVmTest {
        val original = audioStore.add("puvodni.mp3")
        repository.createWord(null, "slovo", audioFile = original)
        subscribe()
        viewModel.onEvent(AdminEvent.EditItem(viewModel.uiState.value.items.single()))

        viewModel.onEvent(AdminEvent.AudioPicked("content://nove"))
        assertEquals(2, audioStore.files.size)
        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(setOf(original), audioStore.files)
        assertEquals(original, audioOf("slovo"))
    }

    @Test
    fun rejectedAudio_showsProblem_savesNothing_andDialogStaysUsable() = runVmTest {
        subscribe()
        val expected = mapOf(
            AudioError.TOO_LONG to AudioProblem.TOO_LONG,
            AudioError.TOO_LARGE to AudioProblem.TOO_LARGE,
            AudioError.UNSUPPORTED_FORMAT to AudioProblem.UNSUPPORTED_FORMAT,
            AudioError.UNREADABLE to AudioProblem.UNREADABLE,
            AudioError.STORAGE_FULL to AudioProblem.STORAGE_FULL,
        )
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))

        for ((error, problem) in expected) {
            audioStore.failWith = error
            viewModel.onEvent(AdminEvent.AudioPicked("content://spatne"))
            assertEquals(problem, editDialog().audioProblem)
            assertNull(editDialog().audioFile)
            assertEquals(false, editDialog().isProcessingAudio)
            assertTrue(audioStore.files.isEmpty())
            viewModel.onEvent(AdminEvent.DismissAudioProblem)
            assertNull(editDialog().audioProblem)
        }

        viewModel.onEvent(AdminEvent.ConfirmEdit) // slovíčko jde uložit i bez zvuku
        assertNull(audioOf("slovo"))
    }

    @Test
    fun saveIsIgnoredWhileAudioIsBeingChecked() = runVmTest {
        subscribe()
        audioStore.gate = CompletableDeferred()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.TitleChanged("slovo"))
        viewModel.onEvent(AdminEvent.AudioPicked("content://pomale"))
        assertTrue(editDialog().isProcessingAudio)

        viewModel.onEvent(AdminEvent.ConfirmEdit)

        assertTrue(viewModel.uiState.value.items.isEmpty())
        audioStore.gate!!.complete(Unit)
        assertEquals(false, editDialog().isProcessingAudio)
    }

    @Test
    fun audioFinishingAfterDialogWasCancelled_isDiscarded() = runVmTest {
        subscribe()
        audioStore.gate = CompletableDeferred()
        viewModel.onEvent(AdminEvent.AddWord)
        viewModel.onEvent(AdminEvent.AudioPicked("content://pomale"))

        viewModel.onEvent(AdminEvent.DismissDialog)
        audioStore.gate!!.complete(Unit)

        assertTrue(audioStore.files.isEmpty())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun audioIsIgnoredForFolders() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddFolder)

        viewModel.onEvent(AdminEvent.AudioPicked("content://zvuk"))

        assertTrue(audioStore.files.isEmpty())
        assertNull(editDialog().audioFile)
    }

    @Test
    fun preview_playsPickedAudio_andStopButtonStopsIt() = runVmTest {
        subscribe()
        val audio = pickAudio()

        viewModel.onEvent(AdminEvent.ToggleAudioPreview)
        assertEquals(listOf(audio), audioPlayer.played)
        assertTrue(editDialog().isPreviewingAudio)
        assertEquals(1, audioPlayer.activePlays)

        viewModel.onEvent(AdminEvent.ToggleAudioPreview)
        assertEquals(false, editDialog().isPreviewingAudio)
        assertEquals(0, audioPlayer.activePlays) // přehrávač je uvolněn
    }

    @Test
    fun preview_endsByItself_whenAudioFinishes() = runVmTest {
        subscribe()
        pickAudio()
        viewModel.onEvent(AdminEvent.ToggleAudioPreview)

        audioPlayer.finishPlaying()

        assertEquals(false, editDialog().isPreviewingAudio)
        assertEquals(0, audioPlayer.activePlays)
    }

    @Test
    fun preview_stopsWhenDialogCloses() = runVmTest {
        subscribe()
        pickAudio()
        viewModel.onEvent(AdminEvent.ToggleAudioPreview)

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(0, audioPlayer.activePlays)
    }

    @Test
    fun preview_stopsWhenAudioIsRemovedOrReplaced() = runVmTest {
        subscribe()
        pickAudio()
        viewModel.onEvent(AdminEvent.ToggleAudioPreview)
        viewModel.onEvent(AdminEvent.AudioPicked("content://jine"))
        assertEquals(0, audioPlayer.activePlays)
        assertEquals(false, editDialog().isPreviewingAudio)

        viewModel.onEvent(AdminEvent.ToggleAudioPreview)
        viewModel.onEvent(AdminEvent.RemoveAudio)
        assertEquals(0, audioPlayer.activePlays)
    }

    @Test
    fun preview_failure_showsProblem_andButtonWorksAgain() = runVmTest {
        subscribe()
        val audio = pickAudio()
        audioPlayer.failWith = AudioPlaybackException("rozbité")

        viewModel.onEvent(AdminEvent.ToggleAudioPreview)

        assertEquals(AudioProblem.PLAYBACK_FAILED, editDialog().audioProblem)
        assertEquals(false, editDialog().isPreviewingAudio)

        audioPlayer.failWith = null
        viewModel.onEvent(AdminEvent.ToggleAudioPreview)
        assertTrue(editDialog().isPreviewingAudio)
        assertNull(editDialog().audioProblem)
        assertEquals(listOf(audio, audio), audioPlayer.played)
    }

    @Test
    fun previewWithoutAudio_doesNothing() = runVmTest {
        subscribe()
        viewModel.onEvent(AdminEvent.AddWord)

        viewModel.onEvent(AdminEvent.ToggleAudioPreview)

        assertTrue(audioPlayer.played.isEmpty())
    }

    private fun ids() = viewModel.uiState.value.items.map { it.id }

    private fun selected() = viewModel.uiState.value.selectedIds

    private fun itemByTitle(title: String) = viewModel.uiState.value.items.first { it.title == title }

    // --- multi-výběr (6.1)

    @Test
    fun selectionMode_togglesItemsAndSelectAll() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        create(AdminEvent.AddWord, "b")
        create(AdminEvent.AddWord, "c")
        assertEquals(false, viewModel.uiState.value.isSelecting)

        viewModel.onEvent(AdminEvent.ToggleSelecting)
        assertTrue(viewModel.uiState.value.isSelecting)
        assertTrue(selected().isEmpty())

        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("b").id))
        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("A").id))
        assertEquals(setOf(itemByTitle("b").id, itemByTitle("A").id), selected())

        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("b").id)) // odznačení
        assertEquals(setOf(itemByTitle("A").id), selected())

        viewModel.onEvent(AdminEvent.ToggleSelectAll)
        assertEquals(ids().toSet(), selected())

        viewModel.onEvent(AdminEvent.ToggleSelectAll) // už je vše označené, takže se vše odznačí
        assertTrue(selected().isEmpty())
        assertTrue(viewModel.uiState.value.isSelecting)

        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("c").id))
        viewModel.onEvent(AdminEvent.ToggleSelecting) // konec režimu zruší výběr
        assertEquals(false, viewModel.uiState.value.isSelecting)
        assertTrue(selected().isEmpty())
    }

    @Test
    fun selectionOutsideSelectionMode_isIgnored() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")

        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("a").id))
        viewModel.onEvent(AdminEvent.ToggleSelectAll)

        assertTrue(selected().isEmpty())
        assertEquals(false, viewModel.uiState.value.isSelecting)
    }

    @Test
    fun selection_isClearedWhenLeavingTheFolder() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        create(AdminEvent.AddWord, "b")
        viewModel.onEvent(AdminEvent.ToggleSelecting)
        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("b").id))

        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("A").id))

        assertEquals(false, viewModel.uiState.value.isSelecting)
        assertTrue(selected().isEmpty())
    }

    @Test
    fun deleteSelected_showsTotalIncludingContents_andDeletesOnlySelected() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        create(AdminEvent.AddFolder, "Zůstane")
        create(AdminEvent.AddWord, "b")
        create(AdminEvent.AddWord, "c")
        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("A").id))
        create(AdminEvent.AddWord, "a1")
        create(AdminEvent.AddFolder, "A2")
        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("A2").id))
        create(AdminEvent.AddWord, "a2w")
        viewModel.onEvent(AdminEvent.GoToFolder(null))

        viewModel.onEvent(AdminEvent.ToggleSelecting)
        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("A").id))
        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("b").id))
        viewModel.onEvent(AdminEvent.DeleteSelected)

        val dialog = viewModel.uiState.value.dialog as AdminDialog.ConfirmDeleteSelected
        assertEquals(2, dialog.selectedCount)
        assertEquals(5, dialog.totalCount) // A + b + obsah A (a1, A2, a2w)
        assertEquals(listOf("A", "Zůstane", "b", "c"), titles()) // zatím se nic nesmazalo

        viewModel.onEvent(AdminEvent.ConfirmDeleteSelected)

        assertEquals(listOf("Zůstane", "c"), titles())
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(false, viewModel.uiState.value.isSelecting)
        assertTrue(repository.allFolders.none { it.title.startsWith("A") })
        assertTrue(repository.allWords.none { it.title in setOf("b", "a1", "a2w") })
    }

    @Test
    fun deleteSelected_cancelled_deletesNothingAndKeepsSelection() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")
        viewModel.onEvent(AdminEvent.ToggleSelecting)
        viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle("a").id))
        viewModel.onEvent(AdminEvent.DeleteSelected)

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertEquals(listOf("a"), titles())
        assertEquals(1, selected().size)
    }

    @Test
    fun deleteSelected_withNothingSelected_opensNoDialog() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")
        viewModel.onEvent(AdminEvent.ToggleSelecting)

        viewModel.onEvent(AdminEvent.DeleteSelected)

        assertNull(viewModel.uiState.value.dialog)
    }

    // --- hromadný přesun (6.2)

    private fun moveDialog() = viewModel.uiState.value.dialog as AdminDialog.MoveTarget

    private fun selectInRoot(vararg titles: String) {
        viewModel.onEvent(AdminEvent.ToggleSelecting)
        titles.forEach { viewModel.onEvent(AdminEvent.ToggleSelected(itemByTitle(it).id)) }
    }

    @Test
    fun moveSelected_browsesFoldersAndMovesEverythingOnConfirm() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "Cíl")
        create(AdminEvent.AddFolder, "Přesunu")
        create(AdminEvent.AddWord, "slovo")
        create(AdminEvent.AddWord, "zůstane")
        selectInRoot("Přesunu", "slovo")

        viewModel.onEvent(AdminEvent.MoveSelected)
        assertEquals(2, moveDialog().movingCount)
        assertEquals(null, moveDialog().targetId)
        assertEquals(listOf("Cíl", "Přesunu"), moveDialog().folders.map { it.title })

        viewModel.onEvent(AdminEvent.BrowseMoveTarget(itemByTitle("Cíl").id))
        assertEquals(listOf("Cíl"), moveDialog().path.map { it.title })
        assertTrue(moveDialog().folders.isEmpty())

        viewModel.onEvent(AdminEvent.ConfirmMove)

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(false, viewModel.uiState.value.isSelecting)
        assertEquals(listOf("Cíl", "zůstane"), titles())
        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("Cíl").id))
        assertEquals(listOf("Přesunu", "slovo"), titles())
    }

    @Test
    fun moveFolderIntoItsOwnSubfolder_showsProblem_andChangesNothing() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "A")
        create(AdminEvent.AddWord, "w")
        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("A").id))
        create(AdminEvent.AddFolder, "Vnitřní")
        viewModel.onEvent(AdminEvent.GoToFolder(null))
        selectInRoot("A", "w")
        viewModel.onEvent(AdminEvent.MoveSelected)
        viewModel.onEvent(AdminEvent.BrowseMoveTarget(itemByTitle("A").id))
        viewModel.onEvent(AdminEvent.BrowseMoveTarget(moveDialog().folders.single().id)) // Vnitřní

        viewModel.onEvent(AdminEvent.ConfirmMove)

        assertEquals(MoveProblem.INTO_ITSELF, moveDialog().problem)
        assertTrue(viewModel.uiState.value.isSelecting)
        assertEquals(2, selected().size)
        assertEquals(listOf("A", "w"), titles())
        assertEquals(1, repository.allFolders.count { it.parentId == null })
    }

    @Test
    fun confirmMoveToTheFolderWhereTheItemsAre_doesNothing() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")
        create(AdminEvent.AddWord, "b")
        selectInRoot("a")
        viewModel.onEvent(AdminEvent.MoveSelected)

        viewModel.onEvent(AdminEvent.ConfirmMove)

        assertTrue(viewModel.uiState.value.dialog is AdminDialog.MoveTarget)
        assertEquals(listOf("a", "b"), titles())
    }

    @Test
    fun moveDialog_cancelKeepsSelectionAndMovesNothing() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "Cíl")
        create(AdminEvent.AddWord, "a")
        selectInRoot("a")
        viewModel.onEvent(AdminEvent.MoveSelected)
        viewModel.onEvent(AdminEvent.BrowseMoveTarget(itemByTitle("Cíl").id))

        viewModel.onEvent(AdminEvent.DismissDialog)

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(1, selected().size)
        assertEquals(listOf("Cíl", "a"), titles())
    }

    @Test
    fun moveSelected_withNothingSelected_opensNoDialog() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")
        viewModel.onEvent(AdminEvent.ToggleSelecting)

        viewModel.onEvent(AdminEvent.MoveSelected)

        assertNull(viewModel.uiState.value.dialog)
    }

    // --- ruční pořadí (6.3) a abecední řazení (6.4)

    @Test
    fun moveItemUpAndDown_changesOrderInAdmin() = runVmTest {
        subscribe()
        create(AdminEvent.AddWord, "a")
        create(AdminEvent.AddFolder, "B")
        create(AdminEvent.AddWord, "c")

        viewModel.onEvent(AdminEvent.MoveItemUp(itemByTitle("c").id))
        assertEquals(listOf("a", "c", "B"), titles())

        viewModel.onEvent(AdminEvent.MoveItemDown(itemByTitle("a").id))
        assertEquals(listOf("c", "a", "B"), titles())

        viewModel.onEvent(AdminEvent.MoveItemUp(itemByTitle("c").id)) // už je první
        viewModel.onEvent(AdminEvent.MoveItemDown(itemByTitle("B").id)) // už je poslední
        assertEquals(listOf("c", "a", "B"), titles())
    }

    @Test
    fun moveItem_reordersOnlyTheCurrentFolder() = runVmTest {
        subscribe()
        create(AdminEvent.AddFolder, "F")
        create(AdminEvent.AddWord, "root")
        viewModel.onEvent(AdminEvent.GoToFolder(itemByTitle("F").id))
        create(AdminEvent.AddWord, "x")
        create(AdminEvent.AddWord, "y")

        viewModel.onEvent(AdminEvent.MoveItemUp(itemByTitle("y").id))

        assertEquals(listOf("y", "x"), titles())
        viewModel.onEvent(AdminEvent.GoToFolder(null))
        assertEquals(listOf("F", "root"), titles())
    }

    @Test
    fun sortAlphabetically_usesCollationFromConfiguration() = runVmTest {
        subscribe()
        listOf("dům", "chléb", "cibule").forEach { create(AdminEvent.AddWord, it) }

        viewModel.onEvent(AdminEvent.SortAlphabetically)
        assertEquals(listOf("cibule", "dům", "chléb"), titles())

        config.update { it.copy(collationLanguageTag = "en") }
        viewModel.onEvent(AdminEvent.SortAlphabetically)
        assertEquals(listOf("chléb", "cibule", "dům"), titles())
    }
}
