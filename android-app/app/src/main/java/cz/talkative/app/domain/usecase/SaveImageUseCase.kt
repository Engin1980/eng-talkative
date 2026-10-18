package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.image.ImageSaveResult
import cz.talkative.app.domain.image.ImageStore
import javax.inject.Inject

/** Zmenší a uloží vybraný obrázek do soukromého úložiště (zatím bez přiřazení slovíčku). */
class SaveImageUseCase @Inject constructor(
    private val imageStore: ImageStore,
) {
    suspend operator fun invoke(uri: String): ImageSaveResult = imageStore.saveFromUri(uri)
}
