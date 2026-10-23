package cz.talkative.app.domain.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Proč se nahrávání nepodařilo. */
enum class RecordingError {
    /** Zařízení nemá mikrofon. */
    NO_MICROPHONE,

    /** Mikrofon nejde použít (obsazený jinou aplikací, chybí oprávnění, chyba při spuštění). */
    MICROPHONE_UNAVAILABLE,

    /** Na zařízení není dost místa. */
    STORAGE_FULL,
}

sealed interface RecordingResult {
    /**
     * Hotová nahrávka v dočasném [file]; po uložení nebo zahození ji musí smazat volající.
     * [reachedLimit] je `true`, když se nahrávání samo ukončilo po [AudioLimits.MAX_DURATION_MILLIS].
     */
    data class Recorded(val file: File, val reachedLimit: Boolean = false) : RecordingResult

    /** Nahrávka byla tak krátká, že se nic nenahrálo (okamžité zastavení). */
    data object TooShort : RecordingResult

    data class Failed(val error: RecordingError) : RecordingResult
}

/**
 * Nahrávání zvuku z mikrofonu. Funguje jako [AudioPlayer]: [record] je suspend funkce, která se vrátí po skončení
 * nahrávání. Zrušením korutiny se nahrávání zahodí (soubor se smaže, mikrofon se uvolní); [stop] ho ukončí normálně.
 */
interface AudioRecorder {
    /** Uplynulý čas běžící nahrávky v ms; mimo nahrávání 0. */
    val elapsedMillis: StateFlow<Long>

    /** Spustí nahrávání a vrátí se, až skončí ([stop], limit délky, chyba) nebo se korutina zruší. */
    suspend fun record(): RecordingResult

    /** Ukončí běžící nahrávku normálně; [record] pak vrátí [RecordingResult.Recorded]. Bez nahrávání nedělá nic. */
    fun stop()
}
