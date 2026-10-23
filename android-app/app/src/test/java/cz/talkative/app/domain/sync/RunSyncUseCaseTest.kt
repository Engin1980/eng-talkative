package cz.talkative.app.domain.sync

import cz.talkative.app.testing.FakeRemoteStore
import cz.talkative.app.testing.FakeSyncDevice
import cz.talkative.app.testing.FakeSyncStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSyncUseCaseTest {

    private val remote = FakeRemoteStore()
    private val device = FakeSyncDevice(remote, now = 777L)
    private val state = FakeSyncStateRepository(SyncState(account = "a@example.com"))

    private fun run() = runBlocking {
        RunSyncUseCase(state, device.useCase(), { device.now })()
    }

    private fun item(id: String) = SyncItem(id, SyncItemKind.WORD, "default", id, null, 0, 700L)

    @Test
    fun signedOut_doesNothing_andNeverTouchesTheRemote() {
        val signedOut = FakeSyncStateRepository()

        val result = runBlocking { RunSyncUseCase(signedOut, device.useCase(), { 1L })() }

        assertEquals(SyncRunResult.Skipped, result)
        assertNull(remote.metadata)
        assertEquals(0, remote.metadataWrites)
    }

    @Test
    fun success_recordsTheTimeAndClearsOldErrors() {
        device.put(item("w"))
        runBlocking { state.markFailed(SyncError.NO_NETWORK) }

        val result = run()

        assertTrue(result is SyncRunResult.Success)
        assertEquals(777L, state.current.lastSuccessMillis)
        assertNull(state.current.lastError)
        assertFalse(state.current.isRunning)
    }

    @Test
    fun networkAndServerErrors_areRetried_andRecorded() {
        for (error in listOf(SyncError.NO_NETWORK, SyncError.SERVER_ERROR, SyncError.UNAUTHORIZED)) {
            remote.failWith = error

            val result = run()

            assertEquals(SyncRunResult.Retry(error), result)
            assertEquals(error, state.current.lastError)
            assertFalse(state.current.isRunning)
        }
    }

    @Test
    fun problemsThatNeedTheUser_areNotRetried() {
        for (error in listOf(SyncError.NEEDS_CONSENT, SyncError.NOT_SIGNED_IN, SyncError.NO_PLAY_SERVICES, SyncError.NEWER_VERSION)) {
            remote.failWith = error

            val result = run()

            assertEquals(SyncRunResult.GaveUp(error), result)
            assertEquals(error, state.current.lastError)
        }
    }

    @Test
    fun unexpectedException_isRecordedAsUnknown_andRetried() {
        device.local.applyFailure = IllegalStateException("netušená chyba")
        remote.metadata = SyncCodec.encode(listOf(item("x")))

        val result = run()

        assertEquals(SyncRunResult.Retry(SyncError.UNKNOWN), result)
        assertEquals(SyncError.UNKNOWN, state.current.lastError)
        assertFalse(state.current.isRunning)
    }

    @Test
    fun cancelledSync_doesNotLeaveTheRunningFlagOn() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val blocking = object : RemoteStore by remote {
            override suspend fun readMetadata(): String? {
                gate.await()
                return null
            }
        }
        val useCase = RunSyncUseCase(state, FakeSyncDevice(blocking).useCase(), { 1L })

        val job = async { useCase() }
        assertTrue(state.current.isRunning)
        job.cancel()
        try {
            job.await()
        } catch (e: CancellationException) {
            // očekávané
        }

        assertFalse(state.current.isRunning)
        assertNull(state.current.lastError) // přerušení není chyba
    }
}
