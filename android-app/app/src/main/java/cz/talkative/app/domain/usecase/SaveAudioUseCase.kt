package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import javax.inject.Inject

/** Zkontroluje a uloží vybrané MP3 do soukromého úložiště (zatím bez přiřazení slovíčku). */
class SaveAudioUseCase @Inject constructor(
    private val audioStore: AudioStore,
) {
    suspend operator fun invoke(uri: String): AudioSaveResult = audioStore.saveFromUri(uri)
}
