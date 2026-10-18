package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.MoveResult
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/**
 * Přesune vybrané složky a slovíčka do složky [targetFolderId] (`null` = kořen) jako jednu operaci.
 * Složku nelze přesunout do sebe samé ani do své podsložky: to by ji odtrhlo od stromu.
 */
class MoveItemsUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(ids: Set<String>, targetFolderId: String?): MoveResult {
        if (targetFolderId != null) {
            // Cesta k cíli obsahuje cíl i všechny jeho předky; kterákoli přesouvaná složka v ní by vytvořila cyklus.
            val path = repository.getPath(targetFolderId)
            if (path.isEmpty()) return MoveResult.TargetNotFound
            if (path.any { it.id in ids }) return MoveResult.TargetInsideMovedFolder
        }
        repository.moveItems(ids, targetFolderId)
        return MoveResult.Moved
    }
}
