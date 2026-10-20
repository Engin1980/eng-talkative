package cz.talkative.app.ui.draw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.drawing.DrawingBoard
import cz.talkative.app.domain.drawing.DrawingStorage
import cz.talkative.app.domain.drawing.DrawingTool
import cz.talkative.app.domain.drawing.PngEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * Drží kresbu při otočení tabletu a po dobu, kdy je obrazovka na zásobníku navigace. Rozkreslená kresba se po zavření
 * obrazovky zahodí spolu s view modelem (nic se neukládá).
 */
@HiltViewModel
class DrawViewModel @Inject constructor(
    private val storage: DrawingStorage,
) : ViewModel() {

    val board = DrawingBoard()

    private val state = MutableStateFlow(DrawUiState())
    val uiState: StateFlow<DrawUiState> = state.asStateFlow()

    fun onEvent(event: DrawEvent) {
        when (event) {
            is DrawEvent.SelectTool -> state.update { it.copy(tool = event.tool) }
            is DrawEvent.SelectColor -> state.update { it.copy(color = event.color) }
            is DrawEvent.BrushWidthChanged -> state.update { it.copy(brushWidth = event.width) }
            is DrawEvent.StrokeStarted -> startStroke(event)
            is DrawEvent.StrokeMoved -> {
                if (state.value.tool != DrawingTool.FILL && !state.value.isBusy) {
                    board.extendStroke(event.point)
                    state.update { it.copy(revision = it.revision + 1) }
                }
            }
            DrawEvent.StrokeEnded -> {
                board.endStroke()
                refresh()
            }
            DrawEvent.Undo -> if (!state.value.isBusy && board.undo()) refresh()
            DrawEvent.ClearAllRequested -> if (state.value.isModified && !state.value.isBusy) state.update { it.copy(confirmClear = true) }
            DrawEvent.ClearAllConfirmed -> {
                state.update { it.copy(confirmClear = false) }
                if (!state.value.isBusy) {
                    board.clearAll()
                    refresh()
                }
            }
            DrawEvent.ClearAllDismissed -> state.update { it.copy(confirmClear = false) }
            DrawEvent.Save -> save()
            DrawEvent.DiscardRequested -> state.update { it.copy(confirmDiscard = true) }
            DrawEvent.DiscardDismissed -> state.update { it.copy(confirmDiscard = false) }
            DrawEvent.SavedConsumed -> state.update { it.copy(savedImageUri = null) }
        }
    }

    private fun startStroke(event: DrawEvent.StrokeStarted) {
        val current = state.value
        if (current.isBusy) return
        if (current.tool == DrawingTool.FILL) {
            state.update { it.copy(isBusy = true) }
            viewModelScope.launch {
                board.fill(event.point.x.toInt(), event.point.y.toInt(), current.color)
                state.update { it.copy(isBusy = false) }
                refresh()
            }
        } else {
            board.beginStroke(event.point, current.tool, current.color, current.brushWidth)
            refresh()
        }
    }

    /** Prázdné (celé bílé) plátno se uložit nedá. */
    private fun save() {
        if (state.value.isBusy || !board.isModified) return
        state.update { it.copy(isBusy = true, saveFailed = false) }
        viewModelScope.launch {
            val png = PngEncoder.encode(board.canvas.pixels, board.canvas.size, board.canvas.size)
            try {
                val file = storage.save(png)
                state.update { it.copy(isBusy = false, savedImageUri = file.toURI().toString()) }
            } catch (e: IOException) {
                state.update { it.copy(isBusy = false, saveFailed = true) }
            }
        }
    }

    /** Přepočítá, co UI zobrazuje o plátně, a vyvolá překreslení. */
    private fun refresh() {
        state.update { it.copy(canUndo = board.canUndo, isModified = board.isModified, revision = it.revision + 1) }
    }
}
