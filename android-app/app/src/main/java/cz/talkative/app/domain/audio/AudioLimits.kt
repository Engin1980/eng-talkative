package cz.talkative.app.domain.audio

/** Omezení vlastního zvuku slovíčka a jeho kontrola. Čistá logika bez závislosti na Androidu. */
object AudioLimits {
    /** Zadání: délka řádově desítky sekund. */
    const val MAX_DURATION_MILLIS = 60_000L

    /** Dost i pro minutu MP3 ve vysoké kvalitě; chrání před omylem s velkým souborem. */
    const val MAX_BYTES = 5L * 1024 * 1024

    private const val MP3_MIME_TYPE = "audio/mpeg"

    /** MIME typy M4A (AAC v MP4); `MediaMetadataRetriever` vrací podle verze Androidu první nebo druhý. */
    private val M4A_MIME_TYPES = listOf("audio/mp4", "audio/x-m4a")

    /** Přijímané formáty: MP3 (hotový soubor) a M4A (výstup `MediaRecorderu`, který MP3 neumí). */
    val ACCEPTED_MIME_TYPES: Array<String> = (listOf(MP3_MIME_TYPE) + M4A_MIME_TYPES).toTypedArray()

    /** Přípona uloženého souboru podle formátu ([mimeType] už prošel kontrolou [check]). */
    fun fileExtension(mimeType: String?): String = if (mimeType == MP3_MIME_TYPE) "mp3" else "m4a"

    /** Vrátí důvod odmítnutí, nebo `null`, když je zvuk v pořádku. [durationMillis] je `null`, když ji systém nezjistil. */
    fun check(mimeType: String?, durationMillis: Long?): AudioError? = when {
        mimeType == null || mimeType !in ACCEPTED_MIME_TYPES -> AudioError.UNSUPPORTED_FORMAT
        durationMillis == null || durationMillis <= 0 -> AudioError.UNREADABLE
        durationMillis > MAX_DURATION_MILLIS -> AudioError.TOO_LONG
        else -> null
    }
}
