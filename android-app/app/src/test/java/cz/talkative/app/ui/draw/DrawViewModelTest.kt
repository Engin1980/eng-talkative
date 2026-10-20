package cz.talkative.app.ui.draw

import cz.talkative.app.domain.drawing.DrawPoint
import cz.talkative.app.domain.drawing.DrawingColors
import cz.talkative.app.domain.drawing.DrawingLimits
import cz.talkative.app.domain.drawing.DrawingStorage
import cz.talkative.app.domain.drawing.DrawingTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DrawViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeStorage(private val directory: File) : DrawingStorage {
        var failure: IOException? = null
        val saved = mutableListOf<ByteArray>()

        override suspend fun save(png: ByteArray): File {
            failure?.let { throw it }
            saved += png
            return File(directory, "drawing-${saved.size}.png").also { it.writeBytes(png) }
        }
    }

    private lateinit var storage: FakeStorage
    private lateinit var viewModel: DrawViewModel
    private val red = DrawingColors.PALETTE[1]

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        storage = FakeStorage(temp.newFolder("drawings"))
        viewModel = DrawViewModel(storage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    private fun awaitIdle() = runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.isBusy } } }

    private fun drawLine(y: Float = 100f) {
        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(50f, y)))
        viewModel.onEvent(DrawEvent.StrokeMoved(DrawPoint(200f, y)))
        viewModel.onEvent(DrawEvent.StrokeMoved(DrawPoint(350f, y)))
        viewModel.onEvent(DrawEvent.StrokeEnded)
    }

    @Test
    fun startsWithBlackBrushOfDefaultWidth_onAnEmptyCanvas() {
        assertEquals(DrawingTool.BRUSH, state.tool)
        assertEquals(DrawingColors.BLACK, state.color)
        assertEquals(DrawingLimits.DEFAULT_BRUSH_WIDTH, state.brushWidth)
        assertFalse(state.isModified)
        assertFalse(state.canUndo)
    }

    @Test
    fun toolColourAndWidth_canBeChanged() {
        viewModel.onEvent(DrawEvent.SelectTool(DrawingTool.ERASER))
        viewModel.onEvent(DrawEvent.SelectColor(red))
        viewModel.onEvent(DrawEvent.BrushWidthChanged(40))

        assertEquals(DrawingTool.ERASER, state.tool)
        assertEquals(red, state.color)
        assertEquals(40, state.brushWidth)
    }

    @Test
    fun stroke_changesTheCanvas_andEnablesUndoAndSave() {
        viewModel.onEvent(DrawEvent.SelectColor(red))
        val revision = state.revision

        drawLine()

        assertEquals(red, viewModel.board.canvas.pixelAt(200, 100))
        assertTrue(state.isModified)
        assertTrue(state.canUndo)
        assertTrue(state.revision > revision)
    }

    @Test
    fun chosenWidth_isUsedByTheBrush() {
        viewModel.onEvent(DrawEvent.BrushWidthChanged(4))
        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(100f, 100f)))
        viewModel.onEvent(DrawEvent.StrokeEnded)
        val thin = viewModel.board.canvas.pixels.count { it != DrawingColors.WHITE }

        viewModel.onEvent(DrawEvent.Undo)
        viewModel.onEvent(DrawEvent.BrushWidthChanged(60))
        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(100f, 100f)))
        viewModel.onEvent(DrawEvent.StrokeEnded)
        val wide = viewModel.board.canvas.pixels.count { it != DrawingColors.WHITE }

        assertTrue("tenký $thin, široký $wide", wide > thin * 20)
    }

    @Test
    fun eraser_paintsWhiteOverTheStroke() {
        drawLine()
        viewModel.onEvent(DrawEvent.SelectTool(DrawingTool.ERASER))

        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(150f, 100f)))
        viewModel.onEvent(DrawEvent.StrokeMoved(DrawPoint(250f, 100f)))
        viewModel.onEvent(DrawEvent.StrokeEnded)

        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(200, 100))
        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(70, 100))
    }

    @Test
    fun undo_revertsTheLastAction() {
        drawLine(100f)
        drawLine(200f)

        viewModel.onEvent(DrawEvent.Undo)

        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(200, 200))
        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(200, 100))
        assertTrue(state.canUndo)

        viewModel.onEvent(DrawEvent.Undo)

        assertFalse(state.isModified)
        assertFalse(state.canUndo)
    }

    @Test
    fun fill_fillsTheBackground_showsBusy_andCanBeUndone() {
        viewModel.onEvent(DrawEvent.SelectTool(DrawingTool.FILL))
        viewModel.onEvent(DrawEvent.SelectColor(red))

        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(10f, 10f)))
        awaitIdle()
        viewModel.onEvent(DrawEvent.StrokeEnded)

        assertEquals(red, viewModel.board.canvas.pixelAt(300, 300))
        assertTrue(state.isModified)
        assertTrue(state.canUndo)
        assertFalse(state.isBusy)

        viewModel.onEvent(DrawEvent.Undo)
        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(300, 300))
    }

    @Test
    fun fillTool_ignoresDragging_andDoesNotDrawStrokes() {
        viewModel.onEvent(DrawEvent.SelectTool(DrawingTool.FILL))
        viewModel.onEvent(DrawEvent.StrokeStarted(DrawPoint(10f, 10f)))
        awaitIdle()
        val before = viewModel.board.canvas.copyPixels()

        viewModel.onEvent(DrawEvent.StrokeMoved(DrawPoint(300f, 300f)))
        viewModel.onEvent(DrawEvent.StrokeEnded)

        assertTrue(before.contentEquals(viewModel.board.canvas.pixels))
    }

    @Test
    fun clearAll_asksFirst_thenClears_andCanBeUndone() {
        drawLine()

        viewModel.onEvent(DrawEvent.ClearAllRequested)
        assertTrue(state.confirmClear)
        assertTrue(state.isModified) // zatím se nic nesmazalo

        viewModel.onEvent(DrawEvent.ClearAllDismissed)
        assertFalse(state.confirmClear)
        assertTrue(state.isModified)

        viewModel.onEvent(DrawEvent.ClearAllRequested)
        viewModel.onEvent(DrawEvent.ClearAllConfirmed)
        assertFalse(state.confirmClear)
        assertFalse(state.isModified)

        viewModel.onEvent(DrawEvent.Undo)
        assertTrue(state.isModified)
    }

    @Test
    fun clearAll_onAnEmptyCanvas_doesNotAsk() {
        viewModel.onEvent(DrawEvent.ClearAllRequested)

        assertFalse(state.confirmClear)
    }

    @Test
    fun save_onAnEmptyCanvas_doesNothing() {
        viewModel.onEvent(DrawEvent.Save)

        assertTrue(storage.saved.isEmpty())
        assertNull(state.savedImageUri)
    }

    @Test
    fun save_writesAPng_andHandsOverTheFileOnce() {
        drawLine()

        viewModel.onEvent(DrawEvent.Save)
        awaitIdle()

        assertEquals(1, storage.saved.size)
        assertTrue(storage.saved.single().take(4) == listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        val uri = state.savedImageUri!!
        assertTrue(uri.startsWith("file:") && uri.endsWith("drawing-1.png"))

        viewModel.onEvent(DrawEvent.SavedConsumed)
        assertNull(state.savedImageUri)
    }

    @Test
    fun saveFailure_isShown_andTheDrawingStaysEditable() {
        drawLine()
        storage.failure = IOException("místo došlo")

        viewModel.onEvent(DrawEvent.Save)
        awaitIdle()

        assertTrue(state.saveFailed)
        assertNull(state.savedImageUri)
        assertTrue(state.isModified)

        storage.failure = null
        viewModel.onEvent(DrawEvent.Save)
        awaitIdle()

        assertFalse(state.saveFailed)
        assertTrue(state.savedImageUri != null)
    }

    @Test
    fun theDrawingSurvivesAsLongAsTheViewModelDoes() {
        drawLine()
        val pixels = viewModel.board.canvas.pixels

        // „otočení“: obrazovka se znovu složí nad stejným view modelem, plátno je pořád v něm
        assertTrue(state.isModified)
        assertEquals(DrawingColors.BLACK, pixels[100 * 512 + 200])
    }

    @Test
    fun discardQuestion_isShownAndDismissed() {
        drawLine()

        viewModel.onEvent(DrawEvent.DiscardRequested)
        assertTrue(state.confirmDiscard)
        viewModel.onEvent(DrawEvent.DiscardDismissed)

        assertFalse(state.confirmDiscard)
        assertTrue(state.isModified) // zeptání nic nezměnilo
        assertTrue(storage.saved.isEmpty())
    }
}
