package cz.talkative.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.talkative.app.data.repository.DataStoreSyncStateRepository
import cz.talkative.app.domain.sync.SyncError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = flow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        flow.value = transform(flow.value)
        return flow.value
    }
}

class DataStoreSyncStateRepositoryTest {

    private val dataStore = MemoryDataStore()
    private val repository = DataStoreSyncStateRepository(dataStore)

    @Test
    fun defaults_areSignedOut() = runTest {
        val state = repository.state.first()

        assertNull(state.account)
        assertNull(state.lastSuccessMillis)
        assertNull(state.lastError)
        assertFalse(state.isRunning)
        assertFalse(state.isSignedIn)
    }

    @Test
    fun account_isStored_andSignOutForgetsEverything() = runTest {
        repository.setAccount("uzivatel@example.com")
        repository.markStarted()
        repository.markSuccess(5_000)
        repository.markFailed(SyncError.NO_NETWORK)

        val signedIn = repository.state.first()
        assertEquals("uzivatel@example.com", signedIn.account)
        assertEquals(5_000L, signedIn.lastSuccessMillis)
        assertEquals(SyncError.NO_NETWORK, signedIn.lastError)
        assertFalse(signedIn.isRunning) // chyba běh ukončí

        repository.setAccount(null)
        val signedOut = repository.state.first()
        assertNull(signedOut.account)
        assertNull(signedOut.lastSuccessMillis)
        assertNull(signedOut.lastError)
    }

    @Test
    fun success_clearsTheError_andRunningFlagIsTrackedOnlyWhenSignedIn() = runTest {
        repository.markStarted()
        assertFalse(repository.state.first().isRunning) // bez účtu se „běží“ nebere

        repository.setAccount("a@example.com")
        repository.markStarted()
        assertTrue(repository.state.first().isRunning)
        repository.markFailed(SyncError.SERVER_ERROR)
        repository.markSuccess(9_000)

        val state = repository.state.first()
        assertNull(state.lastError)
        assertFalse(state.isRunning)
        assertEquals(9_000L, state.lastSuccessMillis)
    }

    @Test
    fun unknownStoredError_isIgnored_andEmptyAccountMeansSignedInWithoutEmail() = runTest {
        dataStore.edit { it[stringPreferencesKey("sync_last_error")] = "NEEXISTUJE" }
        repository.setAccount("")

        val state = repository.state.first()

        assertNull(state.lastError)
        assertTrue(state.isSignedIn)
        assertEquals("", state.account)
    }

    @Test
    fun setAccount_toAnotherAccount_clearsOnlyTheError() = runTest {
        repository.setAccount("a@example.com")
        repository.markSuccess(1_000)
        repository.markFailed(SyncError.UNAUTHORIZED)

        repository.setAccount("b@example.com")

        val state = repository.state.first()
        assertEquals("b@example.com", state.account)
        assertNull(state.lastError)
    }
}
