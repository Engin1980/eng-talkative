package cz.talkative.app.domain.model

/** Způsob přidání slova do věty. */
enum class AddWordMode { TAP, DRAG }

/** Velikost mřížky: [columns] sloupců × [rows] řádků na jedné stránce. */
data class GridSize(val columns: Int, val rows: Int) {
    val pageSize: Int get() = columns * rows
}

/**
 * Lokální konfigurace zařízení (nesynchronizuje se). Výchozí hodnoty jsou podle `assignment.md`;
 * `AppConfig()` je tedy výchozí konfigurace.
 */
data class AppConfig(
    val addWordMode: AddWordMode = AddWordMode.TAP,
    val allowReorderInSentence: Boolean = true,
    /** Jak dlouho podržet slovo v mřížce, aby se přidalo do věty (jen v režimu klepnutí); 0 = přidá se hned na klepnutí. */
    val addHoldMillis: Long = 0L,
    /** Jak dlouho podržet slovo ve větě, aby se odebralo. */
    val removeHoldMillis: Long = 1_000L,
    /** Jak dlouho podržet skrytý prvek pro vstup do administrace. */
    val adminHoldMillis: Long = 10_000L,
    val speakWordOnTap: Boolean = false,
    val gridPortrait: GridSize = GridSize(columns = 3, rows = 3),
    val gridLandscape: GridSize = GridSize(columns = 4, rows = 3),
    /** Jazyková značka (BCP 47) pro abecední řazení. */
    val collationLanguageTag: String = "cs",
    val hideCaptionsInSentence: Boolean = false,
    val autoClearAfterPlayback: Boolean = false,
)
