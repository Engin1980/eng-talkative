package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Vytvoří složku (`id == null`) nebo změní titulek existující. Titulek se ořízne a nesmí být prázdný. */
class SaveFolderUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(id: String?, parentId: String?, title: String): SaveResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return SaveResult.EmptyTitle
        if (id == null) repository.createFolder(parentId, trimmed) else repository.updateFolderTitle(id, trimmed)
        return SaveResult.Saved
    }
}
