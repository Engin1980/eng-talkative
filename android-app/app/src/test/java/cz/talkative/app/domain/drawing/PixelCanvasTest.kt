package cz.talkative.app.domain.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelCanvasTest {

    private val canvas = PixelCanvas()
    private val red = 0xFFE53935.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val black = DrawingColors.BLACK
    private val white = DrawingColors.WHITE

    private fun countOf(color: Int) = canvas.pixels.count { it == color }

    private fun countNot(color: Int) = canvas.pixels.count { it != color }

    @Test
    fun newCanvas_is512Square_allWhite_andOpaque() {
        assertEquals(512, canvas.size)
        assertEquals(512 * 512, canvas.pixels.size)
        assertEquals(512 * 512, countOf(white))
        assertFalse(canvas.hasContent())
    }

    @Test
    fun stroke_coloursPixelsAlongTheLine_andNothingFarFromIt() {
        canvas.drawSegment(DrawPoint(50f, 100f), DrawPoint(450f, 100f), black, width = 10)

        assertEquals(black, canvas.pixelAt(250, 100))
        assertEquals(black, canvas.pixelAt(60, 102))
        assertEquals(white, canvas.pixelAt(250, 120)) // daleko od čáry
        assertEquals(white, canvas.pixelAt(250, 80))
        assertEquals(white, canvas.pixelAt(20, 100)) // před začátkem čáry
        assertEquals(white, canvas.pixelAt(480, 100))
        assertEquals(white, canvas.pixelAt(0, 0))
        assertEquals(white, canvas.pixelAt(511, 511))
    }

    @Test
    fun wideBrush_coloursMorePixelsThanNarrowOne() {
        val narrow = PixelCanvas().also { it.drawSegment(DrawPoint(50f, 50f), DrawPoint(400f, 300f), black, 6) }
        val wide = PixelCanvas().also { it.drawSegment(DrawPoint(50f, 50f), DrawPoint(400f, 300f), black, 40) }

        assertTrue(wide.pixels.count { it != white } > narrow.pixels.count { it != white } * 3)
    }

    @Test
    fun widthOutsideTheLimits_isClamped() {
        val tooThin = PixelCanvas().also { it.drawDot(DrawPoint(100f, 100f), black, 0) }
        val tooWide = PixelCanvas().also { it.drawDot(DrawPoint(256f, 256f), black, 500) }

        assertTrue(tooThin.hasContent())
        assertEquals(white, tooWide.pixelAt(256 + 40, 256)) // širší než 64 px se nekreslí
        assertEquals(black, tooWide.pixelAt(256 + 30, 256))
    }

    @Test
    fun tapWithoutMovement_drawsARoundDot() {
        canvas.drawDot(DrawPoint(200f, 200f), red, 20)

        assertEquals(red, canvas.pixelAt(200, 200))
        assertEquals(red, canvas.pixelAt(208, 200)) // uvnitř poloměru 10
        assertEquals(white, canvas.pixelAt(215, 200))
        // kulatý tvar: roh čtverce o straně 20 už je venku
        assertEquals(white, canvas.pixelAt(209, 209))
        val dotPixels = countNot(white)
        assertTrue("plocha tečky: $dotPixels", dotPixels in 250..400) // π·10² ≈ 314
    }

    @Test
    fun strokeEnds_areRound() {
        canvas.drawSegment(DrawPoint(100f, 100f), DrawPoint(300f, 100f), black, 20)

        assertEquals(black, canvas.pixelAt(92, 100)) // kulatý konec zasahuje o poloměr za bod
        assertEquals(white, canvas.pixelAt(91, 92)) // roh čtvercového konce by tu byl zaplněný
    }

    @Test
    fun strokeEdges_areSmoothed() {
        canvas.drawSegment(DrawPoint(50f, 100.2f), DrawPoint(450f, 100.2f), black, 9)

        val edge = canvas.pixelAt(250, 95) // hraniční pixel: ani celý černý, ani bílý
        val grey = edge and 0xFF
        assertTrue("okraj má mít mezibarvu, ale je $grey", grey in 1..254)
    }

    @Test
    fun eraserColour_isWhite_soTheCanvasStaysOpaque() {
        canvas.drawSegment(DrawPoint(50f, 100f), DrawPoint(450f, 100f), black, 20)
        canvas.drawSegment(DrawPoint(200f, 100f), DrawPoint(300f, 100f), white, 20)

        assertEquals(white, canvas.pixelAt(250, 100))
        assertEquals(black, canvas.pixelAt(100, 100))
        assertTrue(canvas.pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun segmentsOutsideTheCanvas_areClipped_withoutCrash() {
        canvas.drawSegment(DrawPoint(-100f, -100f), DrawPoint(700f, 700f), black, 30)
        canvas.drawDot(DrawPoint(-50f, 900f), red, 30)

        assertEquals(black, canvas.pixelAt(256, 256))
    }

    /** Čtverec 100×100 s černým obrysem (3 px), uvnitř bílo. */
    private fun drawClosedSquare() {
        val a = DrawPoint(100f, 100f)
        val b = DrawPoint(200f, 100f)
        val c = DrawPoint(200f, 200f)
        val d = DrawPoint(100f, 200f)
        canvas.drawSegment(a, b, black, 4)
        canvas.drawSegment(b, c, black, 4)
        canvas.drawSegment(c, d, black, 4)
        canvas.drawSegment(d, a, black, 4)
    }

    @Test
    fun fill_inClosedShape_fillsOnlyTheInside() {
        drawClosedSquare()

        val filled = canvas.floodFill(150, 150, red)

        assertEquals(red, canvas.pixelAt(150, 150))
        assertEquals(red, canvas.pixelAt(105, 105))
        assertEquals(white, canvas.pixelAt(50, 50)) // venku zůstalo bílo
        assertEquals(white, canvas.pixelAt(300, 300))
        assertEquals(black, canvas.pixelAt(100, 150)) // čára se nezměnila
        assertEquals(filled, countOf(red))
        assertTrue("vyplněno $filled px", filled in 8_000..10_000) // 100×100 bez obrysu
    }

    @Test
    fun fill_outsideTheShape_doesNotEnterIt() {
        drawClosedSquare()

        canvas.floodFill(10, 10, blue)

        assertEquals(blue, canvas.pixelAt(300, 300))
        assertEquals(white, canvas.pixelAt(150, 150)) // uvnitř uzavřeného tvaru zůstalo
    }

    @Test
    fun fill_onALine_doesNotLeakAcrossIt() {
        canvas.drawSegment(DrawPoint(0f, 256f), DrawPoint(511f, 256f), black, 8) // čára přes celé plátno

        canvas.floodFill(100, 100, red)

        assertEquals(red, canvas.pixelAt(300, 10))
        assertEquals(white, canvas.pixelAt(300, 400)) // pod čárou zůstalo bílo
    }

    @Test
    fun fill_reachesTheSmoothedEdges_throughTolerance() {
        val a = DrawPoint(100.3f, 100.4f)
        val b = DrawPoint(300.7f, 250.2f)
        canvas.drawSegment(a, b, black, 5) // šikmá čára má vyhlazený okraj
        canvas.drawSegment(DrawPoint(300f, 100f), DrawPoint(300f, 300f), black, 5)
        canvas.drawSegment(DrawPoint(100f, 300f), DrawPoint(300f, 300f), black, 5)
        canvas.drawSegment(DrawPoint(100f, 100f), DrawPoint(100f, 300f), black, 5)

        canvas.floodFill(150, 250, red)

        // vedle šikmé čáry nezůstal světlý lem: žádný bílý pixel v bezprostředním okolí čáry na straně výplně
        val nearEdge = (120..250).count { x ->
            val y = (100.4f + (250.2f - 100.4f) * (x - 100.3f) / (300.7f - 100.3f)).toInt() + 5
            canvas.pixelAt(x, y) == white
        }
        assertEquals(0, nearEdge)
    }

    @Test
    fun fill_withTheSameColour_doesNothing() {
        assertEquals(0, canvas.floodFill(10, 10, white))
        assertFalse(canvas.hasContent())

        canvas.drawDot(DrawPoint(200f, 200f), red, 30)
        val before = canvas.copyPixels()
        assertEquals(0, canvas.floodFill(200, 200, red))
        assertTrue(before.contentEquals(canvas.pixels))
    }

    @Test
    fun fill_outsideTheCanvas_isIgnored() {
        assertEquals(0, canvas.floodFill(-1, 10, red))
        assertEquals(0, canvas.floodFill(10, 512, red))
        assertEquals(0, canvas.floodFill(9999, 9999, red))
        assertFalse(canvas.hasContent())
    }

    @Test
    fun fill_ofTheWholeCanvas_isFast_andDoesNotCrash() {
        val start = System.nanoTime()
        val filled = canvas.floodFill(0, 0, red)
        val millis = (System.nanoTime() - start) / 1_000_000

        assertEquals(512 * 512, filled)
        assertEquals(512 * 512, countOf(red))
        println("Výplň celého plátna 512×512 na JVM: $millis ms")
        assertTrue("výplň trvala $millis ms", millis < 1_000)
    }

    @Test
    fun fill_ofAComplexRegion_withManySpans_matchesAReferenceFill() {
        // hřeben svislých čar: oblast je jedna souvislá, ale s mnoha úseky v každém řádku
        for (x in 20..480 step 20) canvas.drawSegment(DrawPoint(x.toFloat(), 40f), DrawPoint(x.toFloat(), 400f), black, 4)

        val filled = canvas.floodFill(10, 10, blue)

        assertEquals(filled, countOf(blue))
        assertEquals(blue, canvas.pixelAt(30, 200)) // mezi čarami se výplň dostala obloukem přes volný okraj
        assertEquals(blue, canvas.pixelAt(250, 450))
        assertNotEquals(white, canvas.pixelAt(30, 200))
    }

    @Test
    fun clearAndRestore_workOnTheWholeCanvas() {
        canvas.drawDot(DrawPoint(100f, 100f), red, 30)
        val snapshot = canvas.copyPixels()

        canvas.clear()
        assertFalse(canvas.hasContent())
        canvas.restore(snapshot)

        assertEquals(red, canvas.pixelAt(100, 100))
    }
}
