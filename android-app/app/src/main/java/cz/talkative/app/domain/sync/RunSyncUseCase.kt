package cz.talkative.app.domain.sync

import cz.talkative.app.domain.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SyncRunResult {
    /** Není přihlášený účet, nic se nedělo. */
    data object Skipped : SyncRunResult

    data class Success(val outcome: SyncOutcome) : SyncRunResult

    /** Chyba, která může zmizet sama (síť, server): má smysl to zkusit později. */
    data class Retry(val error: SyncError) : SyncRunResult

    /** Chyba, kterou musí vyřešit uživatel (znovu se přihlásit, aktualizovat aplikaci): opakování nepomůže. */
    data class GaveUp(val error: SyncError) : SyncRunResult
}

/**
 * Spustí synchronizaci a zapíše její výsledek do [SyncStateRepository] (probíhá / poslední úspěch / poslední chyba).
 * Volá ho pracovní úloha na pozadí; bez přihlášeného účtu nedělá nic.
 */
class RunSyncUseCase @Inject constructor(
    private val state: SyncStateRepository,
    private val sync: SyncDictionaryUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): SyncRunResult {
        if (!state.state.first().isSignedIn) return SyncRunResult.Skipped
        state.markStarted()
        var finished = false
        try {
            val outcome = sync()
            state.markSuccess(clock.nowMillis())
            finished = true
            return SyncRunResult.Success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncException) {
            state.markFailed(e.error)
            finished = true
            return if (e.error in RETRYABLE) SyncRunResult.Retry(e.error) else SyncRunResult.GaveUp(e.error)
        } catch (e: Exception) {
            state.markFailed(SyncError.UNKNOWN)
            finished = true
            return SyncRunResult.Retry(SyncError.UNKNOWN)
        } finally {
            // Přerušení (zrušená úloha, vypnutí procesu) nesmí nechat navždy svítit „probíhá synchronizace“.
            if (!finished) withContext(NonCancellable) { state.markIdle() }
        }
    }

    private companion object {
        val RETRYABLE = setOf(SyncError.NO_NETWORK, SyncError.SERVER_ERROR, SyncError.UNAUTHORIZED, SyncError.UNKNOWN)
    }
}

/** Naplánuje synchronizaci na pozadí. Opakované žádosti se slučují do jedné. */
interface SyncScheduler {
    /** Spustí synchronizaci, jakmile bude síť, nejdřív za [delayMillis] (krátké zpoždění sloučí sérii úprav v administraci). */
    fun requestSync(delayMillis: Long = 0)
}
