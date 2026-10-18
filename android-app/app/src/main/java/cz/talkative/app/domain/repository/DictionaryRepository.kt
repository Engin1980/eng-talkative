package cz.talkative.app.domain.repository

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import kotlinx.coroutines.flow.Flow

/**
 * Slovník (strom složek a slovíček) aktuálního profilu. Smazané položky se nikdy nevracejí.
 * `parentId == null` znamená kořen.
 */
interface DictionaryRepository {
    /** Obsah složky (složky i slovíčka společně) seřazený podle ručního pořadí. */
    fun observeItems(parentId: String?): Flow<List<DictionaryItem>>

    /** Cesta od první složky pod kořenem až po [folderId] včetně; pro kořen je prázdná. */
    suspend fun getPath(folderId: String?): List<Folder>

    /** Nová položka se zařadí na konec složky. */
    suspend fun createFolder(parentId: String?, title: String): Folder

    suspend fun createWord(
        parentId: String?,
        title: String,
        imageFile: String? = null,
        alternateText: String? = null,
        audioFile: String? = null,
    ): Word

    suspend fun updateFolderTitle(id: String, title: String)

    suspend fun updateWordTitle(id: String, title: String)

    /** Nastaví (nebo odebere, `null`) obrázek slovíčka; soubor původního obrázku se smaže. */
    suspend fun updateWordImage(id: String, imageFile: String?)

    /** Nastaví (nebo odebere, `null`) jiný text, který se čte místo titulku. */
    suspend fun updateWordAlternateText(id: String, alternateText: String?)

    /** Nastaví (nebo odebere, `null`) vlastní MP3 slovíčka; soubor původního MP3 se smaže. */
    suspend fun updateWordAudio(id: String, audioFile: String?)

    /** Počet složek a slovíček uvnitř složky včetně všech podsložek (složka sama se nepočítá). */
    suspend fun countFolderContents(folderId: String): Int

    /** Smaže složku i celý její podstrom včetně souborů obrázků a MP3. */
    suspend fun deleteFolder(id: String)

    /** Smaže slovíčko včetně souborů jeho obrázku a MP3. */
    suspend fun deleteWord(id: String)

    /** Smaže soubory obrázků a MP3, které nepatří žádnému slovíčku (po přerušeném mazání nebo zrušené úpravě). */
    suspend fun cleanUpOrphanFiles()
}
