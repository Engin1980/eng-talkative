package cz.talkative.app.testing

import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import kotlinx.coroutines.CompletableDeferred
import java.io.File

/** Úložiště MP3 v paměti pro testy; "soubory" jsou jen názvy. */
class FakeAudioStore : AudioStore {
    val files = mutableSetOf<String>()
    private var counter = 0

    /** Když je nastavené, ukládání selže s touto chybou. */
    var failWith: AudioError? = null

    /** Když je nastavené, ukládání čeká, až se brána dokončí (řízení časování v testech). */
    var gate: CompletableDeferred<Unit>? = null

    /** Zaregistruje existující soubor (jako by ho předtím někdo uložil). */
    fun add(name: String): String {
        files += name
        return name
    }

    override suspend fun saveFromUri(uri: String): AudioSaveResult {
        gate?.await()
        failWith?.let { return AudioSaveResult.Failed(it) }
        val name = "fake-${counter++}.mp3"
        files += name
        return AudioSaveResult.Saved(name)
    }

    override fun resolve(fileName: String): File? = if (fileName in files) File(fileName) else null

    override suspend fun delete(fileName: String) {
        files -= fileName
    }

    override suspend fun deleteAllExcept(keep: Set<String>) {
        files.retainAll(keep)
    }
}
