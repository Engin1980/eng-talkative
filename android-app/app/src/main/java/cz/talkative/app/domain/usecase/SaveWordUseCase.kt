package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.model.SaveResult
import cz.talkative.app.domain.repository.DictionaryRepository
import javax.inject.Inject

/**
 * Vytvoří slovíčko (`id == null`) nebo uloží změny existujícího. Titulek se ořízne a nesmí být prázdný.
 * [imageFile] je vybraný obrázek, [originalImageFile] ten, který slovíčko mělo před úpravou (mění se jen při rozdílu).
 * [alternateText] je jiný text pro čtení; prázdný se ukládá jako žádný (čte se titulek).
 * [audioFile] je vlastní MP3, [originalAudioFile] to, které slovíčko mělo před úpravou. Zvuk je buď jiný text, nebo MP3:
 * slovíčko s MP3 jiný text neukládá.
 */
class SaveWordUseCase @Inject constructor(
    private val repository: DictionaryRepository,
) {
    suspend operator fun invoke(
        id: String?,
        parentId: String?,
        title: String,
        imageFile: String? = null,
        originalImageFile: String? = null,
        alternateText: String? = null,
        audioFile: String? = null,
        originalAudioFile: String? = null,
    ): SaveResult {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return SaveResult.EmptyTitle
        val alternate = if (audioFile != null) null else alternateText?.trim()?.ifEmpty { null }
        if (id == null) {
            repository.createWord(parentId, trimmed, imageFile, alternate, audioFile)
        } else {
            repository.updateWordTitle(id, trimmed)
            repository.updateWordAlternateText(id, alternate)
            if (imageFile != originalImageFile) repository.updateWordImage(id, imageFile)
            if (audioFile != originalAudioFile) repository.updateWordAudio(id, audioFile)
        }
        return SaveResult.Saved
    }
}
