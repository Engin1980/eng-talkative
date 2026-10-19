package cz.talkative.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.talkative.app.data.repository.DataStoreConfigRepository
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DataStore v paměti: souborový DataStore na Windows nezvládá přejmenování přes existující soubor,
 * proto se skutečné ukládání na disk ověřuje v instrumentovaném testu (`ConfigPersistenceTest`).
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}

class DataStoreConfigRepositoryTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DataStoreConfigRepository(dataStore)

    @Test
    fun defaults_matchAssignment() = runTest {
        val config = repository.config.first()

        assertEquals(AddWordMode.TAP, config.addWordMode)
        assertEquals(true, config.allowReorderInSentence)
        assertEquals(1_000L, config.removeHoldMillis)
        assertEquals(10_000L, config.adminHoldMillis)
        assertEquals(false, config.speakWordOnTap)
        assertEquals(GridSize(columns = 3, rows = 3), config.gridPortrait)
        assertEquals(GridSize(columns = 4, rows = 3), config.gridLandscape)
        assertEquals("cs", config.collationLanguageTag)
        assertEquals(false, config.hideCaptionsInSentence)
        assertEquals(false, config.autoClearAfterPlayback)
        assertEquals(AppConfig(), config)
    }

    @Test
    fun update_persistsChangesAndKeepsTheRest() = runTest {
        repository.update { it.copy(addWordMode = AddWordMode.DRAG, gridPortrait = GridSize(2, 5)) }
        repository.update { it.copy(adminHoldMillis = 4_000L, hideCaptionsInSentence = true) }

        val config = repository.config.first()
        assertEquals(AddWordMode.DRAG, config.addWordMode)
        assertEquals(GridSize(2, 5), config.gridPortrait)
        assertEquals(4_000L, config.adminHoldMillis)
        assertEquals(true, config.hideCaptionsInSentence)
        assertEquals(AppConfig().gridLandscape, config.gridLandscape)
    }

    @Test
    fun unknownStoredEnumValue_fallsBackToDefault() = runTest {
        dataStore.edit { it[stringPreferencesKey("add_word_mode")] = "NEEXISTUJE" }

        assertEquals(AddWordMode.TAP, repository.config.first().addWordMode)
    }

    @Test
    fun storedValuesOutsideLimits_areClampedWhenRead() = runTest {
        dataStore.edit {
            it[androidx.datastore.preferences.core.intPreferencesKey("grid_portrait_columns")] = 99
            it[androidx.datastore.preferences.core.longPreferencesKey("remove_hold_millis")] = 0L
        }

        val config = repository.config.first()

        assertEquals(6, config.gridPortrait.columns)
        assertEquals(500L, config.removeHoldMillis)
    }
}
