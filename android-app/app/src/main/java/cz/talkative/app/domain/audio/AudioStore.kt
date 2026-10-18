package cz.talkative.app.domain.audio

import java.io.File

/** Proč se MP3 nepodařilo uložit. */
enum class AudioError {
    /** Soubor nejde otevřít nebo ho systém nedokáže přečíst jako zvuk. */
    UNREADABLE,

    /** Soubor je zvuk, ale ne MP3. */
    UNSUPPORTED_FORMAT,

    /** Zvuk je delší než [AudioLimits.MAX_DURATION_MILLIS]. */
    TOO_LONG,

    /** Soubor je větší než [AudioLimits.MAX_BYTES]. */
    TOO_LARGE,

    /** Na zařízení není dost místa. */
    STORAGE_FULL,
}

sealed interface AudioSaveResult {
    /** [fileName] je název souboru v soukromém úložišti aplikace (ne cesta). */
    data class Saved(val fileName: String) : AudioSaveResult

    data class Failed(val error: AudioError) : AudioSaveResult
}

/** Soukromé úložiště vlastních MP3 slovíček. Uloží se jen MP3, které projde kontrolou formátu, délky a velikosti. */
interface AudioStore {
    /** Zkopíruje MP3 z [uri], zkontroluje ho a uloží; při odmítnutí po sobě nenechá žádný soubor. */
    suspend fun saveFromUri(uri: String): AudioSaveResult

    /** Soubor MP3, nebo `null`, pokud neexistuje. */
    fun resolve(fileName: String): File?

    /** Smaže soubor; neexistující soubor nevadí. */
    suspend fun delete(fileName: String)

    /** Smaže všechny soubory, které nejsou v [keep] (úklid po přerušeném mazání). */
    suspend fun deleteAllExcept(keep: Set<String>)
}
