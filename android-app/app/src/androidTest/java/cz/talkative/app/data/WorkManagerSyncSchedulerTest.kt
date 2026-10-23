package cz.talkative.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cz.talkative.app.data.sync.SyncWorker
import cz.talkative.app.data.sync.WorkManagerSyncScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Skutečný WorkManager (inicializovaný aplikací přes `Configuration.Provider`): žádost je jedna unikátní úloha,
 * čeká na síť a nová žádost nahradí nespuštěnou. Zpoždění je dlouhé, aby se úloha během testu nespustila.
 */
@RunWith(AndroidJUnit4::class)
class WorkManagerSyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workManager get() = WorkManager.getInstance(context)
    private val scheduler = WorkManagerSyncScheduler(context)

    @Before
    fun setUp() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME).result.get()
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME).result.get()
    }

    private fun infos() = workManager.getWorkInfosForUniqueWork(SyncWorker.UNIQUE_NAME).get()

    @Test
    fun request_enqueuesOneWorkThatWaitsForTheNetwork() {
        scheduler.requestSync(delayMillis = 10 * 60_000L)

        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun theWorkIsCreatedByTheHiltWorkerFactory_andRunsToTheEnd() {
        // Bez přihlášeného účtu se synchronizace přeskočí a úloha skončí úspěchem; selhání továrny nebo manifestu
        // (WorkManager „Could not instantiate“) by skončilo FAILED.
        scheduler.requestSync(delayMillis = 0)

        val deadline = System.currentTimeMillis() + 30_000
        var state = infos().single().state
        while (!state.isFinished && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            state = infos().single().state
        }

        assertEquals(WorkInfo.State.SUCCEEDED, state)
    }

    @Test
    fun repeatedRequests_areMergedIntoOneWaitingWork() {
        repeat(5) { scheduler.requestSync(delayMillis = 10 * 60_000L) }

        val waiting = infos().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, waiting.size)
        assertTrue(infos().none { it.state == WorkInfo.State.RUNNING })
    }
}
