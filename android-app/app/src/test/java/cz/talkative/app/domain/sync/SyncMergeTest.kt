package cz.talkative.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    // „teď“ = přesně doba platnosti tombstonů, takže všechna malá čísla v testech jsou čerstvá
    private val now = SyncMerge.TOMBSTONE_TTL_MILLIS

    private fun word(id: String, title: String = id, updatedAt: Long = 1_000, deleted: Boolean = false, image: String? = null, audio: String? = null) =
        SyncItem(id, SyncItemKind.WORD, "default", title, null, 0, updatedAt, deleted, image, null, audio)

    private fun folder(id: String, title: String = id, updatedAt: Long = 1_000, deleted: Boolean = false) =
        SyncItem(id, SyncItemKind.FOLDER, "default", title, null, 0, updatedAt, deleted)

    private fun merge(local: List<SyncItem>, remote: List<SyncItem>) = SyncMerge.merge(local, remote, now)

    @Test
    fun newItemOnEitherSide_endsUpOnBoth() {
        val result = merge(listOf(word("a")), listOf(word("b")))

        assertEquals(listOf("a", "b"), result.merged.map { it.id })
        assertEquals(listOf("b"), result.changedLocally.map { it.id }) // "a" místně už je
        assertTrue(result.remoteOutdated) // "a" na Drive chybí
    }

    @Test
    fun identicalSides_changeNothing() {
        val items = listOf(word("a"), folder("f"))

        val result = merge(items, items)

        assertTrue(result.changedLocally.isEmpty())
        assertFalse(result.remoteOutdated)
        assertTrue(result.purgeIds.isEmpty())
    }

    @Test
    fun editOnOneSide_newerWins() {
        val result = merge(listOf(word("a", "nové", updatedAt = 2_000)), listOf(word("a", "staré", updatedAt = 1_000)))

        assertEquals("nové", result.merged.single().title)
        assertTrue(result.changedLocally.isEmpty())
        assertTrue(result.remoteOutdated)
    }

    @Test
    fun concurrentEdits_lastWriteWins_onBothSides() {
        val a = word("a", "z prvního zařízení", updatedAt = 2_000)
        val b = word("a", "z druhého zařízení", updatedAt = 3_000)

        assertEquals("z druhého zařízení", merge(listOf(a), listOf(b)).merged.single().title)
        assertEquals("z druhého zařízení", merge(listOf(b), listOf(a)).merged.single().title)
    }

    @Test
    fun deleteVsEdit_newerChangeWins() {
        val deletedLater = word("a", updatedAt = 3_000, deleted = true)
        val editedEarlier = word("a", "upraveno", updatedAt = 2_000)
        assertTrue(merge(listOf(editedEarlier), listOf(deletedLater)).merged.single().deleted)

        val editedLater = word("a", "upraveno", updatedAt = 4_000)
        val result = merge(listOf(editedLater), listOf(deletedLater))
        assertFalse(result.merged.single().deleted)
        assertEquals("upraveno", result.merged.single().title)
    }

    @Test
    fun tombstoneFromRemote_isAppliedLocally() {
        val result = merge(listOf(word("a", updatedAt = 1_000)), listOf(word("a", updatedAt = 2_000, deleted = true)))

        assertEquals(listOf("a"), result.changedLocally.map { it.id })
        assertTrue(result.changedLocally.single().deleted)
    }

    @Test
    fun equalTimestamps_deleteWins_andOtherwiseResultIsTheSameInBothDirections() {
        val deleted = word("a", updatedAt = 2_000, deleted = true)
        val edited = word("a", "upraveno", updatedAt = 2_000)
        assertTrue(merge(listOf(edited), listOf(deleted)).merged.single().deleted)
        assertTrue(merge(listOf(deleted), listOf(edited)).merged.single().deleted)

        val x = word("a", "x", updatedAt = 2_000)
        val y = word("a", "y", updatedAt = 2_000)
        assertEquals(merge(listOf(x), listOf(y)).merged, merge(listOf(y), listOf(x)).merged)
    }

    @Test
    fun merge_isCommutative_andIdempotent() {
        val local = listOf(word("a", "l", 2_000), word("b", updatedAt = 1_000), folder("f", "lf", 5_000), word("c", updatedAt = 3_000, deleted = true))
        val remote = listOf(word("a", "r", 3_000), word("d"), folder("f", "rf", 4_000), word("c", "živé", 2_000))

        val ab = merge(local, remote)
        val ba = merge(remote, local)
        assertEquals(ab.merged, ba.merged)

        val again = merge(ab.merged, ab.merged)
        assertEquals(ab.merged, again.merged)
        assertTrue(again.changedLocally.isEmpty())
        assertFalse(again.remoteOutdated)
        // sloučení výsledku s kteroukoli stranou původní výsledek nezmění
        assertEquals(ab.merged, merge(ab.merged, local).merged)
        assertEquals(ab.merged, merge(ab.merged, remote).merged)
    }

    @Test
    fun expiredTombstones_areDroppedFromBothSides_recentOnesStay() {
        val old = word("old", updatedAt = now - SyncMerge.TOMBSTONE_TTL_MILLIS - 1, deleted = true)
        val recent = word("recent", updatedAt = now - 1_000, deleted = true)

        val result = merge(listOf(old, recent), listOf(old))

        assertEquals(listOf("recent"), result.merged.map { it.id })
        assertEquals(setOf("old"), result.purgeIds)
        assertTrue(result.remoteOutdated) // Drive se zbaví prošlého tombstonu
        assertEquals(listOf("recent"), merge(result.merged, result.merged).merged.map { it.id }) // idempotence
    }

    @Test
    fun expiredTombstoneOnlyOnRemote_isNotCopiedLocally() {
        val old = word("old", updatedAt = now - SyncMerge.TOMBSTONE_TTL_MILLIS - 1, deleted = true)

        val result = merge(emptyList(), listOf(old))

        assertTrue(result.merged.isEmpty())
        assertTrue(result.changedLocally.isEmpty())
        assertTrue(result.purgeIds.isEmpty())
    }

    // ---- média

    private fun image(name: String) = MediaRef(MediaKind.IMAGE, name)
    private fun audio(name: String) = MediaRef(MediaKind.AUDIO, name)

    @Test
    fun referencedMedia_ignoresDeletedWordsAndFolders() {
        val items = listOf(
            word("a", image = "a.png", audio = "a.m4a"),
            word("b", deleted = true, image = "b.png"),
            folder("f"),
        )

        assertEquals(setOf(image("a.png"), audio("a.m4a")), referencedMedia(items))
    }

    @Test
    fun mediaPlan_uploadsLocalOnly_downloadsRemoteOnly_reportsMissing() {
        val referenced = setOf(image("jen-misto"), image("jen-drive"), image("oboji"), image("nikde"))

        val plan = SyncMerge.planMedia(
            referenced = referenced,
            local = setOf(image("jen-misto"), image("oboji"), image("nepouzite")),
            remote = setOf(image("jen-drive"), image("oboji")),
        )

        assertEquals(setOf(image("jen-misto")), plan.toUpload)
        assertEquals(setOf(image("jen-drive")), plan.toDownload)
        assertEquals(setOf(image("nikde")), plan.missing)
    }

    @Test
    fun remoteGarbage_isOnlyUnreferencedAndOldEnough() {
        val old = now - SyncMerge.REMOTE_MEDIA_MIN_AGE_MILLIS - 1
        val remote = listOf(
            RemoteMedia(image("pouzity"), old),
            RemoteMedia(image("stary-nepouzity"), old),
            RemoteMedia(image("novy-nepouzity"), now - 1_000),
        )

        val garbage = SyncMerge.remoteGarbage(remote, setOf(image("pouzity")), now)

        assertEquals(listOf(image("stary-nepouzity")), garbage)
    }
}
