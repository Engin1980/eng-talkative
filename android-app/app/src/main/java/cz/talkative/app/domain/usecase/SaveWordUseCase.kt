package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Vytvoří slovíčko (`id == null`) nebo změní titulek existujícího. Titulek se ořízne a nesmí být prázdný. */
class SaveWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(id: String?, parentId: String?, title: String): SaveResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return SaveResult.EmptyTitle
        if (id == null) repository.createWord(parentId, trimmed) else repository.updateWordTitle(id, trimmed)
        return SaveResult.Saved
    }
}
