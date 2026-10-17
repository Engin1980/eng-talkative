package cz.talkative.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.MIGRATION_1_2
import cz.talkative.app.data.local.TalkativeDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TalkativeDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsMetaAndCreatesEmptyDictionaryTables() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO meta (`key`, value) VALUES ('current_profile', 'default')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT value FROM meta WHERE `key` = 'current_profile'").use {
            it.moveToFirst()
            assertEquals("default", it.getString(0))
        }
        for (table in listOf("folders", "words")) {
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
