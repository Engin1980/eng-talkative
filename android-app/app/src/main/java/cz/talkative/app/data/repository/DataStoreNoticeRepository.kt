package cz.talkative.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import cz.talkative.app.domain.notice.NoticeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Odsouhlasená verze oznámení ve stejném DataStore jako konfigurace (jiný klíč, konfigurace se jí nedotýká). */
class DataStoreNoticeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NoticeRepository {

    override val acknowledgedVersion: Flow<Int?> = dataStore.data.map { it[ACKNOWLEDGED_VERSION] }

    override suspend fun acknowledge(version: Int) {
        dataStore.edit { it[ACKNOWLEDGED_VERSION] = version }
    }

    private companion object {
        val ACKNOWLEDGED_VERSION = intPreferencesKey("notice_acknowledged_version")
    }
}
