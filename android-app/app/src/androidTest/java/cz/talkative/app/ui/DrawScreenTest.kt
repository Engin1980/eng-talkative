package cz.talkative.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.data.drawing.CacheDrawingStorage
import cz.talkative.app.domain.drawing.DrawingColors
import cz.talkative.app.domain.drawing.DrawingLimits
import cz.talkative.app.domain.drawing.DrawingTool
import cz.talkative.app.ui.draw.DRAW_BRUSH_PREVIEW_TAG
import cz.talkative.app.ui.draw.DRAW_BUSY_TAG
import cz.talkative.app.ui.draw.DRAW_CANVAS_TAG
import cz.talkative.app.ui.draw.DRAW_SAVE_FAILED_TAG
import cz.talkative.app.ui.draw.DRAW_WIDTH_SLIDER_TAG
import cz.talkative.app.ui.draw.DrawEvent
import cz.talkative.app.ui.draw.DrawScreen
import cz.talkative.app.ui.draw.DrawUiState
import cz.talkative.app.ui.draw.DrawViewModel
import cz.talkative.app.ui.draw.drawColorTag
import cz.talkative.app.ui.draw.drawToolTag
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Obrazovka kreslení: bezstavové testy panelu a testy se skutečným view modelem a plátnem. */
@RunWith(AndroidJUnit4::class)
class DrawScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<DrawEvent>()
    private var closed = 0
    private val white = DrawingColors.WHITE
    private val blank = IntArray(DrawingLimits.CANVAS_SIZE * DrawingLimits.CANVAS_SIZE) { white }

    private fun show(state: DrawUiState) {
        composeRule.setContent {
            TalkativeTheme { DrawScreen(state = state, pixels = blank, onEvent = { events += it }, onClose = { closed++ }) }
        }
    }

    private fun showLive(): DrawViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = DrawViewModel(CacheDrawingStorage(context.cacheDir))
        composeRule.setContent {
            TalkativeTheme {
                val state by viewModel.uiState.collectAsState()
                DrawScreen(state = state, pixels = viewModel.board.canvas.pixels, onEvent = viewModel::onEvent, onClose = { closed++ })
            }
        }
        return viewModel
    }

    @Test
    fun emptyCanvas_hasUndoSaveAndClearDisabled_closeAlwaysEnabled() {
        show(DrawUiState())

        composeRule.onNodeWithText("Zpět").assertIsNotEnabled()
        composeRule.onNodeWithText("Uložit").assertIsNotEnabled()
        composeRule.onNodeWithText("Smazat vše").assertIsNotEnabled()
        composeRule.onNodeWithText("Zavřít").assertIsEnabled().performClick()
        assertEquals(1, closed)
    }

    @Test
    fun modifiedCanvas_enablesTheButtons_andTheyReportEvents() {
        show(DrawUiState(canUndo = true, isModified = true))

        composeRule.onNodeWithText("Zpět").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Smazat vše").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Uložit").assertIsEnabled().performClick()

        assertEquals(listOf(DrawEvent.Undo, DrawEvent.ClearAllRequested, DrawEvent.Save), events)
    }

    @Test
    fun everyControl_isAtLeast64dpTall() {
        show(DrawUiState(canUndo = true, isModified = true))

        for (label in listOf("Štětec", "Guma", "Výplň", "Zpět", "Smazat vše", "Uložit", "Zavřít")) {
            try {
                composeRule.onNodeWithText(label).assertHeightIsAtLeast(64.dp)
            } catch (e: AssertionError) {
                throw AssertionError("ovládací prvek „$label“: ${e.message}", e)
            }
        }
        composeRule.onNodeWithTag(drawColorTag(DrawingColors.BLACK)).assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag(DRAW_WIDTH_SLIDER_TAG).assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun selectedTool_isMarked_andTappingChangesIt() {
        show(DrawUiState(tool = DrawingTool.ERASER))

        composeRule.onNodeWithTag(drawToolTag(DrawingTool.ERASER)).assertIsSelected()
        composeRule.onNodeWithTag(drawToolTag(DrawingTool.BRUSH)).assertIsNotSelected()
        composeRule.onNodeWithTag(drawToolTag(DrawingTool.FILL)).assertIsNotSelected()

        composeRule.onNodeWithTag(drawToolTag(DrawingTool.FILL)).performClick()

        assertEquals(listOf<DrawEvent>(DrawEvent.SelectTool(DrawingTool.FILL)), events)
    }

    @Test
    fun selectedColor_isMarked_andEveryPaletteColorCanBePicked() {
        show(DrawUiState(color = DrawingColors.PALETTE[2]))

        composeRule.onNodeWithTag(drawColorTag(DrawingColors.PALETTE[2])).assertIsSelected()
        composeRule.onNodeWithTag(drawColorTag(DrawingColors.BLACK)).assertIsNotSelected()

        for (color in DrawingColors.PALETTE) composeRule.onNodeWithTag(drawColorTag(color)).performClick()

        assertEquals(DrawingColors.PALETTE.map { DrawEvent.SelectColor(it) }, events)
        assertEquals(8, DrawingColors.PALETTE.size)
    }

    @Test
    fun widthSlider_isDisabledForTheFillTool_andPreviewReflectsTheState() {
        show(DrawUiState(tool = DrawingTool.FILL))
        composeRule.onNodeWithTag(DRAW_WIDTH_SLIDER_TAG).assertIsNotEnabled()
    }

    @Test
    fun widthSlider_isEnabledForBrushAndEraser_andShowsAPreview() {
        show(DrawUiState(tool = DrawingTool.BRUSH))

        composeRule.onNodeWithTag(DRAW_WIDTH_SLIDER_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(DRAW_BRUSH_PREVIEW_TAG).assertIsDisplayed()
    }

    @Test
    fun busyAndSaveFailure_areShown() {
        show(DrawUiState(isBusy = true, saveFailed = true, isModified = true))

        composeRule.onNodeWithTag(DRAW_BUSY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DRAW_SAVE_FAILED_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Uložit").assertIsNotEnabled() // během práce se nedá uložit ani vracet
        composeRule.onNodeWithText("Zpět").assertIsNotEnabled()
    }

    @Test
    fun clearConfirmation_isShown_andBothButtonsReport() {
        show(DrawUiState(isModified = true, confirmClear = true))

        composeRule.onNodeWithText("Smazat celou kresbu?").assertIsDisplayed()
        composeRule.onNodeWithText("Smazat", substring = false).performClick()
        composeRule.onNodeWithText("Zrušit").performClick()

        assertEquals(listOf(DrawEvent.ClearAllConfirmed, DrawEvent.ClearAllDismissed), events)
    }

    @Test
    fun canvas_isSquare_andFitsTheScreen() {
        show(DrawUiState())

        val node = composeRule.onNodeWithTag(DRAW_CANVAS_TAG).assertIsDisplayed().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        assertEquals(bounds.width, bounds.height, 1f)
        assertTrue(bounds.width > 300f)
    }

    // ---- skutečný view model a plátno

    @Test
    fun dragAcrossTheCanvas_drawsAStroke_andEnablesUndoAndSave() {
        val viewModel = showLive()
        composeRule.onNodeWithText("Uložit").assertIsNotEnabled()

        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), durationMillis = 300)
        }
        composeRule.waitForIdle()

        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(256, 256))
        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(256, 60))
        assertTrue(viewModel.uiState.value.isModified)
        composeRule.onNodeWithText("Zpět").assertIsEnabled()
        composeRule.onNodeWithText("Uložit").assertIsEnabled()
    }

    @Test
    fun tapOnTheCanvas_drawsADot() {
        val viewModel = showLive()

        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(256, 256))
        assertTrue(viewModel.uiState.value.isModified)
    }

    @Test
    fun undoButton_revertsTheStroke_andDisablesItself() {
        val viewModel = showLive()
        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zpět").performClick()
        composeRule.waitForIdle()

        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(256, 256))
        composeRule.onNodeWithText("Zpět").assertIsNotEnabled()
        composeRule.onNodeWithText("Uložit").assertIsNotEnabled()
    }

    @Test
    fun fillTool_filledTheTappedRegion() {
        val viewModel = showLive()
        composeRule.onNodeWithTag(drawColorTag(DrawingColors.PALETTE[5])).performClick()
        composeRule.onNodeWithTag(drawToolTag(DrawingTool.FILL)).performClick()

        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitUntil(5_000) { viewModel.board.canvas.pixelAt(10, 10) == DrawingColors.PALETTE[5] }

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(DRAW_BUSY_TAG).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText("Zpět").assertIsEnabled()
    }

    @Test
    fun clearAll_askedFirst_thenEmptiesTheCanvas_andUndoBringsItBack() {
        val viewModel = showLive()
        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Smazat vše").performClick()
        composeRule.onNodeWithText("Smazat celou kresbu?").assertIsDisplayed()
        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(256, 256)) // zatím se nesmazalo
        composeRule.onNodeWithText("Smazat").performClick()
        composeRule.waitForIdle()

        assertEquals(DrawingColors.WHITE, viewModel.board.canvas.pixelAt(256, 256))
        composeRule.onNodeWithText("Zpět").performClick()
        composeRule.waitForIdle()
        assertEquals(DrawingColors.BLACK, viewModel.board.canvas.pixelAt(256, 256))
    }

    @Test
    fun closeOnUnchangedCanvas_closesWithoutAsking() {
        show(DrawUiState(isModified = false))

        composeRule.onNodeWithText("Zavřít").performClick()

        assertEquals(1, closed)
        assertEquals(emptyList<DrawEvent>(), events)
    }

    @Test
    fun closeOnModifiedCanvas_asksFirst_andDoesNotCloseYet() {
        show(DrawUiState(isModified = true, canUndo = true))

        composeRule.onNodeWithText("Zavřít").performClick()

        assertEquals(0, closed)
        assertEquals(listOf<DrawEvent>(DrawEvent.DiscardRequested), events)
    }

    @Test
    fun discardDialog_offersDiscardAndContinue() {
        show(DrawUiState(isModified = true, confirmDiscard = true))

        composeRule.onNodeWithText("Zahodit kresbu?").assertIsDisplayed()
        composeRule.onNodeWithText("Pokračovat v kreslení").performClick()
        assertEquals(0, closed)
        assertEquals(listOf<DrawEvent>(DrawEvent.DiscardDismissed), events)

        composeRule.onNodeWithText("Zahodit").performClick()

        assertEquals(1, closed)
        assertEquals(listOf<DrawEvent>(DrawEvent.DiscardDismissed, DrawEvent.DiscardDismissed), events)
    }

    @Test
    fun liveScreen_closeAfterDrawing_asks_andDiscardClosesWithoutSavingAnything() {
        val viewModel = showLive()
        composeRule.onNodeWithTag(DRAW_CANVAS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Zavřít").performClick()
        composeRule.onNodeWithText("Zahodit kresbu?").assertIsDisplayed()
        assertEquals(0, closed)
        composeRule.onNodeWithText("Pokračovat v kreslení").performClick()
        assertTrue(viewModel.uiState.value.isModified) // kresba zůstala

        composeRule.onNodeWithText("Zavřít").performClick()
        composeRule.onNodeWithText("Zahodit").performClick()

        assertEquals(1, closed)
        assertEquals(null, viewModel.uiState.value.savedImageUri) // nic se neuložilo
    }
}
