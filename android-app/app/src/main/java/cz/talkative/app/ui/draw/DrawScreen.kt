package cz.talkative.app.ui.draw

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.drawing.DrawPoint
import cz.talkative.app.domain.drawing.DrawingColors
import cz.talkative.app.domain.drawing.DrawingLimits
import cz.talkative.app.domain.drawing.DrawingTool
import cz.talkative.app.ui.theme.TalkativeTheme

/** Značky prvků obrazovky, podle kterých testy hledají. */
const val DRAW_CANVAS_TAG = "draw-canvas"
const val DRAW_BUSY_TAG = "draw-busy"
const val DRAW_BRUSH_PREVIEW_TAG = "draw-brush-preview"
const val DRAW_WIDTH_SLIDER_TAG = "draw-width-slider"
const val DRAW_SAVE_FAILED_TAG = "draw-save-failed"

fun drawToolTag(tool: DrawingTool) = "draw-tool-${tool.name}"

fun drawColorTag(color: Int) = "draw-color-${Integer.toHexString(color)}"

/**
 * Napojení obrazovky na view model. Uložená kresba ([onSaved] dostane URI souboru v cache) se předá jen jednou
 * a view model si pak vynuluje.
 */
@Composable
fun DrawRoute(onSaved: (String) -> Unit, onClose: () -> Unit, viewModel: DrawViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedImageUri) {
        state.savedImageUri?.let {
            onSaved(it)
            viewModel.onEvent(DrawEvent.SavedConsumed)
        }
    }
    DrawScreen(state = state, pixels = viewModel.board.canvas.pixels, onEvent = viewModel::onEvent, onClose = onClose)
}

/**
 * Bezstavová obrazovka kreslení. [pixels] jsou živé pixely plátna (ARGB, 512 × 512); překreslí se, kdykoli se změní
 * [DrawUiState.revision]. Na šířku je panel nástrojů vedle plátna, na výšku pod ním.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawScreen(
    state: DrawUiState,
    pixels: IntArray,
    onEvent: (DrawEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    // Nezměněné plátno se zavře hned, rozkreslenou kresbu je třeba nejdřív potvrdit zahodit.
    val requestClose = { if (state.isModified) onEvent(DrawEvent.DiscardRequested) else onClose() }
    BackHandler { requestClose() }
    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(dimens.spaceMedium)) {
        val landscape = maxWidth > maxHeight
        val side = if (landscape) minOf(maxHeight, maxWidth - dimens.drawToolbarMinWidth) else minOf(maxWidth, maxHeight - dimens.drawToolbarMinHeight)
        val toolbar: @Composable (Modifier) -> Unit = { toolbarModifier ->
            Toolbar(state, side, onEvent, requestClose, toolbarModifier)
        }
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
                DrawingCanvas(state, pixels, onEvent, Modifier.size(side))
                toolbar(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
                DrawingCanvas(state, pixels, onEvent, Modifier.size(side))
                toolbar(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    if (state.confirmClear) ConfirmClearDialog(onEvent)
    if (state.confirmDiscard) {
        ConfirmDiscardDialog(
            onKeepDrawing = { onEvent(DrawEvent.DiscardDismissed) },
            onDiscard = {
                onEvent(DrawEvent.DiscardDismissed)
                onClose()
            },
        )
    }
}

/** Plátno: zobrazí pixely a převádí tahy prstem na události v souřadnicích plátna (0..511). */
@Composable
private fun DrawingCanvas(state: DrawUiState, pixels: IntArray, onEvent: (DrawEvent) -> Unit, modifier: Modifier) {
    val dimens = TalkativeTheme.dimens
    val bitmap = remember { Bitmap.createBitmap(DrawingLimits.CANVAS_SIZE, DrawingLimits.CANVAS_SIZE, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val currentOnEvent by rememberUpdatedState(onEvent)
    val description = stringResource(R.string.draw_canvas)

    Canvas(
        modifier = modifier
            .border(BorderStroke(dimens.borderWidth, MaterialTheme.colorScheme.outline))
            .semantics { contentDescription = description }
            .testTag(DRAW_CANVAS_TAG)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnEvent(DrawEvent.StrokeStarted(toCanvasPoint(down.position, size)))
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed && change.positionChanged()) {
                            // Rychlý tah má mezi událostmi ještě mezipolohy; bez nich by se čára lámala.
                            change.historical.forEach { currentOnEvent(DrawEvent.StrokeMoved(toCanvasPoint(it.position, size))) }
                            currentOnEvent(DrawEvent.StrokeMoved(toCanvasPoint(change.position, size)))
                        }
                        change.consume()
                    } while (change.pressed)
                    currentOnEvent(DrawEvent.StrokeEnded)
                }
            },
    ) {
        state.revision // čtení vynutí překreslení po každé změně plátna
        bitmap.setPixels(pixels, 0, DrawingLimits.CANVAS_SIZE, 0, 0, DrawingLimits.CANVAS_SIZE, DrawingLimits.CANVAS_SIZE)
        drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}

