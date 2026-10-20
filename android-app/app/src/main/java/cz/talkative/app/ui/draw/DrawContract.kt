package cz.talkative.app.ui.draw

import cz.talkative.app.domain.drawing.DrawPoint
import cz.talkative.app.domain.drawing.DrawingColors
import cz.talkative.app.domain.drawing.DrawingLimits
import cz.talkative.app.domain.drawing.DrawingTool

/**
 * Stav obrazovky kreslení. Samotné pixely plátna jsou v `DrawingBoard` (kopírovat by se musely při každém tahu);
 * [revision] se zvýší při každé změně plátna, aby se obrazovka překreslila.
 */
data class DrawUiState(
    val tool: DrawingTool = DrawingTool.BRUSH,
    val color: Int = DrawingColors.BLACK,
    val brushWidth: Int = DrawingLimits.DEFAULT_BRUSH_WIDTH,
    val canUndo: Boolean = false,
    /** Na plátně je něco jiného než bílá. */
    val isModified: Boolean = false,
    /** Běží výplň nebo ukládání; kreslení a další akce se tou dobou ignorují. */
    val isBusy: Boolean = false,
    val revision: Int = 0,
    /** Dotaz „Smazat vše?“. */
    val confirmClear: Boolean = false,
    /** Uložená kresba (URI souboru v cache), kterou má převzít editace slovíčka; po převzetí se vynuluje. */
    val savedImageUri: String? = null,
    val saveFailed: Boolean = false,
    /** Dotaz „Zahodit kresbu?“ při zavření rozkreslené kresby. */
    val confirmDiscard: Boolean = false,
)

sealed interface DrawEvent {
    data class SelectTool(val tool: DrawingTool) : DrawEvent
    data class SelectColor(val color: Int) : DrawEvent
    data class BrushWidthChanged(val width: Int) : DrawEvent

    /** Prst se dotkl plátna v bodě [point] (souřadnice plátna 0..511). U výplně to rovnou výplň spustí. */
    data class StrokeStarted(val point: DrawPoint) : DrawEvent
    data class StrokeMoved(val point: DrawPoint) : DrawEvent
    data object StrokeEnded : DrawEvent

    data object Undo : DrawEvent
    data object ClearAllRequested : DrawEvent
    data object ClearAllConfirmed : DrawEvent
    data object ClearAllDismissed : DrawEvent
    data object Save : DrawEvent

    /** Zavření rozkreslené kresby: zeptá se, jestli ji zahodit. Nezměněné plátno se zavírá bez dotazu přímo v obrazovce. */
    data object DiscardRequested : DrawEvent
    data object DiscardDismissed : DrawEvent
    data object SavedConsumed : DrawEvent
}
