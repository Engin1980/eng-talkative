package cz.talkative.app.domain.model

/** Rozumné meze hodnot konfigurace; mimo ně se hodnota při ukládání i čtení srazí na nejbližší povolenou. */
object ConfigLimits {
    const val MIN_GRID_SIDE = 1
    const val MAX_GRID_SIDE = 6

    /** 0 znamená přidání hned na klepnutí; kladná hodnota je doba podržení. */
    const val MIN_ADD_HOLD_MILLIS = 0L
    const val MAX_ADD_HOLD_MILLIS = 5_000L
    const val ADD_HOLD_STEP_MILLIS = 500L

    const val MIN_REMOVE_HOLD_MILLIS = 500L
    const val MAX_REMOVE_HOLD_MILLIS = 5_000L
    const val REMOVE_HOLD_STEP_MILLIS = 500L

    /** Vstup do administrace má být záměrně pomalý, proto je dolní mez vyšší než u odebrání slova. */
    const val MIN_ADMIN_HOLD_MILLIS = 3_000L
    const val MAX_ADMIN_HOLD_MILLIS = 30_000L
    const val ADMIN_HOLD_STEP_MILLIS = 1_000L

    /** Jazyky nabízené pro abecední řazení (BCP 47), čeština první. */
    val COLLATION_LANGUAGES = listOf("cs", "sk", "en", "de", "pl")
}

private fun GridSize.sanitized() = GridSize(
    columns = columns.coerceIn(ConfigLimits.MIN_GRID_SIDE, ConfigLimits.MAX_GRID_SIDE),
    rows = rows.coerceIn(ConfigLimits.MIN_GRID_SIDE, ConfigLimits.MAX_GRID_SIDE),
)

/** Konfigurace s hodnotami upravenými do povolených mezí; prázdná kolace se nahradí výchozí. */
fun AppConfig.sanitized(): AppConfig = copy(
    addHoldMillis = addHoldMillis.coerceIn(ConfigLimits.MIN_ADD_HOLD_MILLIS, ConfigLimits.MAX_ADD_HOLD_MILLIS),
    removeHoldMillis = removeHoldMillis.coerceIn(ConfigLimits.MIN_REMOVE_HOLD_MILLIS, ConfigLimits.MAX_REMOVE_HOLD_MILLIS),
    adminHoldMillis = adminHoldMillis.coerceIn(ConfigLimits.MIN_ADMIN_HOLD_MILLIS, ConfigLimits.MAX_ADMIN_HOLD_MILLIS),
    gridPortrait = gridPortrait.sanitized(),
    gridLandscape = gridLandscape.sanitized(),
    collationLanguageTag = collationLanguageTag.ifBlank { AppConfig().collationLanguageTag },
)
