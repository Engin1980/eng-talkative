package cz.talkative.app.data.audio

import android.media.MediaPlayer
import cz.talkative.app.domain.audio.AudioPlaybackException
import cz.talkative.app.domain.audio.AudioPlayer
import cz.talkative.app.domain.audio.AudioStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Přehrávání MP3 přes [MediaPlayer]. Přehrávač se vytváří na hlavním vlákně (kvůli callbackům), příprava souboru běží
 * na pozadí (`prepareAsync`), takže UI nezamrzá. Po dohrání, chybě i zrušení se vždy uvolní.
 */
class MediaAudioPlayer @Inject constructor(
    private val store: AudioStore,
) : AudioPlayer {

    override suspend fun play(fileName: String) {
        val file = store.resolve(fileName) ?: throw AudioPlaybackException("Soubor $fileName neexistuje")
        withContext(Dispatchers.Main.immediate) {
            val player = MediaPlayer()
            try {
                suspendCancellableCoroutine { continuation ->
                    player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                    player.setOnErrorListener { _, what, extra ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(AudioPlaybackException("Chyba přehrávače ($what, $extra)"))
                        }
                        true
                    }
                    player.setOnPreparedListener { it.start() }
                    try {
                        player.setDataSource(file.path)
                        player.prepareAsync()
                    } catch (e: Exception) {
                        continuation.resumeWithException(AudioPlaybackException("Soubor nelze přehrát", e))
                    }
                }
            } finally {
                player.release()
            }
        }
    }
}
