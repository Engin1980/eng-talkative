package cz.talkative.app.domain.audio

/** Přehrání MP3 se nepodařilo (chybí soubor, poškozený soubor, chyba přehrávače). */
class AudioPlaybackException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Přehrávač vlastních MP3. Nemá stav, každé přehrání si vezme a uvolní vlastní prostředky. */
interface AudioPlayer {
    /**
     * Přehraje MP3 [fileName] z úložiště a vrátí se, až dohraje. Zrušením korutiny se přehrávání okamžitě zastaví.
     * @throws AudioPlaybackException když se přehrání nepodaří
     */
    suspend fun play(fileName: String)

    /** `false`, když soubor [fileName] v úložišti není (pak se místo něj čte titulek slovíčka). */
    fun isAvailable(fileName: String): Boolean = true
}
