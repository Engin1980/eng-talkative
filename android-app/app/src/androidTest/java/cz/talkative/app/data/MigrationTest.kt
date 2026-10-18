package cz.talkative.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.local.MIGRATION_1_2
import cz.talkative.app.data.local.MIGRATION_2_3
import cz.talkative.app.data.local.MIGRATION_3_4
import cz.talkative.app.data.local.TalkativeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun migrate2To3_keepsWordsAndAddsEmptyImageColumn() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL("INSERT INTO words (id, profileId, title, parentId, sortOrder, updatedAt, deleted) VALUES ('w1', 'default', 'chci', NULL, 0, 5, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

        db.query("SELECT title, imageFile FROM words WHERE id = 'w1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("chci", it.getString(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun migrate1To3_runsAllMigrationsInOrder() {
        helper.createDatabase(DB_NAME, 1).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        db.query("SELECT COUNT(*) FROM words").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate3To4_keepsWordsAndAddsEmptySoundColumns() {
        helper.createDatabase(DB_NAME, 3).apply {
            execSQL("INSERT INTO words (id, profileId, title, parentId, sortOrder, updatedAt, deleted, imageFile) VALUES ('w1', 'default', 'chci', NULL, 0, 5, 0, 'a.jpg')")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

        db.query("SELECT title, imageFile, alternateText, audioFile FROM words WHERE id = 'w1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("chci", it.getString(0))
            assertEquals("a.jpg", it.getString(1))
            assertTrue(it.isNull(2))
            assertTrue(it.isNull(3))
        }
    }
}
