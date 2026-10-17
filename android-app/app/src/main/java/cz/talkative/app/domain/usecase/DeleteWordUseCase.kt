package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

class DeleteWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(wordId: String) = repository.deleteWord(wordId)
}
