package cz.talkative.app.domain.drawing

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Bod na plátně v pixelech (levý horní roh je 0, 0). */
data class DrawPoint(val x: Float, val y: Float)

/** Mezní hodnoty a výchozí hodnoty kreslení. */
object DrawingLimits {
    /** Plátno je čtverec 512 × 512 px, stejně jako největší strana uložených obrázků slovíček. */
    const val CANVAS_SIZE = 512
    const val MIN_BRUSH_WIDTH = 4
    const val MAX_BRUSH_WIDTH = 64
    const val DEFAULT_BRUSH_WIDTH = 16

    /** Nejvýše tolik kroků Zpět (každý snímek je 1 MB). */
    const val MAX_UNDO_STEPS = 20

    /** Jak moc se smí barva pixelu lišit od barvy v místě klepnutí (největší rozdíl kanálu R, G, B), aby ji výplň zahrnula. */
    const val FILL_TOLERANCE = 96
}

/** Barvy kreslení jako pevné hodnoty ARGB (výsledek je stejný bez ohledu na téma). */
object DrawingColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** Paleta nabízená uživateli; první je černá. */
    val PALETTE: List<Int> = listOf(
        BLACK,
        0xFFE53935.toInt(), // červená
        0xFFFB8C00.toInt(), // oranžová
        0xFFFDD835.toInt(), // žlutá
        0xFF43A047.toInt(), // zelená
        0xFF1E88E5.toInt(), // modrá
        0xFF8E24AA.toInt(), // fialová
        0xFF6D4C41.toInt(), // hnědá
    )
}

/**
 * Plátno 512 × 512 px, vždy neprůhledné a zpočátku celé bílé. Pracuje přímo s poli pixelů (ARGB), takže se dá testovat
 * na JVM bez Androidu. Tahy mají kulaté konce a vyhlazené okraje, výplň je scanline algoritmus bez rekurze.
 */
class PixelCanvas {
    val size = DrawingLimits.CANVAS_SIZE
    val pixels = IntArray(size * size) { DrawingColors.WHITE }

    fun pixelAt(x: Int, y: Int): Int = pixels[y * size + x]

    /** `true`, když je na plátně cokoli jiného než bílá. */
    fun hasContent(): Boolean = pixels.any { it != DrawingColors.WHITE }

    fun clear() = pixels.fill(DrawingColors.WHITE)

    fun copyPixels(): IntArray = pixels.copyOf()

    fun restore(snapshot: IntArray) = snapshot.copyInto(pixels)

    /** Nakreslí kruh (tečku) o průměru [width] se středem [point]. */
    fun drawDot(point: DrawPoint, color: Int, width: Int) = drawSegment(point, point, color, width)

    /** Nakreslí úsečku od [from] po [to] s kulatými konci o šířce [width] (omezené na povolené meze). */
    fun drawSegment(from: DrawPoint, to: DrawPoint, color: Int, width: Int) {
        val radius = width.coerceIn(DrawingLimits.MIN_BRUSH_WIDTH, DrawingLimits.MAX_BRUSH_WIDTH) / 2f
        val minX = max(0, floor(min(from.x, to.x) - radius - 1).toInt())
        val maxX = min(size - 1, ceil(max(from.x, to.x) + radius + 1).toInt())
        val minY = max(0, floor(min(from.y, to.y) - radius - 1).toInt())
        val maxY = min(size - 1, ceil(max(from.y, to.y) + radius + 1).toInt())
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val distance = distanceToSegment(x + 0.5f, y + 0.5f, from, to)
                // Pixel uvnitř je celý, na okraji se barva podle pokrytí smíchá s podkladem (vyhlazení).
                val coverage = (radius + 0.5f - distance).coerceIn(0f, 1f)
                if (coverage > 0f) {
                    val index = y * size + x
                    pixels[index] = if (coverage >= 1f) color else blend(pixels[index], color, coverage)
                }
            }
        }
    }

    /**
     * Zaplní souvislou oblast podobných barev kolem bodu [x], [y] barvou [color]. Pixel patří do oblasti, když se jeho
     * barva od barvy v bodě klepnutí liší nejvýše o [tolerance] (vyhlazené okraje čar pak nezanechají světlý lem).
     * Mimo plátno nebo při stejné barvě se nic nestane. Vrací počet zaplněných pixelů.
     */
    fun floodFill(x: Int, y: Int, color: Int, tolerance: Int = DrawingLimits.FILL_TOLERANCE): Int {
        if (x !in 0 until size || y !in 0 until size) return 0
        val target = pixelAt(x, y)
        if (target == color) return 0
        val visited = BooleanArray(size * size)
        var stack = IntArray(1024) // zásobník semínek (jeden na souvislý úsek v sousedním řádku), podle potřeby roste
        var top = 0
        var filled = 0

        fun push(index: Int) {
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = index
        }

        fun matches(index: Int) = !visited[index] && colorDistance(pixels[index], target) <= tolerance

        push(y * size + x)
        while (top > 0) {
            val seed = stack[--top]
            if (visited[seed]) continue // úsek už zaplnilo jiné semínko
            val row = seed / size
            var left = seed % size
            var right = left
            while (left > 0 && matches(row * size + left - 1)) left--
            while (right < size - 1 && matches(row * size + right + 1)) right++
            for (px in left..right) {
                val index = row * size + px
                visited[index] = true
                pixels[index] = color
                filled++
            }
            for (neighbour in intArrayOf(row - 1, row + 1)) {
                if (neighbour !in 0 until size) continue
                var px = left
                while (px <= right) {
                    if (matches(neighbour * size + px)) {
                        push(neighbour * size + px)
                        // celý souvislý úsek dostane jedno semínko
                        while (px <= right && matches(neighbour * size + px)) px++
                    } else {
                        px++
                    }
                }
            }
        }
        return filled
    }

    private fun distanceToSegment(px: Float, py: Float, from: DrawPoint, to: DrawPoint): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - from.x) * dx + (py - from.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val cx = from.x + t * dx
        val cy = from.y + t * dy
        val ex = px - cx
        val ey = py - cy
        return sqrt(ex * ex + ey * ey)
    }

    private fun blend(under: Int, over: Int, coverage: Float): Int {
        fun channel(shift: Int): Int {
            val a = (under shr shift) and 0xFF
            val b = (over shr shift) and 0xFF
            return (a + (b - a) * coverage).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun colorDistance(a: Int, b: Int): Int = max(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        max(abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
    )
}
