package cz.talkative.app.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * Stav synchronizace uložený na zařízení (ne v konfiguraci, takže se nikdy neexportuje).
 * [account] je e-mail vybraného účtu Google, prázdný text znamená účet bez známého e-mailu; `null` = nikdo není přihlášený
 * a aplikace běží jen lokálně.
 */
data class SyncState(
    val account: String? = null,
    val lastSuccessMillis: Long? = null,
    val lastError: SyncError? = null,
    val isRunning: Boolean = false,
) {
    val isSignedIn: Boolean get() = account != null
}

interface SyncStateRepository {
    val state: Flow<SyncState>

    /** Uloží vybraný účet; `null` účet odebere a smaže i stav poslední synchronizace. */
    suspend fun setAccount(email: String?)

    suspend fun markStarted()

    suspend fun markSuccess(nowMillis: Long)

    suspend fun markFailed(error: SyncError)
}

/**
 * Platformní objekt, který smí otevřít jen obrazovka (v Androidu `IntentSender` se souhlasem k přístupu). Domény a view model
 * ho jen předávají dál, proto je typ záměrně obecný.
 */
class ConsentRequest(val handle: Any)

sealed interface AuthorizationOutcome {
    /** Přístup k Drive je povolený; [email] může být neznámý. */
    data class Granted(val email: String?) : AuthorizationOutcome

    /** Uživatel musí souhlas nebo účet potvrdit na obrazovce. */
    data class NeedsConsent(val request: ConsentRequest) : AuthorizationOutcome

    data class Failed(val error: SyncError) : AuthorizationOutcome

    /** Uživatel potvrzení zrušil; nic se nemění. */
    data object Cancelled : AuthorizationOutcome
}

/** Přihlášení k Drive: výběr účtu a souhlas s oprávněním `drive.appdata`. */
interface SyncAuthorizer {
    /** Zkusí získat přístup k Drive pro uložený účet (nebo bez účtu, pak nabídne výběr). */
    suspend fun authorize(): AuthorizationOutcome

    /** Dokončí souhlas; [result] je výsledek obrazovky souhlasu (v Androidu `Intent`), `null` = zrušeno. */
    suspend fun completeConsent(result: Any?): AuthorizationOutcome

    /** Odvolá přístup aplikace k Drive (kde to jde); chyby se ignorují, účet se zapomene vždy. */
    suspend fun revoke()
}
