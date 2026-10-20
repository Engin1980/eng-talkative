package cz.talkative.app.domain.drawing

import java.io.File

/** Kam se ukládá hotová kresba před předáním editaci slovíčka: dočasný soubor v `cache/drawings`. */
interface DrawingStorage {
    /** Zapíše PNG a vrátí soubor; starší kresby se smažou (ve složce je vždy nejvýše jedna). Při chybě vyhodí `IOException`. */
    suspend fun save(png: ByteArray): File
}
