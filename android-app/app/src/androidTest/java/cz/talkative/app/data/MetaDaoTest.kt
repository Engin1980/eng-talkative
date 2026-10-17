package cz.talkative.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.MetaEntity
import cz.talkative.app.data.local.TalkativeDatabase
import cz.talkative.app.data.repository.RoomProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetaDaoTest {

    private lateinit var database: TalkativeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TalkativeDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertIfAbsent_doesNotOverwriteExistingValue() = runTest {
        val dao = database.metaDao()

        dao.insertIfAbsent(MetaEntity("k", "first"))
        dao.insertIfAbsent(MetaEntity("k", "second"))

        assertEquals("first", dao.observeValue("k").first())
    }

    @Test
    fun profileRepository_createsDefaultProfileOnFirstRead() = runTest {
        val repository = RoomProfileRepository(database.metaDao())

        assertEquals("default", repository.observeCurrentProfile().first().id)
        assertEquals("default", database.metaDao().observeValue(MetaEntity.KEY_CURRENT_PROFILE).first())
    }
}
