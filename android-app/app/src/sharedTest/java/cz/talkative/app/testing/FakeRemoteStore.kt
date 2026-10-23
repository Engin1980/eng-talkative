package cz.talkative.app.testing

import cz.talkative.app.domain.sync.MediaRef
import cz.talkative.app.domain.sync.RemoteMedia
import cz.talkative.app.domain.sync.RemoteStore
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.domain.sync.SyncException
import java.io.File

/** Vzdálené úložiště v paměti pro testy; média mají obsah jako bajty a čas změny nastavitelný z testu. */
class FakeRemoteStore : RemoteStore {
    var metadata: String? = null
    val media = mutableMapOf<MediaRef, ByteArray>()
    val modified = mutableMapOf<MediaRef, Long>()

    /** Když je nastavené, každé volání skončí touto chybou (výpadek sítě apod.). */
    var failWith: SyncError? = null

    /** Selže až po zadaném počtu úspěšných volání (výpadek sítě uprostřed synchronizace). */
    var failAfterCalls: Int? = null
    private var calls = 0

    var metadataWrites = 0
        private set
    val uploads = mutableListOf<MediaRef>()
    val downloads = mutableListOf<MediaRef>()
    val deletes = mutableListOf<MediaRef>()

    /** Čas, který dostanou nově nahraná média. */
    var now = 0L

    private fun check() {
        failWith?.let { throw SyncException(it, "Test: vzdálené úložiště selhalo") }
        failAfterCalls?.let { limit ->
            if (calls >= limit) throw SyncException(SyncError.NO_NETWORK, "Test: výpadek uprostřed synchronizace")
        }
        calls++
    }

    fun addMedia(ref: MediaRef, bytes: ByteArray = ByteArray(4) { 1 }, modifiedMillis: Long = now) {
        media[ref] = bytes
        modified[ref] = modifiedMillis
    }

    override suspend fun readMetadata(): String? {
        check()
        return metadata
    }

    override suspend fun writeMetadata(json: String) {
        check()
        metadata = json
        metadataWrites++
    }

    override suspend fun listMedia(): List<RemoteMedia> {
        check()
        return media.keys.map { RemoteMedia(it, modified[it] ?: 0L) }
    }

    override suspend fun uploadMedia(ref: MediaRef, file: File) {
        check()
        media[ref] = file.readBytes()
        modified[ref] = now
        uploads += ref
    }

    override suspend fun downloadMedia(ref: MediaRef, target: File) {
        check()
        val bytes = media[ref] ?: throw SyncException(SyncError.BAD_RESPONSE, "Test: ${ref.name} na Drive není")
        target.writeBytes(bytes)
        downloads += ref
    }

    override suspend fun deleteMedia(ref: MediaRef) {
        check()
        media.remove(ref)
        modified.remove(ref)
        deletes += ref
    }
}
