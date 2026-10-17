package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.repository.DictionaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFolderItemsUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    operator fun invoke(folderId: String?): Flow<List<DictionaryItem>> = repository.observeItems(folderId)
}
