package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/**
 * Vytvoří slovíčko (`id == null`) nebo uloží změny existujícího. Titulek se ořízne a nesmí být prázdný.
 * [imageFile] je vybraný obrázek, [originalImageFile] ten, který slovíčko mělo před úpravou (mění se jen při rozdílu).
 */
class SaveWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(
        id: String?,
        parentId: String?,
        title: String,
        imageFile: String? = null,
        originalImageFile: String? = null,
    ): SaveResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return SaveResult.EmptyTitle
        if (id == null) {
            repository.createWord(parentId, trimmed, imageFile)
        } else {
            repository.updateWordTitle(id, trimmed)
            if (imageFile != originalImageFile) repository.updateWordImage(id, imageFile)
        }
        return SaveResult.Saved
    }
}
