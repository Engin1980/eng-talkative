package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.ordering.ItemOrdering
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Posune jednu položku složky o místo nahoru nebo dolů; [orderedIds] je dnešní pořadí celé složky. */
class MoveItemInOrderUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(parentId: String?, orderedIds: List<String>, itemId: String, offset: Int) {
        val newOrder = ItemOrdering.moved(orderedIds, itemId, offset) ?: return
        repository.reorderItems(parentId, newOrder)
    }
}
