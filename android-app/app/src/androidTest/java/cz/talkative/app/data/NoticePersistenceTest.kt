package cz.talkative.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.repository.DataStoreConfigRepository
import cz.talkative.app.data.repository.DataStoreNoticeRepository
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.notice.NOTICE_VERSION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Odsouhlasení oznámení se skutečně ukládá do souboru a nerozbíjí konfiguraci ve stejném úložišti. */
@RunWith(AndroidJUnit4::class)
class NoticePersistenceTest {

    @Test
    fun acknowledgement_survivesANewDataStoreInstance_andDoesNotTouchTheConfig() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "notice-test-${System.nanoTime()}.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(scope = firstScope) { file }
        val notice = DataStoreNoticeRepository(firstStore)
        val config = DataStoreConfigRepository(firstStore)
        assertNull(notice.acknowledgedVersion.first())
        config.update { it.copy(addWordMode = AddWordMode.DRAG) }
        notice.acknowledge(NOTICE_VERSION)
        config.update { it.copy(speakWordOnTap = true) }
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondStore = PreferenceDataStoreFactory.create(scope = secondScope) { file }
        val version = DataStoreNoticeRepository(secondStore).acknowledgedVersion.first()
        val loaded = DataStoreConfigRepository(secondStore).config.first()
        secondScope.cancel()
        file.delete()

        assertEquals(NOTICE_VERSION, version)
        assertEquals(AddWordMode.DRAG, loaded.addWordMode)
        assertEquals(true, loaded.speakWordOnTap)
    }
}
