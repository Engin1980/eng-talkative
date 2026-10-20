package cz.talkative.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.domain.model.sanitized
import cz.talkative.app.domain.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Konfigurace v Jetpack DataStore (Preferences). Chybějící klíče znamenají výchozí hodnotu. */
class DataStoreConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ConfigRepository {

    override val config: Flow<AppConfig> = dataStore.data.map { it.toConfig() }

    override suspend fun update(transform: (AppConfig) -> AppConfig) {
        dataStore.edit { preferences -> preferences.write(transform(preferences.toConfig())) }
    }

    private fun Preferences.toConfig(): AppConfig {
        val defaults = AppConfig()
        return AppConfig(
            addWordMode = this[ADD_WORD_MODE]?.let { name -> AddWordMode.entries.firstOrNull { it.name == name } }
                ?: defaults.addWordMode,
            allowReorderInSentence = this[ALLOW_REORDER] ?: defaults.allowReorderInSentence,
            addHoldMillis = this[ADD_HOLD_MILLIS] ?: defaults.addHoldMillis,
            removeHoldMillis = this[REMOVE_HOLD_MILLIS] ?: defaults.removeHoldMillis,
            adminHoldMillis = this[ADMIN_HOLD_MILLIS] ?: defaults.adminHoldMillis,
            speakWordOnTap = this[SPEAK_WORD_ON_TAP] ?: defaults.speakWordOnTap,
            gridPortrait = GridSize(
                columns = this[GRID_PORTRAIT_COLUMNS] ?: defaults.gridPortrait.columns,
                rows = this[GRID_PORTRAIT_ROWS] ?: defaults.gridPortrait.rows,
            ),
            gridLandscape = GridSize(
                columns = this[GRID_LANDSCAPE_COLUMNS] ?: defaults.gridLandscape.columns,
                rows = this[GRID_LANDSCAPE_ROWS] ?: defaults.gridLandscape.rows,
            ),
            collationLanguageTag = this[COLLATION] ?: defaults.collationLanguageTag,
            hideCaptionsInSentence = this[HIDE_CAPTIONS] ?: defaults.hideCaptionsInSentence,
            autoClearAfterPlayback = this[AUTO_CLEAR] ?: defaults.autoClearAfterPlayback,
        ).sanitized() // poškozená nebo ručně upravená hodnota nesmí rozbít klienta
    }

    private fun MutablePreferences.write(config: AppConfig) {
        this[ADD_WORD_MODE] = config.addWordMode.name
        this[ALLOW_REORDER] = config.allowReorderInSentence
        this[ADD_HOLD_MILLIS] = config.addHoldMillis
        this[REMOVE_HOLD_MILLIS] = config.removeHoldMillis
        this[ADMIN_HOLD_MILLIS] = config.adminHoldMillis
        this[SPEAK_WORD_ON_TAP] = config.speakWordOnTap
        this[GRID_PORTRAIT_COLUMNS] = config.gridPortrait.columns
        this[GRID_PORTRAIT_ROWS] = config.gridPortrait.rows
        this[GRID_LANDSCAPE_COLUMNS] = config.gridLandscape.columns
        this[GRID_LANDSCAPE_ROWS] = config.gridLandscape.rows
        this[COLLATION] = config.collationLanguageTag
        this[HIDE_CAPTIONS] = config.hideCaptionsInSentence
        this[AUTO_CLEAR] = config.autoClearAfterPlayback
    }

    private companion object {
        val ADD_WORD_MODE = stringPreferencesKey("add_word_mode")
        val ALLOW_REORDER = booleanPreferencesKey("allow_reorder_in_sentence")
        val ADD_HOLD_MILLIS = longPreferencesKey("add_hold_millis")
        val REMOVE_HOLD_MILLIS = longPreferencesKey("remove_hold_millis")
        val ADMIN_HOLD_MILLIS = longPreferencesKey("admin_hold_millis")
        val SPEAK_WORD_ON_TAP = booleanPreferencesKey("speak_word_on_tap")
        val GRID_PORTRAIT_COLUMNS = intPreferencesKey("grid_portrait_columns")
        val GRID_PORTRAIT_ROWS = intPreferencesKey("grid_portrait_rows")
        val GRID_LANDSCAPE_COLUMNS = intPreferencesKey("grid_landscape_columns")
        val GRID_LANDSCAPE_ROWS = intPreferencesKey("grid_landscape_rows")
        val COLLATION = stringPreferencesKey("collation_language_tag")
        val HIDE_CAPTIONS = booleanPreferencesKey("hide_captions_in_sentence")
        val AUTO_CLEAR = booleanPreferencesKey("auto_clear_after_playback")
    }
}
