package cz.talkative.app.domain.drawing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class DrawingBoardTest {

    private val board = DrawingBoard()
    private val red = 0xFFE53935.toInt()
    private val black = DrawingColors.BLACK
    private val white = DrawingColors.WHITE

    private fun line(y: Float) = listOf(DrawPoint(50f, y), DrawPoint(200f, y), DrawPoint(350f, y))

    private fun stroke(y: Float, color: Int = black) = board.stroke(line(y), DrawingTool.BRUSH, color, 10)

    @Test
    fun newBoard_isEmpty_andNothingToUndo() {
        assertFalse(board.isModified)
        assertFalse(board.canUndo)
        assertFalse(board.undo())
    }

    @Test
    fun oneStroke_isOneUndoStep_regardlessOfTheNumberOfPoints() {
        stroke(100f)

        assertTrue(board.isModified)
        assertEquals(1, board.undoSteps)
        assertTrue(board.undo())
        assertFalse(board.isModified)
        assertFalse(board.canUndo)
    }

    @Test
    fun strokeInSeparateCalls_isOneStep_andTapMakesADot() {
        board.beginStroke(DrawPoint(100f, 100f), DrawingTool.BRUSH, red, 20)
        board.endStroke() // klepnutí bez posunu

        assertEquals(red, board.canvas.pixelAt(100, 100))
        assertEquals(1, board.undoSteps)

        board.beginStroke(DrawPoint(200f, 200f), DrawingTool.BRUSH, black, 10)
        board.extendStroke(DrawPoint(250f, 200f))
        board.extendStroke(DrawPoint(300f, 220f))
        board.endStroke()

        assertEquals(2, board.undoSteps)
        assertEquals(black, board.canvas.pixelAt(250, 200))
    }

    @Test
    fun eraser_paintsWhite_andIsAnActionOfItsOwn() {
        stroke(100f)
        board.stroke(listOf(DrawPoint(150f, 100f), DrawPoint(250f, 100f)), DrawingTool.ERASER, red, 20) // barva se u gumy ignoruje

        assertEquals(white, board.canvas.pixelAt(200, 100))
        assertEquals(black, board.canvas.pixelAt(70, 100))
        assertEquals(2, board.undoSteps)

        board.undo()
        assertEquals(black, board.canvas.pixelAt(200, 100)) // Zpět vrátilo i mazání
    }

    @Test
    fun extendingWithoutBegin_doesNothing() {
        board.extendStroke(DrawPoint(10f, 10f))

        assertFalse(board.isModified)
        assertEquals(0, board.undoSteps)
    }

    @Test
    fun fillTool_doesNotStartAStroke() {
        board.beginStroke(DrawPoint(10f, 10f), DrawingTool.FILL, red, 10)

        assertFalse(board.isModified)
        assertEquals(0, board.undoSteps)
    }

    @Test
    fun fill_isOneStep_andUndoRestoresTheOldColours() = runBlocking {
        stroke(100f)
        val filled = board.fill(256, 300, red)

        assertTrue(filled > 0)
        assertEquals(red, board.canvas.pixelAt(256, 300))
        assertEquals(2, board.undoSteps)

        board.undo()

        assertEquals(white, board.canvas.pixelAt(256, 300))
        assertEquals(black, board.canvas.pixelAt(200, 100)) // tah zůstal
        assertFalse(board.isBusy)
    }

    @Test
    fun fillThatChangesNothing_doesNotAddAnUndoStep() = runBlocking {
        board.fill(10, 10, white) // stejná barva
        board.fill(-5, 10, red) // mimo plátno

        assertEquals(0, board.undoSteps)
        assertFalse(board.isModified)
    }

    @Test
    fun undo_keepsAtMostTwentySteps_andTheTwentyFirstDoesNotRestoreTheBlankCanvas() {
        repeat(21) { index -> stroke(10f + index * 20f) }

        assertEquals(20, board.undoSteps)
        repeat(20) { assertTrue(board.undo()) }
        assertFalse(board.undo()) // 21. krok Zpět už není

        // po 20 krocích zpět zůstal jen první tah, ne původní prázdný stav
        assertTrue(board.isModified)
        assertEquals(black, board.canvas.pixelAt(100, 10))
        assertEquals(white, board.canvas.pixelAt(100, 30))
    }

    @Test
    fun clearAll_isUndoable() {
        stroke(100f)

        board.clearAll()
        assertFalse(board.isModified)
        assertEquals(2, board.undoSteps)

        board.undo()
        assertTrue(board.isModified)
        assertEquals(black, board.canvas.pixelAt(200, 100))
    }

    @Test
    fun isModified_isFalseAgainAfterUndoingEverything() {
        stroke(100f)
        stroke(200f)
        board.undo()
        board.undo()

        assertFalse(board.isModified)
    }

    @Test
    fun pngEncoder_producesAReadablePng_thatMatchesTheCanvas() {
        stroke(100f)
        board.canvas.drawDot(DrawPoint(400f, 400f), red, 30)

        val png = PngEncoder.encode(board.canvas.pixels, board.canvas.size, board.canvas.size)

        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        for ((x, y) in listOf(0 to 0, 100 to 100, 200 to 100, 400 to 400, 511 to 511, 300 to 300)) {
            assertEquals("pixel $x,$y", board.canvas.pixelAt(x, y), image.getRGB(x, y))
        }
    }

    @Test
    fun pngEncoder_rejectsWrongSize() {
        val failure = runCatching { PngEncoder.encode(IntArray(10), 4, 4) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
