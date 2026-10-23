package cz.talkative.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.FolderEntity
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.sync.SyncTrigger
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.testing.FakeSyncScheduler
import cz.talkative.app.testing.FakeSyncStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spouštění synchronizace: při startu a po změně slovníku v databázi, jen když je přihlášený účet. */
@RunWith(AndroidJUnit4::class)
class SyncTriggerTest {

    private lateinit var database: TalkativeDatabase
    private val scheduler = FakeSyncScheduler()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TalkativeDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun trigger(state: SyncState) = SyncTrigger(database, FakeSyncStateRepository(state), scheduler)

    private fun addFolder(id: String) = runBlocking {
        database.dictionaryDao().insertFolder(FolderEntity(id, "default", "složka", null, 0, 1L))
    }

    @Test
    fun signedIn_requestsSyncAtStart_andAfterEachChange_withDelayToMergeBursts() = runBlocking {
        trigger(SyncState(account = "a@example.com")).start(scope)
        assertEquals(listOf(0L), scheduler.requests) // start aplikace

        addFolder("f1")
        addFolder("f2")
        addFolder("f3")
        delay(3_500) // série úprav se po 2 s ticha sloučí do jedné žádosti

        assertEquals(listOf(0L, 5_000L), scheduler.requests)
    }

    @Test
    fun signedOut_neverSchedulesAnything() = runBlocking {
        trigger(SyncState()).start(scope)

        addFolder("f1")
        delay(3_500)

        assertTrue(scheduler.requests.isEmpty())
    }
}
