package cz.talkative.app.domain.repository

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import kotlinx.coroutines.flow.Flow

/** Všechny nesmazané složky a slovíčka profilu jako ploché seznamy (vazby jsou přes `parentId`). */
data class DictionarySnapshot(val folders: List<Folder>, val words: List<Word>)

/** Složka k importu; [key] a [parentKey] jsou jen vazby uvnitř dávky, repozitář přidělí nová ID. `parentKey == null` = cílová složka importu. */
data class ImportedFolder(val key: String, val parentKey: String?, val title: String, val sortOrder: Int)

data class ImportedWord(
    val key: String,
    val parentKey: String?,
    val title: String,
    val sortOrder: Int,
    val imageFile: String?,
    val alternateText: String?,
    val audioFile: String?,
)

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

    /**
     * Uloží ruční pořadí složky [parentId]: položky z [orderedIds] dostanou pořadí 0, 1, 2, ... (složky a slovíčka
     * dohromady) v jedné transakci. Nový `updatedAt` dostanou jen položky, kterým se pořadí změnilo.
     */
    suspend fun reorderItems(parentId: String?, orderedIds: List<String>)

    /**
     * Přesune složky a slovíčka s [ids] na konec složky [targetParentId] (`null` = kořen) v jedné transakci;
     * zachová jejich dosavadní vzájemné pořadí. Položky, které už v cíli jsou, a neexistující ID se přeskočí.
     * Kontrolu cyklů (složka do vlastního podstromu) dělá volající, viz `MoveItemsUseCase`.
     */
    suspend fun moveItems(ids: Collection<String>, targetParentId: String?)

    /** Smaže vybrané složky (i s podstromy) a slovíčka v jedné transakci; neexistující ID se přeskočí. */
    suspend fun deleteItems(ids: Collection<String>)

    /**
     * Vloží složky a slovíčka s novými ID v jedné transakci. Položky s `parentKey == null` přijdou na konec složky
     * [targetParentId] (`null` = kořen) a drží vzájemné pořadí podle `sortOrder`; ostatní mají `sortOrder` uvnitř své složky.
     * S [replaceAll] se nejdřív smaže celý dosavadní slovník (včetně souborů obrázků a MP3, až po úspěšném zápisu)
     * a nové položky jdou do kořene. Soubory v [ImportedWord] musí už být uložené v úložištích a patřit jen tomu slovíčku.
     * Při chybě se nezmění nic.
     */
    suspend fun importItems(
        targetParentId: String?,
        folders: List<ImportedFolder>,
        words: List<ImportedWord>,
        replaceAll: Boolean,
    )

    /** Celý slovník najednou (pro export); smazané položky v něm nejsou. */
    suspend fun snapshot(): DictionarySnapshot

    /** Smaže soubory obrázků a MP3, které nepatří žádnému slovíčku (po přerušeném mazání nebo zrušené úpravě). */
    suspend fun cleanUpOrphanFiles()
}
