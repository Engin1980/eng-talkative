package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.AudioStore
import cz.talkative.app.domain.audio.RecordingResult
import java.io.IOException
import javax.inject.Inject

/** Uloží hotovou nahrávku stejnou cestou jako vybraný soubor; dočasný soubor nahrávky se smaže vždy. */
class SaveRecordingUseCase @Inject constructor(
    private val audioStore: AudioStore,
) {
    suspend operator fun invoke(recording: RecordingResult.Recorded): AudioSaveResult =
        try {
            recording.file.inputStream().use { audioStore.saveFromStream(it) }
        } catch (e: IOException) {
            AudioSaveResult.Failed(AudioError.UNREADABLE)
        } finally {
            recording.file.delete()
        }

    /** Zahodí nahrávku, kterou nikdo nechce uložit. */
    fun discard(recording: RecordingResult.Recorded) {
        recording.file.delete()
    }
}
