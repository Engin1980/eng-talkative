package cz.talkative.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncState
import cz.talkative.app.domain.sync.SyncStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Účet a stav synchronizace ve stejném souboru DataStore jako konfigurace, ale pod vlastními klíči (do exportu nepatří). */
class DataStoreSyncStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncStateRepository {

    override val state: Flow<SyncState> = dataStore.data.map { preferences ->
        SyncState(
            account = preferences[ACCOUNT],
            lastSuccessMillis = preferences[LAST_SUCCESS],
            lastError = preferences[LAST_ERROR]?.let { name -> SyncError.entries.firstOrNull { it.name == name } },
            // Příznak „běží“ po pádu aplikace přežije; proto se bere jako platný jen, když je přihlášený účet.
            isRunning = preferences[RUNNING] == true && preferences[ACCOUNT] != null,
        )
    }

    override suspend fun setAccount(email: String?) {
        dataStore.edit { preferences ->
            if (email == null) {
                preferences.remove(ACCOUNT)
                preferences.remove(LAST_SUCCESS)
                preferences.remove(LAST_ERROR)
                preferences.remove(RUNNING)
            } else {
                preferences[ACCOUNT] = email
                preferences.remove(LAST_ERROR)
            }
        }
    }

    override suspend fun markStarted() {
        dataStore.edit { it[RUNNING] = true }
    }

    override suspend fun markSuccess(nowMillis: Long) {
        dataStore.edit {
            it[RUNNING] = false
            it[LAST_SUCCESS] = nowMillis
            it.remove(LAST_ERROR)
        }
    }

    override suspend fun markFailed(error: SyncError) {
        dataStore.edit {
            it[RUNNING] = false
            it[LAST_ERROR] = error.name
        }
    }

    private companion object {
        val ACCOUNT = stringPreferencesKey("sync_account")
        val LAST_SUCCESS = longPreferencesKey("sync_last_success")
        val LAST_ERROR = stringPreferencesKey("sync_last_error")
        val RUNNING = booleanPreferencesKey("sync_running")
    }
}
