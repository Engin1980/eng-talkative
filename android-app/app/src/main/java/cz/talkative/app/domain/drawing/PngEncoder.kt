package cz.talkative.app.domain.drawing

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Zápis neprůhledných pixelů (ARGB, průhlednost se ignoruje) do PNG (RGB, 8 bitů). Bez Androidu, takže jde testovat na JVM. */
object PngEncoder {

    fun encode(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(pixels.size == width * height) { "Počet pixelů neodpovídá rozměrům" }
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        writeChunk(out, "IHDR", header(width, height))
        writeChunk(out, "IDAT", compress(rawScanlines(pixels, width, height)))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun header(width: Int, height: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(width)
            writeInt(height)
            writeByte(8) // bitů na kanál
            writeByte(2) // RGB
            writeByte(0) // komprese
            writeByte(0) // filtr
            writeByte(0) // bez prokládání
        }
        return bytes.toByteArray()
    }

    /** Každý řádek začíná bajtem filtru 0 (bez filtru), pak následují R, G, B jednotlivých pixelů. */
    private fun rawScanlines(pixels: IntArray, width: Int, height: Int): ByteArray {
        val raw = ByteArray(height * (1 + width * 3))
        var position = 0
        for (y in 0 until height) {
            raw[position++] = 0
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                raw[position++] = (pixel shr 16).toByte()
                raw[position++] = (pixel shr 8).toByte()
                raw[position++] = pixel.toByte()
            }
        }
        return raw
    }

    private fun compress(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val stream = DataOutputStream(out)
        stream.writeInt(data.size)
        stream.write(typeBytes)
        stream.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        stream.writeInt(crc.value.toInt())
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
}
