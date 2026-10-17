package cz.talkative.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Lokální databáze (zdroj pravdy). Schéma se exportuje do `app/schemas`.
 * Další entity (složky, slovíčka…) přibudou v dalších krocích plánu.
 */
@Database(entities = [MetaEntity::class], version = 1, exportSchema = true)
abstract class TalkativeDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "talkative.db"
    }
}
