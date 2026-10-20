package cz.talkative.app.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import cz.talkative.app.ui.theme.TalkativeTheme

/**
 * Sloupec, který se při nedostatku výšky posouvá a ukazuje trvale viditelný ukazatel posunu.
 * Je to jediný způsob, jak v aplikaci vyrobit posouvaný obsah dialogu nebo obrazovky: systémové
 * posuvníky na dotykovém displeji mizí, takže by nebylo poznat, že pod okrajem je další obsah.
 * Vejde-li se obsah, nic se neposouvá ani nekreslí.
 */
@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.scrollIndicator(state).verticalScroll(state),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Ukazatel posunu pro [verticalScroll]; musí být před ním v řetězci modifikátorů. Jezdec se kreslí těsně za
 * pravým okrajem obsahu, tedy do okraje (odsazení), aby nepřekrýval ovládací prvky.
 */
fun Modifier.scrollIndicator(state: ScrollState): Modifier = scrollIndicatorDrawing {
    if (state.maxValue > 0) {
        val total = state.maxValue + it
        ScrollThumb(start = state.value / total, size = it / total)
    } else {
        null
    }
}

/** Ukazatel posunu pro [androidx.compose.foundation.lazy.LazyColumn]; poloha se odhaduje z počtu položek. */
fun Modifier.scrollIndicator(state: LazyListState): Modifier = scrollIndicatorDrawing {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || !(state.canScrollBackward || state.canScrollForward)) {
        null
    } else {
        val first = visible.first()
        val partial = if (first.size > 0) -first.offset.toFloat() / first.size else 0f
        itemsThumb(info.totalItemsCount, first.index + partial, visible.size.toFloat())
    }
}

/** Ukazatel posunu pro [androidx.compose.foundation.lazy.grid.LazyVerticalGrid] (po řádcích mřížky). */
fun Modifier.scrollIndicator(state: LazyGridState): Modifier = scrollIndicatorDrawing {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || !(state.canScrollBackward || state.canScrollForward)) {
        null
    } else {
        itemsThumb(info.totalItemsCount, state.firstVisibleItemIndex.toFloat(), visible.size.toFloat())
    }
}

private fun itemsThumb(totalItems: Int, firstVisible: Float, visibleCount: Float): ScrollThumb? {
    if (totalItems <= 0) return null
    val size = (visibleCount / totalItems).coerceIn(0f, 1f)
    val start = (firstVisible / totalItems).coerceIn(0f, 1f - size)
    return ScrollThumb(start = start, size = size)
}

/** Jezdec jako podíl výšky: kde začíná a jak je dlouhý (0..1). */
private class ScrollThumb(val start: Float, val size: Float)

/** Vykreslí jezdec, který [thumb] spočítá z výšky okna ve pixelech; `null` = nic nekreslit. */
private fun Modifier.scrollIndicatorDrawing(thumb: (viewportHeight: Float) -> ScrollThumb?): Modifier = composed {
    val dimens = TalkativeTheme.dimens
    val color: Color = MaterialTheme.colorScheme.outline
    drawWithContent {
        drawContent()
        thumb(size.height)?.let { drawThumb(it, color, dimens.scrollIndicatorWidth.toPx(), dimens.scrollIndicatorMinLength.toPx(), dimens.scrollIndicatorOutset.toPx()) }
    }
}

private fun DrawScope.drawThumb(thumb: ScrollThumb, color: Color, width: Float, minLength: Float, outset: Float) {
    val length = (size.height * thumb.size).coerceAtLeast(minLength).coerceAtMost(size.height)
    val top = (size.height - length) * if (thumb.size >= 1f) 0f else thumb.start / (1f - thumb.size)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width + outset - width, top),
        size = Size(width, length),
        cornerRadius = CornerRadius(width / 2),
    )
}
