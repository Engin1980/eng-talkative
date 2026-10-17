package cz.talkative.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Složka (adjacency list: `parentId` je ID rodiče, kořen má `null`).
 * Záměrně bez cizího klíče: odkaz na neexistující složku se má umět zobrazit jako nekonzistence, ne selhat.
 * Smazaná složka zůstává jako tombstone (`deleted = true`, nový `updatedAt`) kvůli budoucí synchronizaci.
 */
@Entity(tableName = "folders", indices = [Index("profileId", "parentId")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val title: String,
    val parentId: String?,
    val sortOrder: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
