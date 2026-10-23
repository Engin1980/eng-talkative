package cz.talkative.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.ui.sync.SYNC_ERROR_TAG
import cz.talkative.app.ui.sync.SyncEvent
import cz.talkative.app.ui.sync.SyncScreen
import cz.talkative.app.ui.sync.SyncUiState
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SyncEvent>()
    private var backs = 0

    private fun show(state: SyncUiState) {
        composeRule.setContent {
            TalkativeTheme { SyncScreen(state = state, onEvent = { events += it }, onBack = { backs++ }) }
        }
    }

    @Test
    fun signedOut_explainsLocalOnlyMode_andOffersSignIn() {
        show(SyncUiState())

        composeRule.onNodeWithText("Nikdo není přihlášený. Data zůstávají jen v tomto zařízení.").assertIsDisplayed()
        composeRule.onNodeWithText("Přihlásit účet Google").assertHeightIsAtLeast(64.dp).performClick()
        composeRule.onAllNodesWithText("Odhlásit").assertCountEquals(0)
        assertEquals(listOf<SyncEvent>(SyncEvent.SignInRequested), events)
    }

    @Test
    fun signedIn_showsAccountStatus_andOffersChangeAndSignOut() {
        show(SyncUiState(sync = SyncState(account = "uzivatel@example.com")))

        composeRule.onNodeWithText("Přihlášený účet: uzivatel@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Zatím nesynchronizováno.").assertIsDisplayed()
        composeRule.onNodeWithText("Změnit účet").performClick()
        composeRule.onNodeWithText("Odhlásit").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf(SyncEvent.SignInRequested, SyncEvent.SignOutRequested), events)
    }

    @Test
    fun accountWithoutEmail_isStillShownAsSignedIn() {
        show(SyncUiState(sync = SyncState(account = "")))

        composeRule.onNodeWithText("Přihlášený účet: účet Google").assertIsDisplayed()
    }

    @Test
    fun lastError_andRunningState_areShown() {
        show(SyncUiState(sync = SyncState(account = "a@example.com", lastError = SyncError.NO_NETWORK, lastSuccessMillis = 1_000L)))

        composeRule.onNodeWithTag(SYNC_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Chybí připojení k internetu", substring = true).assertIsDisplayed()
    }

    @Test
    fun runningSync_isAnnounced() {
        show(SyncUiState(sync = SyncState(account = "a@example.com", isRunning = true)))

        composeRule.onNodeWithText("Probíhá synchronizace…").assertIsDisplayed()
    }

    @Test
    fun signInError_isShown_andButtonsAreDisabledWhileSigningIn() {
        show(SyncUiState(isSigningIn = true, signInError = SyncError.NO_PLAY_SERVICES))

        composeRule.onNodeWithTag(SYNC_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("služby Google Play", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Přihlásit účet Google").assertIsNotEnabled()
        composeRule.onNodeWithText("Přihlašuji…").assertIsDisplayed()
    }

    @Test
    fun syncNow_isOfferedWhenSignedIn() {
        show(SyncUiState(sync = SyncState(account = "a@example.com")))
        composeRule.onNodeWithText("Synchronizovat teď").assertHeightIsAtLeast(64.dp).performClick()
        assertEquals(listOf<SyncEvent>(SyncEvent.SyncNowRequested), events)
    }

    @Test
    fun syncNow_isDisabledWhileSyncRuns() {
        show(SyncUiState(sync = SyncState(account = "a@example.com", isRunning = true)))
        composeRule.onNodeWithText("Synchronizovat teď").assertIsNotEnabled()
    }

    @Test
    fun syncNow_isNotOfferedWhenSignedOut() {
        show(SyncUiState())
        composeRule.onAllNodesWithText("Synchronizovat teď").assertCountEquals(0)
    }

    @Test
    fun pendingSync_explainsItWaitsForTheNetwork() {
        show(SyncUiState(sync = SyncState(account = "a@example.com"), syncPending = true))
        composeRule.onNodeWithText("Synchronizace je naplánovaná", substring = true).assertIsDisplayed()
    }

    @Test
    fun backButton_callsCallback() {
        show(SyncUiState())

        composeRule.onNodeWithText("Zpět do administrace").assertHeightIsAtLeast(64.dp).performClick()

        assertEquals(1, backs)
    }
}
