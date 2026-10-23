package cz.talkative.app.testing

import cz.talkative.app.domain.sync.MediaKind
import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.RemoteStore
import cz.talkative.app.domain.sync.SyncDictionaryUseCase
import cz.talkative.app.domain.sync.SyncItem
import cz.talkative.app.domain.sync.SyncLocalStore
import cz.talkative.app.domain.sync.SyncMediaStore
import cz.talkative.app.domain.sync.SyncStagingDir
import cz.talkative.app.domain.util.Clock
import java.io.File

/** Místní databáze v paměti pro testy synchronizace (včetně tombstonů). */
class FakeSyncLocalStore : SyncLocalStore {
    val items = linkedMapOf<String, SyncItem>()
    var applyCalls = 0
        private set

    /** Když je nastavené, `apply` skončí touto výjimkou (selhání zápisu do databáze). */
    var applyFailure: Exception? = null

    override suspend fun readAll(): List<SyncItem> = items.values.toList()

    override suspend fun apply(changed: List<SyncItem>, purgeIds: Set<String>) {
        applyFailure?.let { throw it }
        applyCalls++
        changed.forEach { items[it.id] = it }
        purgeIds.forEach { items.remove(it) }
    }
}

/** Média v paměti; zároveň vědí, který soubor je referencovaný, aby šel ověřit úklid osiřelých. */
class FakeSyncMediaStore(private val local: FakeSyncLocalStore) : SyncMediaStore {
    val files = mutableMapOf<MediaRef, ByteArray>()
    var failRestore = false

    fun put(ref: MediaRef, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        files[ref] = bytes
    }

    override fun file(ref: MediaRef): File? {
        val bytes = files[ref] ?: return null
        return File.createTempFile("media", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
    }

    override suspend fun restore(ref: MediaRef, source: File): Boolean {
        if (failRestore) return false
        files[ref] = source.readBytes()
        return true
    }

    override suspend fun delete(ref: MediaRef) {
        files.remove(ref)
    }

    /** Smaže soubory, na které nic neodkazuje (jako skutečný úklid osiřelých). */
    fun removeOrphans() {
        val referenced = local.items.values.filter { !it.deleted }.flatMap {
            listOfNotNull(it.imageFile?.let { n -> MediaRef(MediaKind.IMAGE, n) }, it.audioFile?.let { n -> MediaRef(MediaKind.AUDIO, n) })
        }.toSet()
        files.keys.retainAll(referenced)
    }
}

/** Jedno „zařízení“ pro testy synchronizace: vlastní databáze a soubory, společné vzdálené úložiště. */
class FakeSyncDevice(val remote: RemoteStore, var now: Long = 1_000_000L) {
    val local = FakeSyncLocalStore()
    val media = FakeSyncMediaStore(local)
    val stagingDir: File = File.createTempFile("staging", "").apply { delete() }

    fun useCase() = SyncDictionaryUseCase(
        local, media, remote, Clock { now }, SyncStagingDir(stagingDir),
    )

    fun put(item: SyncItem) {
        local.items[item.id] = item
    }
}
