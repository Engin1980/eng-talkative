package cz.talkative.app.testing

import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioPlayer
import kotlinx.coroutines.CompletableDeferred

/** Falešný přehrávač MP3 pro testy. Přehrávání trvá, dokud ho test nedokončí ([finishPlaying]) nebo nezruší. */
class FakeAudioPlayer : AudioPlayer {
    /** Všechna MP3, jejichž přehrání začalo, v pořadí. */
    val played = mutableListOf<String>()

    /** Kolik přehrávání právě běží (po dohrání, chybě i zrušení klesne). */
    var activePlays = 0
        private set

    /** Když je nastavené, přehrání skončí touto chybou. */
    var failWith: AudioPlaybackException? = null

    /** Názvy souborů, jejichž přehrání má selhat. */
    val failingFiles = mutableSetOf<String>()

    /** Když je `false`, přehrání skončí hned (jako velmi krátký zvuk). */
    var holdPlayback = true
    private var current: CompletableDeferred<Unit>? = null

    /** Názvy souborů, které v „úložišti“ chybí. */
    val missingFiles = mutableSetOf<String>()

    override fun isAvailable(fileName: String) = fileName !in missingFiles

    override suspend fun play(fileName: String) {
        played += fileName
        failWith?.let { throw it }
        if (fileName in failingFiles) throw AudioPlaybackException("Test: $fileName nejde přehrát")
        if (!holdPlayback) return
        activePlays++
        try {
            val gate = CompletableDeferred<Unit>().also { current = it }
            gate.await()
        } finally {
            activePlays--
        }
    }

    /** Simuluje, že přehrávané MP3 dohrálo samo. */
    fun finishPlaying() {
        current?.complete(Unit)
    }
}
