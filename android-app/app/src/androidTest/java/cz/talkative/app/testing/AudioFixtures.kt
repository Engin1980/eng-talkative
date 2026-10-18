package cz.talkative.app.testing

import android.content.Context
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
