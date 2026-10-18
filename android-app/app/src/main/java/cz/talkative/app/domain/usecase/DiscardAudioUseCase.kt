package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.audio.AudioStore
import javax.inject.Inject

/** Smaže uložené MP3, které se nakonec nepřiřadilo žádnému slovíčku (zrušený dialog, nahrazené MP3). */
class DiscardAudioUseCase @Inject constructor(
    private val audioStore: AudioStore,
) {
    suspend operator fun invoke(fileName: String) = audioStore.delete(fileName)
}
