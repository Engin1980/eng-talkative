package cz.talkative.app.domain.model

/** Položka slovníku zobrazená ve složce: složka nebo slovíčko. Řadí se společně podle [sortOrder]. */
sealed interface DictionaryItem {
    val id: String
    val title: String
    val sortOrder: Int
}

/** Složka. Kořen slovníku nemá vlastní záznam; položky v kořeni mají `parentId == null`. */
data class Folder(
    override val id: String,
    val profileId: String,
    override val title: String,
    val parentId: String?,
    override val sortOrder: Int,
    val updatedAt: Long,
) : DictionaryItem

/** Slovíčko. Patří vždy do právě jedné složky (nebo do kořene). */
data class Word(
    override val id: String,
    val profileId: String,
    override val title: String,
    val parentId: String?,
    override val sortOrder: Int,
    val updatedAt: Long,
    /** Název souboru obrázku v soukromém úložišti ([cz.talkative.app.domain.image.ImageStore]); `null` = bez obrázku. */
    val imageFile: String? = null,
) : DictionaryItem
