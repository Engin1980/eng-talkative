package cz.talkative.app.domain.audio

/** Omezení vlastního zvuku slovíčka a jeho kontrola. Čistá logika bez závislosti na Androidu. */
object AudioLimits {
    /** Zadání: délka řádově desítky sekund. */
    const val MAX_DURATION_MILLIS = 60_000L

    /** Dost i pro minutu MP3 ve vysoké kvalitě; chrání před omylem s velkým souborem. */
    const val MAX_BYTES = 5L * 1024 * 1024

    const val MP3_MIME_TYPE = "audio/mpeg"

    /** Vrátí důvod odmítnutí, nebo `null`, když je zvuk v pořádku. [durationMillis] je `null`, když ji systém nezjistil. */
    fun check(mimeType: String?, durationMillis: Long?): AudioError? = when {
        mimeType != MP3_MIME_TYPE -> AudioError.UNSUPPORTED_FORMAT
        durationMillis == null || durationMillis <= 0 -> AudioError.UNREADABLE
        durationMillis > MAX_DURATION_MILLIS -> AudioError.TOO_LONG
        else -> null
    }
}
