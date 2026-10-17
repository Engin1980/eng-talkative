package cz.talkative.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Slovíčko; pravidla stejná jako u [FolderEntity]. [imageFile] je název souboru v úložišti obrázků. Zvuk přibude v kroku 5. */
@Entity(tableName = "words", indices = [Index("profileId", "parentId")])
data class WordEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val title: String,
    val parentId: String?,
    val sortOrder: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val imageFile: String? = null,
)
