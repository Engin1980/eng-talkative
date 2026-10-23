package cz.talkative.app.domain.sync

import cz.talkative.app.testing.FakeRemoteStore
import cz.talkative.app.testing.FakeSyncDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncDictionaryUseCaseTest {

    private val remote = FakeRemoteStore()
    private val a = FakeSyncDevice(remote)
    private val b = FakeSyncDevice(remote)

    private fun folder(id: String, title: String = id, updatedAt: Long = 1_000_000L, deleted: Boolean = false) =
        SyncItem(id, SyncItemKind.FOLDER, "default", title, null, 0, updatedAt, deleted)

    private fun word(id: String, title: String = id, updatedAt: Long = 1_000_000L, image: String? = null, audio: String? = null, deleted: Boolean = false) =
        SyncItem(id, SyncItemKind.WORD, "default", title, null, 0, updatedAt, deleted, image, null, audio)

    private fun image(name: String) = MediaRef(MediaKind.IMAGE, name)
    private fun audio(name: String) = MediaRef(MediaKind.AUDIO, name)

    private fun sync(device: FakeSyncDevice) = runBlocking { device.useCase()() }

    private fun failure(device: FakeSyncDevice): SyncError {
        try {
            sync(device)
        } catch (e: SyncException) {
            return e.error
        }
        fail("Mělo selhat")
        error("nedosažitelné")
    }

    private fun remoteItems() = SyncCodec.decode(remote.metadata!!)

    @Test
    fun firstSync_uploadsItemsAndMedia_andASecondOneChangesNothing() {
        a.put(folder("f"))
        a.put(word("w", image = "w.png", audio = "w.m4a"))
        a.media.put(image("w.png"))
        a.media.put(audio("w.m4a"))

        val outcome = sync(a)

        assertTrue(outcome.metadataUploaded)
        assertEquals(2, outcome.mediaUploaded)
        assertEquals(listOf("f", "w"), remoteItems().map { it.id })
        assertEquals(setOf(image("w.png"), audio("w.m4a")), remote.media.keys)

        val writes = remote.metadataWrites
        val second = sync(a)
        assertFalse(second.metadataUploaded)
        assertEquals(0, second.mediaUploaded + second.mediaDownloaded + second.itemsChangedLocally)
        assertEquals(writes, remote.metadataWrites)
        assertEquals(2, remote.uploads.size)
    }

    @Test
    fun emptyDictionaries_writeNothing() {
        val outcome = sync(a)

        assertFalse(outcome.metadataUploaded)
        assertNull(remote.metadata)
    }

    @Test
    fun secondDevice_pullsTheSameDictionaryWithMedia() {
        a.put(folder("f", "Jídlo"))
        a.put(word("w", "Chléb", image = "w.png"))
        a.media.put(image("w.png"), byteArrayOf(5, 6, 7))
        sync(a)

        val outcome = sync(b)

        assertEquals(2, outcome.itemsChangedLocally)
        assertEquals(1, outcome.mediaDownloaded)
        assertEquals(a.local.items, b.local.items) // včetně pole updatedAt, nic se nepřepsalo
        assertEquals(byteArrayOf(5, 6, 7).toList(), b.media.files[image("w.png")]!!.toList())
        assertTrue(b.local.applyCalls == 1)
    }

    @Test
    fun editOnOneDevice_reachesTheOther_andNothingPingPongs() {
        a.put(word("w", "staré", updatedAt = 1_000_000L))
        sync(a)
        sync(b)

        b.put(word("w", "nové", updatedAt = 2_000_000L))
        sync(b)
        sync(a)

        assertEquals("nové", a.local.items.getValue("w").title)
        assertEquals(2_000_000L, a.local.items.getValue("w").updatedAt)
        val writes = remote.metadataWrites
        sync(a)
        sync(b)
        assertEquals(writes, remote.metadataWrites)
    }

    @Test
    fun deletionPropagates_andTheOrphanFileIsCleanedOnTheOtherDevice() {
        a.put(word("w", image = "w.png"))
        a.media.put(image("w.png"))
        sync(a)
        sync(b)
        assertNotNull(b.media.files[image("w.png")])

        // zařízení A slovíčko smaže: tombstone bez odkazů na média
        a.put(word("w", updatedAt = 2_000_000L, deleted = true))
        a.media.removeOrphans()
        sync(a)
        sync(b)

        // soubor smazalo samo použití synchronizace, bez plošného úklidu
        assertTrue(b.local.items.getValue("w").deleted)
        assertNull(b.media.files[image("w.png")])
    }

    @Test
    fun syncNeverDeletesFilesItDidNotMakeUnused() {
        // soubory, které si administrátor vybral v otevřené editaci a ještě neuložil: nic na ně neodkazuje
        b.media.put(image("rozepsany-obrazek.png"))
        b.media.put(audio("rozepsana-nahravka.m4a"))
        a.put(word("w", image = "w.png"))
        a.media.put(image("w.png"))
        sync(a)

        sync(b)

        assertNotNull(b.media.files[image("rozepsany-obrazek.png")])
        assertNotNull(b.media.files[audio("rozepsana-nahravka.m4a")])
        assertNotNull(b.media.files[image("w.png")])
    }

    @Test
    fun replacedMediaOfAWord_isDeletedLocally_afterTheRemoteChangeWins() {
        a.put(word("w", updatedAt = 1_000_000L, image = "stary.png"))
        a.media.put(image("stary.png"))
        sync(a)
        sync(b)
        a.put(word("w", updatedAt = 2_000_000L, image = "novy.png"))
        a.media.put(image("novy.png"))
        sync(a)

        sync(b)

        assertEquals(setOf(image("novy.png")), b.media.files.keys)
    }

    @Test
    fun deleteVsEdit_theNewerChangeWinsOnBothDevices() {
        a.put(word("w", "původní"))
        sync(a)
        sync(b)

        a.put(word("w", updatedAt = 3_000_000L, deleted = true))
        b.put(word("w", "upraveno později", updatedAt = 4_000_000L))
        sync(a)
        sync(b)
        sync(a)

        assertFalse(a.local.items.getValue("w").deleted)
        assertEquals("upraveno později", a.local.items.getValue("w").title)
        assertEquals(a.local.items, b.local.items)
    }

    @Test
    fun concurrentEdits_endWithTheSameWinnerOnBothDevices() {
        a.put(folder("f", "společné"))
        sync(a)
        sync(b)

        a.put(folder("f", "od A", updatedAt = 2_000_000L))
        b.put(folder("f", "od B", updatedAt = 2_000_001L))
        sync(a)
        sync(b)
        sync(a)

        assertEquals("od B", a.local.items.getValue("f").title)
        assertEquals(a.local.items, b.local.items)
    }

    @Test
    fun networkFailureWhileDownloading_leavesLocalDataUntouched() {
        a.put(word("w1", image = "1.png"))
        a.put(word("w2", image = "2.png"))
        a.media.put(image("1.png"))
        a.media.put(image("2.png"))
        sync(a)

        // metadata, výpis médií a první stažení projdou, druhé stažení selže
        remote.failAfterCalls = 3
        assertEquals(SyncError.NO_NETWORK, failure(b))

        assertTrue(b.local.items.isEmpty())
        assertTrue(b.media.files.isEmpty())
        assertEquals(0, b.local.applyCalls)
        assertFalse(b.stagingDir.exists()) // dočasné soubory se uklidily
    }

    @Test
    fun failureWhileWritingMetadata_leavesLocalDataUntouched_andTheRetryWorks() {
        a.put(word("w", "z A"))
        sync(a)
        b.put(word("x", "z B"))

        // čtení metadat, výpis médií projdou, zápis metadat selže
        remote.failAfterCalls = 2
        assertEquals(SyncError.NO_NETWORK, failure(b))
        assertEquals(setOf("x"), b.local.items.keys)

        remote.failAfterCalls = null
        sync(b)
        assertEquals(setOf("w", "x"), b.local.items.keys)
        assertEquals(setOf("w", "x"), remoteItems().map { it.id }.toSet())
    }

    @Test
    fun localWriteFailure_deletesTheRestoredFiles_andReportsLocalStorage() {
        a.put(word("w", image = "w.png"))
        a.media.put(image("w.png"))
        sync(a)
        b.local.applyFailure = SyncException(SyncError.LOCAL_STORAGE)

        assertEquals(SyncError.LOCAL_STORAGE, failure(b))

        assertTrue(b.media.files.isEmpty())
        assertTrue(b.local.items.isEmpty())
    }

    @Test
    fun restoreFailure_isLocalStorageError() {
        a.put(word("w", image = "w.png"))
        a.media.put(image("w.png"))
        sync(a)
        b.media.failRestore = true

        assertEquals(SyncError.LOCAL_STORAGE, failure(b))
        assertTrue(b.local.items.isEmpty())
    }

    @Test
    fun notSignedIn_orOffline_changesNothing() {
        a.put(word("w"))
        remote.failWith = SyncError.NOT_SIGNED_IN

        assertEquals(SyncError.NOT_SIGNED_IN, failure(a))
        assertNull(remote.metadata)
        assertEquals(setOf("w"), a.local.items.keys)
    }

    @Test
    fun metadataFromNewerVersion_isRefused_andNothingIsOverwritten() {
        remote.metadata = """{"schemaVersion":99,"items":[]}"""
        a.put(word("w"))

        assertEquals(SyncError.NEWER_VERSION, failure(a))

        assertEquals("""{"schemaVersion":99,"items":[]}""", remote.metadata)
    }

    @Test
    fun brokenMetadata_isBadResponse_andLocalDataSurvives() {
        remote.metadata = "poškozeno"
        a.put(word("w"))

        assertEquals(SyncError.BAD_RESPONSE, failure(a))
        assertEquals(setOf("w"), a.local.items.keys)
    }

    @Test
    fun missingMediaOnBothSides_isReported_butSyncSucceeds() {
        a.put(word("w", image = "ztracene.png"))

        val outcome = sync(a)

        assertEquals(1, outcome.missingMedia)
        assertTrue(outcome.metadataUploaded)
    }

    @Test
    fun oldUnusedRemoteMedia_isDeleted_recentOneIsKept() {
        remote.now = a.now
        remote.addMedia(image("stary.png"), modifiedMillis = a.now - SyncMerge.REMOTE_MEDIA_MIN_AGE_MILLIS - 1)
        remote.addMedia(image("novy.png"), modifiedMillis = a.now - 1_000)
        a.put(word("w"))

        sync(a)

        assertEquals(setOf(image("novy.png")), remote.media.keys)
    }

    @Test
    fun failedRemoteGarbageCollection_doesNotFailTheSync() {
        remote.addMedia(image("stary.png"), modifiedMillis = 0)
        a.now = SyncMerge.REMOTE_MEDIA_MIN_AGE_MILLIS + 10
        a.put(word("w", updatedAt = a.now))
        // metadata, výpis médií a zápis proběhnou, mazání selže
        remote.failAfterCalls = 3

        val outcome = sync(a)

        assertTrue(outcome.metadataUploaded)
        assertEquals(setOf("w"), remoteItems().map { it.id }.toSet())
    }

    @Test
    fun expiredTombstones_areRemovedFromBothSides() {
        val old = word("stary", updatedAt = 1_000, deleted = true)
        a.now = SyncMerge.TOMBSTONE_TTL_MILLIS + 5_000
        a.put(old)
        a.put(word("zivy", updatedAt = a.now))
        remote.metadata = SyncCodec.encode(listOf(old))

        sync(a)

        assertEquals(setOf("zivy"), a.local.items.keys)
        assertEquals(listOf("zivy"), remoteItems().map { it.id })
    }
}
