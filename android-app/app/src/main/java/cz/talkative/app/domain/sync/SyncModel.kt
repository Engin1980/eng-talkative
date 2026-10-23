package cz.talkative.app.domain.sync

import kotlinx.serialization.Serializable

enum class SyncItemKind { FOLDER, WORD }

/**
 * Složka nebo slovíčko v podobě, v jaké se synchronizuje (včetně smazaných, tj. tombstonů). Pole [imageFile],
 * [alternateText] a [audioFile] mají jen slovíčka. Obě strany si ukládají [updatedAt] beze změny, takže stejná
 * položka na obou zařízeních je identická.
 */
@Serializable
data class SyncItem(
    val id: String,
    val kind: SyncItemKind,
    val profileId: String,
    val title: String,
    val parentId: String?,
    val sortOrder: Int,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val imageFile: String? = null,
    val alternateText: String? = null,
    val audioFile: String? = null,
)

enum class MediaKind { IMAGE, AUDIO }

/**
 * Soubor média podle druhu a názvu. Název se po uložení nikdy nemění (každé uložení dostane nové UUID), takže
 * stejný název znamená stejný obsah a synchronizace médií je jen rozdíl množin názvů.
 */
@Serializable
data class MediaRef(val kind: MediaKind, val name: String)

/** Média, na která se odkazují nesmazaná slovíčka. */
fun referencedMedia(items: Collection<SyncItem>): Set<MediaRef> = buildSet {
    for (item in items) {
        if (item.deleted || item.kind != SyncItemKind.WORD) continue
        item.imageFile?.let { add(MediaRef(MediaKind.IMAGE, it)) }
        item.audioFile?.let { add(MediaRef(MediaKind.AUDIO, it)) }
    }
}
