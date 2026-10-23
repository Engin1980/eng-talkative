package cz.talkative.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cz.talkative.app.data.audio.FileAudioStore
import cz.talkative.app.data.audio.MediaAudioRecorder
import cz.talkative.app.domain.audio.AudioSaveResult
import cz.talkative.app.domain.audio.RecordingResult
import cz.talkative.app.domain.usecase.SaveRecordingUseCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Skutečný `MediaRecorder`. Emulátor mikrofon obvykle předává z počítače, jinak nahraje ticho, což pro ověření
 * formátu a úklidu stačí.
 */
@RunWith(AndroidJUnit4::class)
class MediaAudioRecorderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val recorder = MediaAudioRecorder(context)
    private val recordings get() = File(context.cacheDir, "recordings")
    private val audioDir get() = File(context.filesDir, "audio")

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.RECORD_AUDIO)
        recordings.deleteRecursively()
        audioDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        recordings.deleteRecursively()
        audioDir.deleteRecursively()
    }

    private fun recordFor(millis: Long): RecordingResult = runBlocking {
        val job = async(start = CoroutineStart.UNDISPATCHED) { recorder.record() }
        delay(millis)
        recorder.stop()
        job.await()
    }

    @Test
    fun recording_isAcceptedByTheAudioStore_andLeavesNoTemporaryFile() = runBlocking {
        val result = recordFor(1_500)

        assertTrue("výsledek: $result", result is RecordingResult.Recorded)
        val recorded = result as RecordingResult.Recorded
        assertTrue(recorded.file.name.endsWith(".m4a"))
        assertEquals(false, recorded.reachedLimit)

        val saved = SaveRecordingUseCase(FileAudioStore(context))(recorded)

        assertTrue("uložení: $saved", saved is AudioSaveResult.Saved)
        assertNotNull(FileAudioStore(context).resolve((saved as AudioSaveResult.Saved).fileName))
        assertTrue(recordings.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancelledRecording_leavesNothing_andElapsedTimeResets() = runBlocking {
        val job = async(start = CoroutineStart.UNDISPATCHED) { recorder.record() }
        delay(700)
        assertTrue(recorder.elapsedMillis.value > 0)

        job.cancel()
        job.join()

        assertEquals(0L, recorder.elapsedMillis.value)
        assertTrue(recordings.listFiles().orEmpty().isEmpty())
        assertTrue(audioDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun stopWithoutRecording_doesNothing() {
        recorder.stop()
        assertEquals(0L, recorder.elapsedMillis.value)
    }

    @Test
    fun immediateStop_isNeverAHalfWrittenFile() {
        val result = recordFor(0)

        if (result is RecordingResult.Recorded) result.file.delete()
        assertTrue(recordings.listFiles().orEmpty().isEmpty())
    }
}
