package cz.talkative.app.domain.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Soubor s metadaty na Drive: verze schématu a všechny položky včetně tombstonů. */
@Serializable
data class SyncDocument(val schemaVersion: Int, val items: List<SyncItem>)

/** (De)serializace metadat synchronizace. Neznámá pole se ignorují, novější verze schématu se odmítne. */
object SyncCodec {
    const val SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(items: List<SyncItem>): String = json.encodeToString(SyncDocument.serializer(), SyncDocument(SCHEMA_VERSION, items))

    /** @throws SyncException `BAD_RESPONSE` pro nečitelný JSON, `NEWER_VERSION` pro novější schéma */
    fun decode(text: String): List<SyncItem> {
        val document = try {
            json.decodeFromString(SyncDocument.serializer(), text)
        } catch (e: SerializationException) {
            throw SyncException(SyncError.BAD_RESPONSE, "Metadata synchronizace nejdou přečíst", e)
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncError.BAD_RESPONSE, "Metadata synchronizace nejdou přečíst", e)
        }
        if (document.schemaVersion > SCHEMA_VERSION) throw SyncException(SyncError.NEWER_VERSION)
        return document.items
    }
}
