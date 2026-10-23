package cz.talkative.app.testing

import cz.talkative.app.domain.audio.AudioRecorder
import cz.talkative.app.domain.audio.RecordingResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Falešný nahrávač pro testy. Nahrávání trvá, dokud ho test neukončí ([stop], [reachLimit]) nebo nezruší. */
class FakeAudioRecorder : AudioRecorder {
    private val _elapsedMillis = MutableStateFlow(0L)
    override val elapsedMillis: StateFlow<Long> = _elapsedMillis

    /** Kolikrát nahrávání začalo. */
    var starts = 0
        private set

    /** Nahrávání právě běží. */
    var isRecording = false
        private set

    /** Kolikrát se nahrávání zrušilo (korutina zrušena před koncem). */
    var cancellations = 0
        private set

    /** Dočasné soubory, které nahrávač vytvořil (test podle nich ověřuje úklid). */
    val files = mutableListOf<File>()

    /** Když je nastavené, nahrávání skončí hned s tímto výsledkem (např. chyba při startu). */
    var immediateResult: RecordingResult? = null

    /** Příští nahrávka bude prázdná (zastavení hned po startu). */
    var tooShort = false

    private var finish: CompletableDeferred<Boolean>? = null

    override suspend fun record(): RecordingResult {
        starts++
        immediateResult?.let { return it }
        val gate = CompletableDeferred<Boolean>().also { finish = it }
        isRecording = true
        try {
            val reachedLimit = gate.await()
            if (tooShort) return RecordingResult.TooShort
            val file = File.createTempFile("fake-recording", ".m4a").also { files += it }
            return RecordingResult.Recorded(file, reachedLimit)
        } catch (e: CancellationException) {
            cancellations++
            throw e
        } finally {
            isRecording = false
            _elapsedMillis.value = 0L
            finish = null
        }
    }

    override fun stop() {
        finish?.complete(false)
    }

    /** Simuluje, že nahrávání dosáhlo časového limitu a skončilo samo. */
    fun reachLimit() {
        finish?.complete(true)
    }

    fun setElapsed(millis: Long) {
        _elapsedMillis.value = millis
    }
}
