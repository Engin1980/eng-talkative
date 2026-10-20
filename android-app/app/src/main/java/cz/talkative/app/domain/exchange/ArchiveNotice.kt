package cz.talkative.app.domain.exchange

import cz.talkative.app.domain.pictogram.ArasaacLinks

/** Text souboru `LICENSE-ARASAAC.txt` v exportním archivu. Zdroj textu jsou řetězce aplikace, doména na Androidu nezávisí. */
interface ArchiveNotice {
    fun arasaacLicenseText(): String
}

/**
 * Složí text uvedení autora a licence ARASAAC: krátké a delší znění uvedení doslova podle podmínek ARASAAC,
 * věta o šíření archivu a odkazy (každý na vlastním řádku).
 */
fun buildArasaacLicenseText(credit: String, creditLong: String, note: String): String =
    listOf(
        credit,
        creditLong,
        note,
        "ARASAAC: ${ArasaacLinks.SITE}",
        "Terms of use: ${ArasaacLinks.TERMS}",
        "CC BY-NC-SA: ${ArasaacLinks.LICENSE}",
    ).joinToString(separator = "\n\n", postfix = "\n")
