package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.domain.model.sanitized
import kotlinx.serialization.Serializable

/** Rozložení ZIP archivu: `manifest.json` a `LICENSE-ARASAAC.txt` v kořeni, obrázky v `images/`, zvuky v `audio/`. */
object ArchiveLayout {
    const val MANIFEST = "manifest.json"

    /** Uvedení autora a licence ARASAAC; jen text pro lidi, import ho ignoruje. */
    const val LICENSE = "LICENSE-ARASAAC.txt"
    const val IMAGES_DIR = "images/"
    const val AUDIO_DIR = "audio/"
}

/** Verze schématu manifestu. Nová verze, kterou starší aplikace nezná, se při importu odmítne. */
const val ARCHIVE_SCHEMA_VERSION = 1

/**
 * Obsah `manifest.json`. Složky a slovíčka jsou ploché seznamy s odkazem na rodiče (`parentId == null` = kořen archivu).
 * Archiv nikdy neobsahuje profil, `updatedAt`, tombstony ani nic z přihlášení; ID slouží jen k vazbám uvnitř archivu
 * a při importu se nahradí novými.
 */
@Serializable
data class ArchiveManifest(
    val schemaVersion: Int,
    val folders: List<ArchiveFolder> = emptyList(),
    val words: List<ArchiveWord> = emptyList(),
    /** Jen když uživatel při exportu zaškrtl, že chce i konfiguraci. */
    val config: ArchiveConfig? = null,
)

@Serializable
data class ArchiveFolder(
    val id: String,
    val title: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
)

/** [imageFile] je název souboru v `images/`, [audioFile] v `audio/`; bez cesty. */
@Serializable
data class ArchiveWord(
    val id: String,
    val title: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val imageFile: String? = null,
    val alternateText: String? = null,
    val audioFile: String? = null,
)

@Serializable
data class ArchiveGrid(val columns: Int, val rows: Int)

/** Konfigurace zařízení; chybějící nebo neznámé hodnoty se při čtení nahradí výchozími. */
@Serializable
data class ArchiveConfig(
    val addWordMode: AddWordMode = AddWordMode.TAP,
    val allowReorderInSentence: Boolean = true,
    val removeHoldMillis: Long = 1_000L,
    val adminHoldMillis: Long = 10_000L,
    val speakWordOnTap: Boolean = false,
    val gridPortrait: ArchiveGrid = ArchiveGrid(columns = 3, rows = 3),
    val gridLandscape: ArchiveGrid = ArchiveGrid(columns = 4, rows = 3),
    val collationLanguageTag: String = "cs",
    val hideCaptionsInSentence: Boolean = false,
    val autoClearAfterPlayback: Boolean = false,
) {
    /** Hodnoty mimo meze se srazí na povolené (viz [AppConfig.sanitized]). */
    fun toAppConfig() = AppConfig(
        addWordMode = addWordMode,
        allowReorderInSentence = allowReorderInSentence,
        removeHoldMillis = removeHoldMillis,
        adminHoldMillis = adminHoldMillis,
        speakWordOnTap = speakWordOnTap,
        gridPortrait = GridSize(gridPortrait.columns, gridPortrait.rows),
        gridLandscape = GridSize(gridLandscape.columns, gridLandscape.rows),
        collationLanguageTag = collationLanguageTag,
        hideCaptionsInSentence = hideCaptionsInSentence,
        autoClearAfterPlayback = autoClearAfterPlayback,
    ).sanitized()

    companion object {
        fun from(config: AppConfig) = ArchiveConfig(
            addWordMode = config.addWordMode,
            allowReorderInSentence = config.allowReorderInSentence,
            removeHoldMillis = config.removeHoldMillis,
            adminHoldMillis = config.adminHoldMillis,
            speakWordOnTap = config.speakWordOnTap,
            gridPortrait = ArchiveGrid(config.gridPortrait.columns, config.gridPortrait.rows),
            gridLandscape = ArchiveGrid(config.gridLandscape.columns, config.gridLandscape.rows),
            collationLanguageTag = config.collationLanguageTag,
            hideCaptionsInSentence = config.hideCaptionsInSentence,
            autoClearAfterPlayback = config.autoClearAfterPlayback,
        )
    }
}
