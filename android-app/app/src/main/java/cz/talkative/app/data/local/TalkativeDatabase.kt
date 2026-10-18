package cz.talkative.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Lokální databáze (zdroj pravdy). Schéma se exportuje do `app/schemas`. */
@Database(
    entities = [MetaEntity::class, FolderEntity::class, WordEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class TalkativeDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        const val NAME = "talkative.db"
    }
}
