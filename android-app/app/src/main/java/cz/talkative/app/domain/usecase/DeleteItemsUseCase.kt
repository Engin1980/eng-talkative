package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Smaže vybrané složky (včetně podstromů) a slovíčka najednou, buď se smaže vše, nebo nic. */
class DeleteItemsUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(ids: Collection<String>) = repository.deleteItems(ids)
}
