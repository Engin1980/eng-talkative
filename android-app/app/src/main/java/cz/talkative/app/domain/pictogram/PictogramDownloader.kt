package cz.talkative.app.domain.pictogram

import java.io.File

sealed interface PictogramDownloadResult {
    /** Piktogram je v dočasném [file]; dál ho zpracuje obvyklá cesta obrázků slovíček (zmenšení a uložení). */
    data class Downloaded(val file: File) : PictogramDownloadResult

    data class Failed(val error: PictogramError) : PictogramDownloadResult
}

/** Stáhne vybraný piktogram do dočasného souboru v `cache`. Při chybě po sobě nenechá žádný soubor. */
interface PictogramDownloader {
    suspend fun download(id: Int): PictogramDownloadResult
}
