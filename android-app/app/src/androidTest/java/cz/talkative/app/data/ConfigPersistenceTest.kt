package cz.talkative.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.repository.DataStoreConfigRepository
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Skutečné ukládání konfigurace do souboru (zápis → nová instance DataStore → čtení). */
@RunWith(AndroidJUnit4::class)
class ConfigPersistenceTest {

    @Test
    fun savedConfig_isReadBackByNewDataStoreOnTheSameFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "config-test-${System.nanoTime()}.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = DataStoreConfigRepository(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        first.update { it.copy(addWordMode = AddWordMode.DRAG, speakWordOnTap = true, collationLanguageTag = "sk") }
        first.update { it.copy(adminHoldMillis = 5_000L) }
        first.update {
            it.copy(
                allowReorderInSentence = false,
                removeHoldMillis = 2_500L,
                gridPortrait = GridSize(2, 4),
                gridLandscape = GridSize(5, 2),
                hideCaptionsInSentence = true,
                autoClearAfterPlayback = true,
            )
        }
        firstScope.cancel() // jedna aktivní instance na soubor

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = DataStoreConfigRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
        val config = second.config.first()
        secondScope.cancel()
        file.delete()

        assertEquals(
            AppConfig(
                addWordMode = AddWordMode.DRAG,
                speakWordOnTap = true,
                collationLanguageTag = "sk",
                adminHoldMillis = 5_000L,
                allowReorderInSentence = false,
                removeHoldMillis = 2_500L,
                gridPortrait = GridSize(2, 4),
                gridLandscape = GridSize(5, 2),
                hideCaptionsInSentence = true,
                autoClearAfterPlayback = true,
            ),
            config,
        )
    }
}
