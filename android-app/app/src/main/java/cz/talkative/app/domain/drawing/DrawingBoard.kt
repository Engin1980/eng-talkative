package cz.talkative.app.domain.drawing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Nástroje kreslení. Výplň nepoužívá šířku štětce. */
enum class DrawingTool { BRUSH, ERASER, FILL }

/**
 * Plátno s historií pro Zpět. Každá akce (tah od přiložení prstu po zvednutí, výplň, smazání všeho) je jeden krok:
 * před ní se uloží snímek plátna, nejvýše [DrawingLimits.MAX_UNDO_STEPS] snímků. Redo se nedělá.
 *
 * Tahy se kreslí na hlavním vlákně, výplň běží na pozadí ([fill]); během ní ([isBusy]) se další akce ignorují,
 * takže se nikdy nezpracovávají souběžně.
 */
class DrawingBoard {
    val canvas = PixelCanvas()

    private val history = ArrayDeque<IntArray>()
    private var lastPoint: DrawPoint? = null
    private var strokeColor = DrawingColors.BLACK
    private var strokeWidth = DrawingLimits.DEFAULT_BRUSH_WIDTH

    @Volatile
    var isBusy = false
        private set

    /** Počet kroků, které jde vrátit. */
    val undoSteps: Int get() = history.size

    val canUndo: Boolean get() = history.isNotEmpty()

    /** Je na plátně něco jiného než bílá? (Po Zpět na prázdné plátno je `false`.) */
    val isModified: Boolean get() = canvas.hasContent()

    /** Začne tah v bodě [start]; samotné klepnutí bez posunu tak nakreslí tečku. Guma kreslí bílou. */
    fun beginStroke(start: DrawPoint, tool: DrawingTool, color: Int, width: Int) {
        if (isBusy || tool == DrawingTool.FILL) return
        pushSnapshot()
        strokeColor = if (tool == DrawingTool.ERASER) DrawingColors.WHITE else color
        strokeWidth = width
        lastPoint = start
        canvas.drawDot(start, strokeColor, strokeWidth)
    }

    /** Prodlouží tah do bodu [point]. */
    fun extendStroke(point: DrawPoint) {
        val from = lastPoint ?: return
        if (isBusy) return
        canvas.drawSegment(from, point, strokeColor, strokeWidth)
        lastPoint = point
    }

    fun endStroke() {
        lastPoint = null
    }

    /** Celý tah najednou (pro testy a jednoduchá použití): jeden krok Zpět. */
    fun stroke(points: List<DrawPoint>, tool: DrawingTool, color: Int, width: Int) {
        if (points.isEmpty()) return
        beginStroke(points.first(), tool, color, width)
        points.drop(1).forEach { extendStroke(it) }
        endStroke()
    }

    /** Výplň na pozadí (`Dispatchers.Default`); během ní jsou ostatní akce ignorované. Vrací, kolik pixelů se zaplnilo. */
    suspend fun fill(x: Int, y: Int, color: Int, tolerance: Int = DrawingLimits.FILL_TOLERANCE): Int {
        if (isBusy) return 0
        isBusy = true
        try {
            return withContext(Dispatchers.Default) {
                val before = canvas.copyPixels()
                val filled = canvas.floodFill(x, y, color, tolerance)
                if (filled > 0) addSnapshot(before) // bez změny se do historie nic nepřidává
                filled
            }
        } finally {
            isBusy = false
        }
    }

    /** Smaže celé plátno (bílé); jde vrátit přes [undo]. */
    fun clearAll() {
        if (isBusy) return
        pushSnapshot()
        canvas.clear()
    }

    /** Vrátí poslední akci; `false`, když není co vracet. */
    fun undo(): Boolean {
        if (isBusy) return false
        val snapshot = history.removeLastOrNull() ?: return false
        canvas.restore(snapshot)
        lastPoint = null
        return true
    }

    private fun pushSnapshot() = addSnapshot(canvas.copyPixels())

    private fun addSnapshot(snapshot: IntArray) {
        history.addLast(snapshot)
        while (history.size > DrawingLimits.MAX_UNDO_STEPS) history.removeFirst()
    }
}
