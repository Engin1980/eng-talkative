package cz.talkative.app.data.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.system.ErrnoException
import android.system.OsConstants
import cz.talkative.app.domain.audio.AudioLimits
import cz.talkative.app.domain.audio.AudioRecorder
import cz.talkative.app.domain.audio.RecordingError
import cz.talkative.app.domain.audio.RecordingResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Nahrávání přes [MediaRecorder]: mikrofon, AAC v MP4 (M4A), mono, 44,1 kHz, 64 kbit/s. Dočasný soubor je v
 * `cache/recordings`. Recorder se po nahrávání, chybě i zrušení vždy uvolní a nepovedený soubor se smaže.
 */
class MediaAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioRecorder {

    private val _elapsedMillis = MutableStateFlow(0L)
    override val elapsedMillis: StateFlow<Long> = _elapsedMillis

    @Volatile
    private var finish: CompletableDeferred<Boolean>? = null

    override suspend fun record(): RecordingResult {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            return RecordingResult.Failed(RecordingError.NO_MICROPHONE)
        }
        val directory = File(context.cacheDir, DIRECTORY_NAME)
        val file = File(directory, UUID.randomUUID().toString() + ".m4a")
        // `true` = skončilo samo po limitu, `false` = ukončil uživatel; výjimka = chyba nahrávání za běhu
        val done = CompletableDeferred<Boolean>().also { finish = it }
        var recorder: MediaRecorder? = null
        var kept = false
        try {
            val created = create()
            recorder = created
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                configureAndStart(created, file, done)
            }
            val started = System.currentTimeMillis()
            val reachedLimit = coroutineScope {
                val ticker = launch {
                    while (true) {
                        _elapsedMillis.value = System.currentTimeMillis() - started
                        delay(TICK_MILLIS)
                    }
                }
                try {
                    done.await()
                } finally {
                    ticker.cancel()
                }
            }
            val stopped = withContext(Dispatchers.IO) {
                try {
                    created.stop()
                    true
                } catch (e: RuntimeException) {
                    false // stop() hodí výjimku, když se nic nenahrálo
                }
            }
            if (!stopped) return RecordingResult.TooShort
            kept = true
            return RecordingResult.Recorded(file, reachedLimit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return RecordingResult.Failed(if (isStorageFull(e)) RecordingError.STORAGE_FULL else RecordingError.MICROPHONE_UNAVAILABLE)
        } catch (e: RuntimeException) {
            // start() hází RuntimeException, když je mikrofon obsazený jinou aplikací; IllegalStateException při chybě za běhu
            return RecordingResult.Failed(RecordingError.MICROPHONE_UNAVAILABLE)
        } finally {
            finish = null
            _elapsedMillis.value = 0L
            recorder?.let {
                runCatching { it.reset() }
                it.release()
            }
            if (!kept) file.delete()
        }
    }

    override fun stop() {
        finish?.complete(false)
    }

    @Suppress("DEPRECATION")
    private fun create(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private fun configureAndStart(recorder: MediaRecorder, file: File, done: CompletableDeferred<Boolean>) {
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(SAMPLE_RATE)
            setAudioEncodingBitRate(BIT_RATE)
            // Trochu pod limitem, aby naměřená délka souboru nepřekročila [AudioLimits.MAX_DURATION_MILLIS].
            setMaxDuration((AudioLimits.MAX_DURATION_MILLIS - LIMIT_MARGIN_MILLIS).toInt())
            setOutputFile(file.path)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) done.complete(true)
            }
            setOnErrorListener { _, what, extra ->
                done.completeExceptionally(IllegalStateException("Chyba nahrávání ($what, $extra)"))
            }
            prepare()
            start()
        }
    }

    private fun isStorageFull(e: IOException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.any {
            (it is ErrnoException && it.errno == OsConstants.ENOSPC) || it.message?.contains("ENOSPC") == true
        }

    companion object {
        const val DIRECTORY_NAME = "recordings"
        private const val SAMPLE_RATE = 44_100
        private const val BIT_RATE = 64_000
        private const val TICK_MILLIS = 100L
        private const val LIMIT_MARGIN_MILLIS = 500L
    }
}