private fun toCanvasPoint(position: Offset, size: IntSize): DrawPoint {
    val scale = DrawingLimits.CANVAS_SIZE.toFloat() / size.width
    val max = DrawingLimits.CANVAS_SIZE - 1f
    return DrawPoint((position.x * scale).coerceIn(0f, max), (position.y * scale).coerceIn(0f, max))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(state: DrawUiState, canvasSide: Dp, onEvent: (DrawEvent) -> Unit, onCloseRequested: () -> Unit, modifier: Modifier) {
    val dimens = TalkativeTheme.dimens
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSmall), verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
            ToolButton(DrawingTool.BRUSH, R.string.draw_tool_brush, state, onEvent)
            ToolButton(DrawingTool.ERASER, R.string.draw_tool_eraser, state, onEvent)
            ToolButton(DrawingTool.FILL, R.string.draw_tool_fill, state, onEvent)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSmall), verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
            DrawingColors.PALETTE.forEachIndexed { index, color ->
                Swatch(color, selected = color == state.color, name = stringResource(colorNameRes(index))) { onEvent(DrawEvent.SelectColor(color)) }
            }
        }

        WidthControl(state, canvasSide, onEvent)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSmall), verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
            OutlinedButton(
                onClick = { onEvent(DrawEvent.Undo) },
                enabled = state.canUndo && !state.isBusy,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) { Text(stringResource(R.string.draw_undo)) }
            OutlinedButton(
                onClick = { onEvent(DrawEvent.ClearAllRequested) },
                enabled = state.isModified && !state.isBusy,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) { Text(stringResource(R.string.draw_clear)) }
            Button(
                onClick = { onEvent(DrawEvent.Save) },
                enabled = state.isModified && !state.isBusy,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) { Text(stringResource(R.string.draw_save)) }
            OutlinedButton(onClick = onCloseRequested, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.draw_close))
            }
        }

        if (state.isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(DRAW_BUSY_TAG)) {
                CircularProgressIndicator(modifier = Modifier.size(dimens.iconSize))
                Text(stringResource(R.string.draw_busy), modifier = Modifier.padding(start = dimens.spaceMedium))
            }
        }
        if (state.saveFailed) {
            Text(
                text = stringResource(R.string.draw_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(DRAW_SAVE_FAILED_TAG),
            )
        }
    }
}

