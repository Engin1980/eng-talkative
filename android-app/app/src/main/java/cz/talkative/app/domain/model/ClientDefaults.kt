package cz.talkative.app.domain.model

/** Výchozí hodnoty z `assignment.md`. V kroku 2.1 je převezme konfigurace (DataStore). */
object ClientDefaults {
    /** Jak dlouho se musí podržet skrytý prvek pro vstup do administrace. */
    const val ADMIN_HOLD_MILLIS = 10_000L
}
