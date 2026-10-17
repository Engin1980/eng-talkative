package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Smaže složku včetně celého podstromu (koš neexistuje). */
class DeleteFolderUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(folderId: String) = repository.deleteFolder(folderId)
}
