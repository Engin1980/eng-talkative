package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

class GetFolderPathUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(folderId: String?): List<Folder> = repository.getPath(folderId)
}
