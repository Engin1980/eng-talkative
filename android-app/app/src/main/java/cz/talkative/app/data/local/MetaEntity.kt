package cz.talkative.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Jednoduchý klíč–hodnota záznam pro drobná metadata aplikace (např. aktuální profil). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
) {
    companion object {
        const val KEY_CURRENT_PROFILE = "current_profile"
    }
}
