package cz.talkative.app.domain.model

/** Výsledek přesunu položek do jiné složky. Při jakémkoli neúspěchu se nic nezměnilo. */
enum class MoveResult {
    Moved,

    /** Cílem je přesouvaná složka nebo některá z jejích podsložek. */
    TargetInsideMovedFolder,

    /** Cílová složka neexistuje (např. byla mezitím smazána). */
    TargetNotFound,
}
