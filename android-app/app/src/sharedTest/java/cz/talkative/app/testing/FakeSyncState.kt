package cz.talkative.app.testing

import cz.talkative.app.domain.sync.AuthorizationOutcome
import cz.talkative.app.domain.sync.SyncAuthorizer
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.domain.sync.SyncStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Stav synchronizace (účet, poslední výsledek) v paměti. */
class FakeSyncStateRepository(initial: SyncState = SyncState()) : SyncStateRepository {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<SyncState> = flow
    val current: SyncState get() = flow.value

    override suspend fun setAccount(email: String?) {
        flow.value = if (email == null) SyncState() else flow.value.copy(account = email, lastError = null)
    }

    override suspend fun markStarted() {
        flow.value = flow.value.copy(isRunning = true)
    }

    override suspend fun markSuccess(nowMillis: Long) {
        flow.value = flow.value.copy(isRunning = false, lastSuccessMillis = nowMillis, lastError = null)
    }

    override suspend fun markFailed(error: SyncError) {
        flow.value = flow.value.copy(isRunning = false, lastError = error)
    }
}

/** Přihlášení řízené z testu: výsledky se nastavují předem, volání se zaznamenávají. */
class FakeSyncAuthorizer : SyncAuthorizer {
    var authorizeResult: AuthorizationOutcome = AuthorizationOutcome.Granted("uzivatel@example.com")
    var consentResult: AuthorizationOutcome = AuthorizationOutcome.Granted("uzivatel@example.com")
    var authorizeCalls = 0
        private set
    val consentResults = mutableListOf<Any?>()
    var revokeCalls = 0
        private set

    override suspend fun authorize(): AuthorizationOutcome {
        authorizeCalls++
        return authorizeResult
    }

    override suspend fun completeConsent(result: Any?): AuthorizationOutcome {
        consentResults += result
        return consentResult
    }

    override suspend fun revoke() {
        revokeCalls++
    }
}
