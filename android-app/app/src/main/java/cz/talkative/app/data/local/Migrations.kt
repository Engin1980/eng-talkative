package cz.talkative.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Verze 1 → 2: tabulky složek a slovíček (krok 1.1 plánu). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("folders", "words")) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `$table` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `parentId` TEXT, `sortOrder` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_${table}_profileId_parentId` ON `$table` (`profileId`, `parentId`)",
            )
        }
    }
}
