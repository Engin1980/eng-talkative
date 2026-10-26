package cz.talkative.app.domain.integrity

/** Co je se slovíčkem nebo složkou v nepořádku. Administrátor to vidí jako výkřičník s vysvětlením. */
enum class ItemProblem {
    /** Slovíčko odkazuje na obrázek, jehož soubor v zařízení není. V klientovi se zobrazí jen text. */
    MISSING_IMAGE,

    /** Slovíčko odkazuje na vlastní zvuk, jehož soubor v zařízení není. V klientovi se přečte titulek. */
    MISSING_AUDIO,

    /** Položka je ve složce, která neexistuje (nebo je smazaná či v cyklu), takže se k ní nedá dostat. */
    MISSING_FOLDER,
}
