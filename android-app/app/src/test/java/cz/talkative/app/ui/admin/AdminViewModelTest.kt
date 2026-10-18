package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
import cz.talkative.app.domain.usecase.DiscardImageUseCase
import cz.talkative.app.domain.usecase.SaveImageUseCase
import cz.talkative.app.domain.image.ImageError
import cz.talkative.app.testing.FakeImageStore
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
            SaveImageUseCase(imageStore),
            DiscardImageUseCase(imageStore),
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
}
