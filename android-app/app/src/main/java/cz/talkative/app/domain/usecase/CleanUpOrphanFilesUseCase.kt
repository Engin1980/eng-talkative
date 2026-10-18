package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/** Smaže soubory obrázků a MP3, které nepatří žádnému slovíčku. Spouští se při startu aplikace. */
class CleanUpOrphanFilesUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke() = repository.cleanUpOrphanFiles()
}
