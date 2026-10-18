package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.ordering.ItemOrdering
import cz.talkative.app.domain.repository.ConfigRepository
import cz.talkative.app.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Seřadí obsah složky podle abecedy; jazyk řazení se bere z lokální konfigurace zařízení. */
class SortAlphabeticallyUseCase @Inject constructor(
    private val repository: DictionaryRepository,
    private val config: ConfigRepository,
) {
    suspend operator fun invoke(parentId: String?) {
        val items = repository.observeItems(parentId).first()
        val languageTag = config.config.first().collationLanguageTag
        repository.reorderItems(parentId, ItemOrdering.alphabetical(items, languageTag))
    }
}
