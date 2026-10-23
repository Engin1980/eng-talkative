package cz.talkative.app.testing

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/** Zvukové soubory pro testy, vytvořené v kódu (žádné binární soubory v repozitáři). */
object AudioFixtures {

    /** MPEG-1 Layer III, 128 kb/s, 44,1 kHz, stereo: každý snímek má 417 bajtů a trvá 1152/44100 s. */
    private const val FRAME_BYTES = 417
    private const val FRAME_SECONDS = 1152.0 / 44100.0

    /** Tichý MP3 přibližně [seconds] sekund dlouhý (snímky s hlavičkou a nulovými daty). */
    fun silentMp3(seconds: Double): ByteArray {
        val frames = Math.ceil(seconds / FRAME_SECONDS).toInt()
        val out = ByteArrayOutputStream(frames * FRAME_BYTES)
        val frame = ByteArray(FRAME_BYTES).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xFB.toByte()
            it[2] = 0x90.toByte()
            it[3] = 0x00
        }
        repeat(frames) { out.write(frame) }
        return out.toByteArray()
    }

    /**
     * Tichý M4A (AAC v MP4, mono, 44,1 kHz) dlouhý [seconds] sekund; vyrobí ho kodér zařízení, protože ruční
     * složení MP4 je zbytečně složité. Stejný formát vyrábí `MediaRecorder` při nahrávání.
     */
    fun silentM4a(context: Context, seconds: Double): ByteArray {
        val sampleRate = 44_100
        val file = File.createTempFile("fixture", ".m4a", context.cacheDir)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val totalSamples = (seconds * sampleRate).toLong()
            val chunkSamples = 2048
            var fed = 0L
            var inputDone = false
            var outputDone = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        buffer.clear()
                        val samples = minOf(chunkSamples.toLong(), totalSamples - fed).toInt()
                        val timeUs = fed * 1_000_000L / sampleRate
                        if (samples <= 0) {
                            codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.put(ByteArray(samples * 2))
                            codec.queueInputBuffer(index, 0, samples * 2, timeUs, 0)
                            fed += samples
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                } else if (out >= 0) {
                    val buffer = codec.getOutputBuffer(out)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && track >= 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            codec.release()
            runCatching { muxer.stop() }
            muxer.release()
        }
        return file.readBytes().also { file.delete() }
    }

    /** Tichý WAV (PCM 16 bit, 8 kHz, mono) dlouhý [seconds] sekund: zvuk, který ale není MP3. */
    fun silentWav(seconds: Int): ByteArray {
        val sampleRate = 8000
        val dataSize = sampleRate * 2 * seconds
        val out = ByteArrayOutputStream()
        fun int(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
        fun short(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
        out.write("RIFF".toByteArray())
        int(36 + dataSize)
        out.write("WAVEfmt ".toByteArray())
        int(16)
        short(1) // PCM
        short(1) // mono
        int(sampleRate)
        int(sampleRate * 2)
        short(2)
        short(16)
        out.write("data".toByteArray())
        int(dataSize)
        out.write(ByteArray(dataSize))
        return out.toByteArray()
    }

    /** Zapíše [bytes] do souboru v cache a vrátí jeho `file://` URI (soubor se maže s cache aplikace). */
    fun writeToCache(context: Context, name: String, bytes: ByteArray): Pair<File, String> {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return file to Uri.fromFile(file).toString()
    }
}
