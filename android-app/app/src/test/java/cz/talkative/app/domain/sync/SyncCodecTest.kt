package cz.talkative.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncCodecTest {

    private val items = listOf(
        SyncItem("f1", SyncItemKind.FOLDER, "default", "Jídlo", null, 0, 10),
        SyncItem("w1", SyncItemKind.WORD, "default", "Chléb", "f1", 1, 20, false, "a.png", "chleba", "b.m4a"),
        SyncItem("w2", SyncItemKind.WORD, "default", "Pryč", null, 2, 30, deleted = true),
    )

    @Test
    fun roundTrip_keepsEverythingIncludingTombstonesAndNulls() {
        assertEquals(items, SyncCodec.decode(SyncCodec.encode(items)))
    }

    @Test
    fun emptyDictionary_roundTrips() {
        assertTrue(SyncCodec.decode(SyncCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun unknownFields_areIgnored() {
        val text = """{"schemaVersion":1,"neco":"nového","items":[{"id":"w","kind":"WORD","profileId":"default","title":"x","parentId":null,"sortOrder":0,"updatedAt":5,"budoucí":1}]}"""

        assertEquals("x", SyncCodec.decode(text).single().title)
    }

    @Test
    fun brokenJson_isBadResponse() {
        for (text in listOf("", "není json", """{"schemaVersion":1}""", """{"schemaVersion":1,"items":[{"id":1}]}""")) {
            try {
                SyncCodec.decode(text)
                fail("Mělo selhat: $text")
            } catch (e: SyncException) {
                assertEquals(SyncError.BAD_RESPONSE, e.error)
            }
        }
    }

    @Test
    fun newerSchema_isRefused() {
        try {
            SyncCodec.decode("""{"schemaVersion":${SyncCodec.SCHEMA_VERSION + 1},"items":[]}""")
            fail("Mělo selhat")
        } catch (e: SyncException) {
            assertEquals(SyncError.NEWER_VERSION, e.error)
        }
    }
}
