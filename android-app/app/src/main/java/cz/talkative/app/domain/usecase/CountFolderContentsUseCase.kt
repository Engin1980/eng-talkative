package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Kolik položek by se smazalo spolu se složkou (pro potvrzovací dialog). */
class CountFolderContentsUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(folderId: String): Int = repository.countFolderContents(folderId)
}
