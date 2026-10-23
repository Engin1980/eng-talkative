package cz.talkative.app.data.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import cz.talkative.app.domain.sync.AccessTokenProvider
import cz.talkative.app.domain.sync.AuthorizationOutcome
import cz.talkative.app.domain.sync.ConsentRequest
import cz.talkative.app.domain.sync.SyncAuthorizer
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncException
import cz.talkative.app.domain.sync.SyncStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Přihlášení a přístupové tokeny přes Authorization API ze služeb Google Play (`Identity.getAuthorizationClient`).
 * Žádá se jediné oprávnění, `drive.appdata` (skrytá složka aplikace). Souhlas může potvrdit jen obrazovka
 * ([AuthorizationOutcome.NeedsConsent]); synchronizace na pozadí bez souhlasu skončí chybou `NEEDS_CONSENT`.
 */
@Singleton
class GoogleAuthorizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val state: SyncStateRepository,
) : SyncAuthorizer, AccessTokenProvider {

    private val client get() = Identity.getAuthorizationClient(context)

    @Volatile
    private var lastToken: String? = null

    private fun request(email: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, ACCOUNT_TYPE)) }
            .build()

    private fun requirePlayServices() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            throw SyncException(SyncError.NO_PLAY_SERVICES)
        }
    }

    // ---- AccessTokenProvider (synchronizace, i na pozadí)

    override suspend fun accessToken(forceRefresh: Boolean): String {
        requirePlayServices()
        val email = state.state.first().account ?: throw SyncException(SyncError.NOT_SIGNED_IN)
        if (forceRefresh) {
            // Odmítnutý token se musí vyřadit z mezipaměti služeb Google, jinak by vrátily znovu ten samý.
            lastToken?.let { token -> withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } } }
        }
        val result = try {
            client.authorize(request(email)).await()
        } catch (e: ApiException) {
            throw SyncException(e.toSyncError(), "Přihlášení k Drive selhalo", e)
        }
        if (result.hasResolution()) throw SyncException(SyncError.NEEDS_CONSENT)
        val token = result.accessToken ?: throw SyncException(SyncError.NEEDS_CONSENT)
        lastToken = token
        return token
    }

    // ---- SyncAuthorizer (obrazovka Synchronizace)

    override suspend fun authorize(): AuthorizationOutcome {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            return AuthorizationOutcome.Failed(SyncError.NO_PLAY_SERVICES)
        }
        val email = state.state.first().account
        return try {
            toOutcome(client.authorize(request(email)).await())
        } catch (e: ApiException) {
            AuthorizationOutcome.Failed(e.toSyncError())
        }
    }

    override suspend fun completeConsent(result: Any?): AuthorizationOutcome {
        val intent = result as? Intent ?: return AuthorizationOutcome.Cancelled
        return try {
            toOutcome(client.getAuthorizationResultFromIntent(intent))
        } catch (e: ApiException) {
            // Zrušení souhlasu uživatelem vrací výjimku se stavem „zrušeno“.
            if (e.statusCode == CommonStatusCodes.CANCELED) AuthorizationOutcome.Cancelled else AuthorizationOutcome.Failed(e.toSyncError())
        }
    }

    override suspend fun revoke() {
        val email = state.state.first().account
        lastToken = null
        try {
            val revoke = RevokeAccessRequest.builder()
                .setScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .apply { if (!email.isNullOrBlank()) setAccount(Account(email, ACCOUNT_TYPE)) }
                .build()
            client.revokeAccess(revoke).await()
        } catch (e: Exception) {
            // Odvolání je nepovinné; účet se zapomene i bez něj.
        }
    }

    private fun toOutcome(result: AuthorizationResult): AuthorizationOutcome {
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) return AuthorizationOutcome.NeedsConsent(ConsentRequest(pending.intentSender))
        if (result.accessToken == null) return AuthorizationOutcome.Failed(SyncError.NEEDS_CONSENT)
        lastToken = result.accessToken
        return AuthorizationOutcome.Granted(result.toGoogleSignInAccount()?.email)
    }

    private fun ApiException.toSyncError(): SyncError = when (statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> SyncError.NO_NETWORK
        CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED -> SyncError.NEEDS_CONSENT
        else -> SyncError.SERVER_ERROR
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val ACCOUNT_TYPE = "com.google"
    }
}

/** Převod `Task` z knihoven Google na suspend funkci (bez další závislosti jen kvůli tomuto). */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
