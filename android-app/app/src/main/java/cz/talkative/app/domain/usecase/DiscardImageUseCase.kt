package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.image.ImageStore
import javax.inject.Inject

/** Smaže uložený obrázek, který se nakonec nepřiřadil žádnému slovíčku (zrušený dialog, nahrazený náhled). */
class DiscardImageUseCase @Inject constructor(
    private val imageStore: ImageStore,
) {
    suspend operator fun invoke(fileName: String) = imageStore.delete(fileName)
}
