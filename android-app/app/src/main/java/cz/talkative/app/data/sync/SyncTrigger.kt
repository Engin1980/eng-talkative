package cz.talkative.app.data.sync

import androidx.room.InvalidationTracker
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.domain.sync.SyncScheduler
import cz.talkative.app.domain.sync.SyncStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spouští synchronizaci: při startu aplikace a po každé změně slovníku (složky a slovíčka). Série úprav se sloučí:
 * plánuje se se zpožděním a nová žádost nahradí dosud nespuštěnou. Bez přihlášeného účtu se nic neplánuje.
 * Použití vzdálených změn vyvolá jednu další, prázdnou synchronizaci; ta nic nezapisuje, takže smyčka skončí.
 */
@Singleton
class SyncTrigger @Inject constructor(
    private val database: TalkativeDatabase,
    private val state: SyncStateRepository,
    private val scheduler: SyncScheduler,
) {
    /** Zavolá se jednou při startu aplikace, až doběhne úklid souborů. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun start(scope: CoroutineScope) {
        requestIfSignedIn(delayMillis = 0)
        database.invalidationTracker.createFlow("folders", "words")
            .drop(1) // první hodnota je jen počáteční stav
            .debounce(CHANGE_DEBOUNCE_MILLIS)
            .onEach { requestIfSignedIn(CHANGE_DELAY_MILLIS) }
            .launchIn(scope)
    }

    private suspend fun requestIfSignedIn(delayMillis: Long) {
        if (state.state.first().isSignedIn) scheduler.requestSync(delayMillis)
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MILLIS = 2_000L
        const val CHANGE_DELAY_MILLIS = 5_000L
    }
}
