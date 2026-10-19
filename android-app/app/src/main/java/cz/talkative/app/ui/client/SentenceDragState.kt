package cz.talkative.app.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import cz.talkative.app.domain.model.AddWordMode

/**
 * Stav tažení slov mezi mřížkou a větou: co se táhne a kde je prst (souřadnice okna), plus plocha řádku věty,
 * aby se dalo poznat, kam se prvek pustil. O tom, co puštění udělá, rozhoduje čistá funkce [resolveDrop].
 */
@Stable
class SentenceDragState(private val onEvent: (ClientEvent) -> Unit) {
    var dragged: DragSource? by mutableStateOf(null)
        private set
    var pointer: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Jen pro rozhodnutí při puštění; zda se dlaždice dají táhnout, předává kompozice parametrem. */
    var addMode: AddWordMode = AddWordMode.TAP

    var barCoordinates: LayoutCoordinates? = null

    internal fun start(source: DragSource, position: Offset) {
        dragged = source
        pointer = position
    }

    internal fun move(position: Offset) {
        pointer = position
    }

    internal fun drop() {
        val source = dragged ?: return
        dragged = null
        resolveDrop(source, pointer, barCoordinates.windowBounds(), addMode)?.let(onEvent)
    }

    internal fun cancel() {
        dragged = null
    }
}

internal fun LayoutCoordinates?.windowBounds(): Rect? =
    this?.takeIf { it.isAttached }?.let { Rect(it.positionInWindow(), it.size.toSize()) }

@Composable
fun rememberSentenceDragState(addMode: AddWordMode, onEvent: (ClientEvent) -> Unit): SentenceDragState {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val state = remember { SentenceDragState { currentOnEvent(it) } }
    state.addMode = addMode
    return state
}

/** Plocha řádku věty, kam se slova pouštějí. */
fun Modifier.dropTarget(state: SentenceDragState): Modifier = onGloballyPositioned { state.barCoordinates = it }

/**
 * Prvek, který lze táhnout prstem ([source]). Gesto začne až po překonání prahu pohybu, takže klepnutí
 * a podržení fungují dál jako dosud. Po dobu tažení [state] drží polohu prstu pro zobrazení tažené dlaždice.
 */
@Composable
fun Modifier.dragSource(enabled: Boolean, source: DragSource, state: SentenceDragState): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val currentSource by rememberUpdatedState(source)
    DisposableEffect(Unit) { onDispose { if (state.dragged == currentSource) state.cancel() } }
    return onGloballyPositioned { origin = it.positionInWindow() }.then(
        if (!enabled) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { state.start(currentSource, origin + it) },
                    onDrag = { change, _ ->
                        change.consume()
                        state.move(origin + change.position)
                    },
                    onDragEnd = { state.drop() },
                    onDragCancel = { state.cancel() },
                )
            }
        },
    )
}
