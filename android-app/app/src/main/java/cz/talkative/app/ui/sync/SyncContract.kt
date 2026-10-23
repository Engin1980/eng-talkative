package cz.talkative.app.ui.sync

import cz.talkative.app.domain.sync.ConsentRequest
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncState

/** Stav obrazovky Synchronizace: uložený stav, souhlas čekající na potvrzení a případná chyba přihlášení. */
data class SyncUiState(
    val sync: SyncState = SyncState(),
    /** Souhlas, který má obrazovka otevřít (systémový dialog výběru účtu a oprávnění). */
    val consent: ConsentRequest? = null,
    /** Probíhá přihlašování (čeká se na služby Google). */
    val isSigningIn: Boolean = false,
    /** Chyba přihlášení; chyby samotné synchronizace jsou v [SyncState.lastError]. */
    val signInError: SyncError? = null,
)

sealed interface SyncEvent {
    data object SignInRequested : SyncEvent

    /** Obrazovka souhlas právě otevřela; stav se vyčistí, aby se neotevřel znovu. */
    data object ConsentLaunched : SyncEvent

    /** Souhlas skončil; [result] je výsledek systémové obrazovky (v Androidu `Intent`), `null` = zrušeno. */
    data class ConsentFinished(val result: Any?) : SyncEvent

    data object SignOutRequested : SyncEvent
    data object DismissError : SyncEvent
}
