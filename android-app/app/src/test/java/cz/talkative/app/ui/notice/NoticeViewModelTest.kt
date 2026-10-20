package cz.talkative.app.ui.notice

import cz.talkative.app.domain.notice.NOTICE_VERSION
import cz.talkative.app.testing.FakeNoticeRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoticeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(repository: FakeNoticeRepository) =
        NoticeViewModel(repository).also { it.state.launchIn(backgroundScope) }

    @Test
    fun firstLaunch_requiresTheNotice() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(NoticeState.REQUIRED, viewModel(FakeNoticeRepository(null)).state.value)
    }

    @Test
    fun acknowledgedCurrentVersion_isNotShownAgain() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(NoticeState.ACKNOWLEDGED, viewModel(FakeNoticeRepository(NOTICE_VERSION)).state.value)
    }

    @Test
    fun olderAcknowledgedVersion_requiresTheNoticeAgain() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(NoticeState.REQUIRED, viewModel(FakeNoticeRepository(NOTICE_VERSION - 1)).state.value)
    }

    @Test
    fun acknowledge_storesTheCurrentVersion_andLetsTheUserIn() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeNoticeRepository(null)
        val viewModel = viewModel(repository)

        viewModel.acknowledge()

        assertEquals(NOTICE_VERSION, repository.current)
        assertEquals(NoticeState.ACKNOWLEDGED, viewModel.state.value)
    }

    @Test
    fun beforeTheStoredValueIsRead_nothingIsShown() {
        val viewModel = NoticeViewModel(FakeNoticeRepository(null))

        assertEquals(NoticeState.LOADING, viewModel.state.value) // bez odběratele zůstává výchozí stav
    }
}
