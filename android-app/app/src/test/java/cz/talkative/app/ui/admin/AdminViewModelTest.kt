package cz.talkative.app.ui.admin

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.usecase.CountFolderContentsUseCase
import cz.talkative.app.domain.usecase.DeleteFolderUseCase
import cz.talkative.app.domain.usecase.DeleteWordUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val repository = FakeDictionaryRepository()
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
}