/** Nástroj; vybraný je vyplněné tlačítko a v sémantice je označený jako vybraný. */
@Composable
private fun ToolButton(tool: DrawingTool, labelRes: Int, state: DrawUiState, onEvent: (DrawEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val isSelected = state.tool == tool
    val modifier = Modifier
        .heightIn(min = dimens.minTouchTarget)
        .semantics { selected = isSelected }
        .testTag(drawToolTag(tool))
    if (isSelected) {
        Button(onClick = { onEvent(DrawEvent.SelectTool(tool)) }, modifier = modifier) { Text(stringResource(labelRes)) }
    } else {
        OutlinedButton(onClick = { onEvent(DrawEvent.SelectTool(tool)) }, modifier = modifier) { Text(stringResource(labelRes)) }
    }
}

/** Čtvereček s barvou; vybraná barva má tlustý rámeček a značku zaškrtnutí. */
@Composable
private fun Swatch(color: Int, selected: Boolean, name: String, onClick: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    val description = stringResource(R.string.draw_color, name)
    Box(
        contentAlignment = Alignment.Center,
        // Klepnutelná je celá plocha 64 dp (klik a značka jsou před odsazením), barevné kolečko je uvnitř rámečku.
        modifier = Modifier
            .size(dimens.minTouchTarget)
            .clip(CircleShape)
            .semantics { this.selected = selected; contentDescription = description }
            .testTag(drawColorTag(color))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .border(
                BorderStroke(if (selected) dimens.swatchSelectedBorder else dimens.borderWidth, MaterialTheme.colorScheme.onSurface),
                CircleShape,
            )
            .padding(dimens.swatchInset)
            .clip(CircleShape)
            .background(Color(color)),
    ) {
        if (selected) {
            val checkColor = if (color == DrawingColors.PALETTE[3]) Color.Black else Color.White // na žluté je bílá značka nečitelná
            Icon(Icons.Filled.Check, contentDescription = null, tint = checkColor)
        }
    }
}

/** Posuvník šířky štětce s živým náhledem (kolečko ve velikosti, jakou má tah na zobrazeném plátně); u výplně neaktivní. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidthControl(state: DrawUiState, canvasSide: Dp, onEvent: (DrawEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val enabled = state.tool != DrawingTool.FILL
    val previewSize = canvasSide * state.brushWidth / DrawingLimits.CANVAS_SIZE
    val sliderInteraction = remember { MutableInteractionSource() }
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
        Text(stringResource(R.string.draw_width), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
            Slider(
                value = state.brushWidth.toFloat(),
                onValueChange = { onEvent(DrawEvent.BrushWidthChanged(it.toInt())) },
                valueRange = DrawingLimits.MIN_BRUSH_WIDTH.toFloat()..DrawingLimits.MAX_BRUSH_WIDTH.toFloat(),
                enabled = enabled,
                // Výška posuvníku se řídí velikostí jezdce, proto má jezdec výšku dotykové plochy.
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = sliderInteraction,
                        enabled = enabled,
                        thumbSize = DpSize(dimens.sliderThumbWidth, dimens.minTouchTarget),
                    )
                },
                interactionSource = sliderInteraction,
                modifier = Modifier.weight(1f).testTag(DRAW_WIDTH_SLIDER_TAG),
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.minTouchTarget)) {
                val previewColor = if (state.tool == DrawingTool.ERASER) MaterialTheme.colorScheme.outline else Color(state.color)
                Box(
                    modifier = Modifier
                        .size(previewSize.coerceAtLeast(dimens.borderWidth).coerceAtMost(dimens.minTouchTarget))
                        .clip(CircleShape)
                        .background(if (enabled) previewColor else Color.Transparent)
                        .border(BorderStroke(dimens.borderWidth, MaterialTheme.colorScheme.outline), CircleShape)
                        .testTag(DRAW_BRUSH_PREVIEW_TAG),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDiscardDialog(onKeepDrawing: () -> Unit, onDiscard: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    AlertDialog(
        onDismissRequest = onKeepDrawing,
        title = { Text(stringResource(R.string.draw_discard_title)) },
        text = { Text(stringResource(R.string.draw_discard_message)) },
        confirmButton = {
            Button(onClick = onDiscard, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.draw_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepDrawing, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.draw_discard_keep))
            }
        },
    )
}

@Composable
private fun ConfirmClearDialog(onEvent: (DrawEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    AlertDialog(
        onDismissRequest = { onEvent(DrawEvent.ClearAllDismissed) },
        title = { Text(stringResource(R.string.draw_clear_title)) },
        text = { Text(stringResource(R.string.draw_clear_message)) },
        confirmButton = {
            Button(onClick = { onEvent(DrawEvent.ClearAllConfirmed) }, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.draw_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(DrawEvent.ClearAllDismissed) }, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

private fun colorNameRes(index: Int): Int = when (index) {
    0 -> R.string.draw_color_black
    1 -> R.string.draw_color_red
    2 -> R.string.draw_color_orange
    3 -> R.string.draw_color_yellow
    4 -> R.string.draw_color_green
    5 -> R.string.draw_color_blue
    6 -> R.string.draw_color_purple
    else -> R.string.draw_color_brown
}
