package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/**
 * Přesune položky, ke kterým se nedá dostat (rodič neexistuje nebo je smazaný, složky v cyklu), do složky „Nalezené“
 * v kořeni, kde je jde opravit, přesunout nebo smazat. Vrací počet přesunutých položek.
 */
class RecoverOrphansUseCase @Inject constructor(private val repository: DictionaryRepository) {
    suspend operator fun invoke(folderTitle: String): Int = repository.recoverOrphans(folderTitle)
}
