package cz.talkative.app.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.sync.AuthorizationOutcome
import cz.talkative.app.domain.sync.ConsentRequest
import cz.talkative.app.domain.sync.SyncAuthorizer
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncScheduler
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.domain.sync.SyncStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val stateRepository: SyncStateRepository,
    private val authorizer: SyncAuthorizer,
    private val scheduler: SyncScheduler,
) : ViewModel() {

    private data class Local(
        val consent: ConsentRequest? = null,
        val isSigningIn: Boolean = false,
        val signInError: SyncError? = null,
        /** Stav v okamžiku, kdy uživatel klepl na „Synchronizovat teď“; čeká se, až se změní. */
        val requestedFrom: SyncState? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SyncUiState> = combine(stateRepository.state, local) { sync, l ->
        val pending = l.requestedFrom?.let { from ->
            !sync.isRunning && sync.lastSuccessMillis == from.lastSuccessMillis && sync.lastError == from.lastError
        } ?: false
        SyncUiState(sync, l.consent, l.isSigningIn, l.signInError, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.SignInRequested -> signIn()
            SyncEvent.ConsentLaunched -> local.update { it.copy(consent = null) }
            is SyncEvent.ConsentFinished -> finishConsent(event.result)
            SyncEvent.SignOutRequested -> signOut()
            SyncEvent.SyncNowRequested -> syncNow()
            SyncEvent.DismissError -> local.update { it.copy(signInError = null) }
        }
    }

    private fun signIn() {
        if (local.value.isSigningIn) return
        local.update { it.copy(isSigningIn = true, signInError = null) }
        viewModelScope.launch { handle(authorizer.authorize()) }
    }

    private fun finishConsent(result: Any?) {
        local.update { it.copy(isSigningIn = true) }
        viewModelScope.launch { handle(authorizer.completeConsent(result)) }
    }

    private suspend fun handle(outcome: AuthorizationOutcome) {
        when (outcome) {
            is AuthorizationOutcome.Granted -> {
                // Prázdný text = přihlášený účet bez známého e-mailu (viz SyncState.account).
                stateRepository.setAccount(outcome.email.orEmpty())
                scheduler.requestSync() // po přihlášení se slovník hned sloučí s Drive
                local.update { it.copy(isSigningIn = false, signInError = null) }
            }
            is AuthorizationOutcome.NeedsConsent -> local.update { it.copy(isSigningIn = false, consent = outcome.request) }
            is AuthorizationOutcome.Failed -> local.update { it.copy(isSigningIn = false, signInError = outcome.error) }
            AuthorizationOutcome.Cancelled -> local.update { it.copy(isSigningIn = false) }
        }
    }

    private fun syncNow() {
        viewModelScope.launch {
            val current = stateRepository.state.first()
            if (!current.isSignedIn || current.isRunning) return@launch
            local.update { it.copy(requestedFrom = current) }
            scheduler.requestSync()
        }
    }

    /** Odhlášení účet zapomene a přístup odvolá; místní slovník zůstane beze změny. */
    private fun signOut() {
        viewModelScope.launch {
            authorizer.revoke()
            stateRepository.setAccount(null)
            local.update { Local() }
        }
    }
}
