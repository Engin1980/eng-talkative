package cz.talkative.app.domain.usecase

import cz.talkative.app.domain.audio.AudioError
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.RecordingResult
import cz.talkative.app.testing.FakeAudioStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SaveRecordingUseCaseTest {

    private val store = FakeAudioStore()
    private val useCase = SaveRecordingUseCase(store)

    private fun recording() = RecordingResult.Recorded(File.createTempFile("rec", ".m4a").apply { writeBytes(ByteArray(10)) })

    @Test
    fun savedRecording_goesToTheStore_andTemporaryFileIsDeleted() = runTest {
        val recording = recording()

        val result = useCase(recording)

        assertTrue(result is AudioSaveResult.Saved)
        assertEquals(setOf((result as AudioSaveResult.Saved).fileName), store.files)
        assertFalse(recording.file.exists())
    }

    @Test
    fun rejectedRecording_isNotStored_andTemporaryFileIsDeleted() = runTest {
        store.failWith = AudioError.TOO_LONG
        val recording = recording()

        val result = useCase(recording)

        assertEquals(AudioSaveResult.Failed(AudioError.TOO_LONG), result)
        assertTrue(store.files.isEmpty())
        assertFalse(recording.file.exists())
    }

    @Test
    fun missingTemporaryFile_isUnreadable() = runTest {
        val recording = recording().also { it.file.delete() }

        assertEquals(AudioSaveResult.Failed(AudioError.UNREADABLE), useCase(recording))
        assertTrue(store.files.isEmpty())
    }

    @Test
    fun discard_deletesTheTemporaryFile() {
        val recording = recording()

        useCase.discard(recording)

        assertFalse(recording.file.exists())
    }
}
