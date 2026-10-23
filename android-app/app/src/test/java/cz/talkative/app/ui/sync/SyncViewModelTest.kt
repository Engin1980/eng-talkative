package cz.talkative.app.ui.sync

import cz.talkative.app.domain.sync.AuthorizationOutcome
import cz.talkative.app.domain.sync.ConsentRequest
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.testing.FakeSyncAuthorizer
import cz.talkative.app.testing.FakeSyncStateRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val state = FakeSyncStateRepository()
    private val authorizer = FakeSyncAuthorizer()
    private lateinit var viewModel: SyncViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SyncViewModel(state, authorizer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVmTest(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.subscribe() {
        viewModel.uiState.launchIn(backgroundScope)
    }

    @Test
    fun startsSignedOut_runningLocallyOnly() = runVmTest {
        subscribe()

        assertFalse(viewModel.uiState.value.sync.isSignedIn)
        assertNull(viewModel.uiState.value.consent)
    }

    @Test
    fun signIn_withGrantedAccess_storesTheAccount() = runVmTest {
        subscribe()

        viewModel.onEvent(SyncEvent.SignInRequested)

        assertEquals("uzivatel@example.com", viewModel.uiState.value.sync.account)
        assertFalse(viewModel.uiState.value.isSigningIn)
        assertNull(viewModel.uiState.value.signInError)
    }

    @Test
    fun signIn_withoutKnownEmail_stillCountsAsSignedIn() = runVmTest {
        subscribe()
        authorizer.authorizeResult = AuthorizationOutcome.Granted(null)

        viewModel.onEvent(SyncEvent.SignInRequested)

        assertTrue(viewModel.uiState.value.sync.isSignedIn)
        assertEquals("", viewModel.uiState.value.sync.account)
    }

    @Test
    fun signIn_needingConsent_exposesItOnce_andFinishesAfterTheUserConfirms() = runVmTest {
        subscribe()
        val request = ConsentRequest("okno")
        authorizer.authorizeResult = AuthorizationOutcome.NeedsConsent(request)

        viewModel.onEvent(SyncEvent.SignInRequested)
        assertEquals(request, viewModel.uiState.value.consent)
        assertFalse(viewModel.uiState.value.sync.isSignedIn) // dokud uživatel nepotvrdí, nic se neukládá

        viewModel.onEvent(SyncEvent.ConsentLaunched)
        assertNull(viewModel.uiState.value.consent) // neotevře se podruhé
        viewModel.onEvent(SyncEvent.ConsentFinished("výsledek obrazovky"))

        assertEquals(listOf<Any?>("výsledek obrazovky"), authorizer.consentResults)
        assertEquals("uzivatel@example.com", viewModel.uiState.value.sync.account)
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun cancelledConsent_changesNothing_andAppStaysLocal() = runVmTest {
        subscribe()
        authorizer.consentResult = AuthorizationOutcome.Cancelled

        viewModel.onEvent(SyncEvent.ConsentFinished(null))

        assertFalse(viewModel.uiState.value.sync.isSignedIn)
        assertNull(viewModel.uiState.value.signInError)
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun failedSignIn_showsTheError_andCanBeDismissed() = runVmTest {
        subscribe()
        authorizer.authorizeResult = AuthorizationOutcome.Failed(SyncError.NO_PLAY_SERVICES)

        viewModel.onEvent(SyncEvent.SignInRequested)

        assertEquals(SyncError.NO_PLAY_SERVICES, viewModel.uiState.value.signInError)
        assertFalse(viewModel.uiState.value.sync.isSignedIn)
        viewModel.onEvent(SyncEvent.DismissError)
        assertNull(viewModel.uiState.value.signInError)
    }

    @Test
    fun signOut_revokesAccess_forgetsTheAccountAndItsStatus() = runVmTest {
        subscribe()
        state.setAccount("uzivatel@example.com")
        state.markSuccess(1_000)
        state.markFailed(SyncError.NO_NETWORK)

        viewModel.onEvent(SyncEvent.SignOutRequested)

        assertEquals(1, authorizer.revokeCalls)
        assertEquals(SyncState(), viewModel.uiState.value.sync)
    }
}
