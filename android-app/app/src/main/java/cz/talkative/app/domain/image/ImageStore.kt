package cz.talkative.app.domain.image

import java.io.File

/** Proč se obrázek nepodařilo uložit. */
enum class ImageError {
    /** Soubor nejde otevřít nebo to není obrázek. */
    UNREADABLE,

    /** Na zařízení není dost místa. */
    STORAGE_FULL,
}

sealed interface ImageSaveResult {
    /** [fileName] je název souboru v soukromém úložišti aplikace (ne cesta). */
    data class Saved(val fileName: String) : ImageSaveResult

    data class Failed(val error: ImageError) : ImageSaveResult
}

/** Soukromé úložiště obrázků slovíček. Ukládá jen zmenšené obrázky, originál se nikdy neponechává. */
interface ImageStore {
    /** Načte obrázek z [uri], zmenší ho (max. [ImageScaling.MAX_SIDE] px, správná orientace dle EXIF) a uloží. */
    suspend fun saveFromUri(uri: String): ImageSaveResult

    /** Soubor obrázku, nebo `null`, pokud neexistuje. */
    fun resolve(fileName: String): File?

    /** Smaže soubor; neexistující soubor nevadí. */
    suspend fun delete(fileName: String)

    /** Smaže všechny soubory, které nejsou v [keep] (úklid po přerušeném mazání). */
    suspend fun deleteAllExcept(keep: Set<String>)
}
