package cz.talkative.app.domain.pictogram

/** Piktogram nabídnutý k výběru; [previewUrl] vede na malý náhled. */
data class Pictogram(val id: Int, val title: String, val previewUrl: String)

/** Proč se hledání nebo stažení nezdařilo. */
enum class PictogramError {
    /** Zařízení není online nebo se k serveru nelze připojit. */
    NO_NETWORK,

    /** Server neodpověděl včas. */
    TIMEOUT,

    /** Server odpověděl chybou. */
    SERVER_ERROR,

    /** Odpověď nejde přečíst nebo je příliš velká. */
    BAD_RESPONSE,
}

sealed interface PictogramSearchResult {
    /** [pictograms] je prázdné, když se nic nenašlo. */
    data class Found(val pictograms: List<Pictogram>) : PictogramSearchResult

    data class Failed(val error: PictogramError) : PictogramSearchResult
}

/** Hledání piktogramů podle slova. Síť se používá jen tady a při stažení vybraného piktogramu. */
interface PictogramSearch {
    suspend fun search(text: String): PictogramSearchResult
}

/** Adresy obrázků piktogramů ARASAAC. */
object PictogramUrls {
    const val DEFAULT_STATIC_BASE = "https://static.arasaac.org"

    /** Velikost náhledu v nabídce. */
    const val PREVIEW_SIZE = 300

    /** Velikost, v jaké se piktogram stahuje a ukládá. */
    const val DOWNLOAD_SIZE = 500

    fun image(id: Int, size: Int, staticBase: String = DEFAULT_STATIC_BASE): String =
        "${staticBase.trimEnd('/')}/pictograms/$id/${id}_$size.png"
}
