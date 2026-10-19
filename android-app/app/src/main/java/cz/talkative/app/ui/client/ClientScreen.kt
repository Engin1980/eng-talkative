package cz.talkative.app.ui.client

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import cz.talkative.app.domain.model.AddWordMode
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.domain.model.Word
import cz.talkative.app.domain.paging.Pagination
import cz.talkative.app.ui.common.WordImage
import cz.talkative.app.ui.theme.TalkativeTheme

const val DRAGGED_WORD_TAG = "dragged-word"

/** Napojení obrazovky na view model. */
@Composable
fun ClientRoute(onOpenAdmin: () -> Unit, viewModel: ClientViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClientScreen(state = state, onEvent = viewModel::onEvent, onOpenAdmin = onOpenAdmin)
}

/**
 * Bezstavová obrazovka klienta: nahoře řádek věty (zatím jen rezerva) se skrytým vstupem do administrace,
 * pod ním stránkovaná mřížka složek a slovíček. Velikost mřížky se volí podle orientace displeje.
 */
@Composable
fun ClientScreen(
    state: ClientUiState,
    onEvent: (ClientEvent) -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
) {
    val dimens = TalkativeTheme.dimens
    val drag = rememberSentenceDragState(state.config.addWordMode, onEvent)
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInWindow() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(dimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
        ) {
            // Skrytý prvek má vlastní buňku vedle řádku věty, aby nepřekrýval žádné ovládání.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SentenceBar(
                    sentence = state.sentence,
                    removeHoldMillis = state.config.removeHoldMillis,
                    hideCaptions = state.config.hideCaptionsInSentence,
                    onRemove = { onEvent(ClientEvent.RemoveFromSentence(it)) },
                    drag = drag,
                    dragEnabled = state.config.addWordMode == AddWordMode.DRAG,
                    emptyHintRes = if (state.config.addWordMode == AddWordMode.DRAG) R.string.client_sentence_hint_drag else R.string.client_sentence_hint,
                    modifier = Modifier.weight(1f),
                )
                PlayStopButton(
                    isSpeaking = state.isSpeaking,
                    enabled = state.sentence.isNotEmpty() || state.isSpeaking,
                    onClick = { onEvent(ClientEvent.TogglePlayback) },
                    modifier = Modifier.padding(start = dimens.spaceMedium),
                )
                ClearSentenceButton(
                    enabled = state.sentence.isNotEmpty(),
                    onClick = { onEvent(ClientEvent.ClearSentence) },
                    modifier = Modifier.padding(start = dimens.spaceMedium),
                )
                HiddenAdminEntry(onEntered = onOpenAdmin, holdMillis = state.config.adminHoldMillis)
            }

            NavigationRow(path = state.path, onEvent = onEvent)
            state.speechError?.let { SpeechErrorDialog(it) { onEvent(ClientEvent.DismissSpeechError) } }

            val grid = if (isLandscape) state.config.gridLandscape else state.config.gridPortrait
            when {
                state.isLoading -> Box(modifier = Modifier.weight(1f))
                state.items.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.client_empty_folder),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val page = Pagination.clampPage(state.pageIndex, state.items.size, grid.pageSize)
                    ItemGrid(
                        items = Pagination.pageItems(state.items, page, grid.pageSize),
                        grid = grid,
                        onFolderClick = { onEvent(ClientEvent.OpenFolder(it.id)) },
                        onWordClick = { onEvent(ClientEvent.WordSelected(it)) },
                        drag = drag,
                        dragEnabled = state.config.addWordMode == AddWordMode.DRAG,
                        modifier = Modifier.weight(1f),
                    )
                    val pageCount = Pagination.pageCount(state.items.size, grid.pageSize)
                    if (pageCount > 1) {
                        PageControls(
                            page = page,
                            pageCount = pageCount,
                            onPageChange = { onEvent(ClientEvent.PageChanged(it)) },
                        )
                    }
                }
            }
        }
        drag.dragged?.let { DraggedWord(title = it.title, pointer = drag.pointer - rootOrigin) }
    }
}

/** Dlaždice, která se při tažení vznáší pod prstem. */
@Composable
private fun DraggedWord(title: String, pointer: Offset) {
    val dimens = TalkativeTheme.dimens
    val shift = with(LocalDensity.current) { (dimens.minTouchTarget / 2).roundToPx() }
    Surface(
        modifier = Modifier
            .offset { IntOffset(pointer.x.roundToInt() - shift, pointer.y.roundToInt() - shift) }
            .testTag(DRAGGED_WORD_TAG),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = dimens.borderWidth * 4,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimens.spaceMedium, vertical = dimens.spaceSmall),
        )
    }
}

/** Velká tlačítka „Zpět“ a „Domů“ a drobečková navigace (kořen › složka › podsložka). */
@Composable
private fun NavigationRow(path: List<Folder>, onEvent: (ClientEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val atRoot = path.isEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onEvent(ClientEvent.NavigateUp) },
            enabled = !atRoot,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(dimens.iconSize))
            Text(text = stringResource(R.string.client_back), modifier = Modifier.padding(start = dimens.spaceSmall))
        }
        Button(
            onClick = { onEvent(ClientEvent.GoHome) },
            enabled = !atRoot,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget),
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(dimens.iconSize))
            Text(text = stringResource(R.string.client_home), modifier = Modifier.padding(start = dimens.spaceSmall))
        }
        Breadcrumbs(path = path, onGoToFolder = { onEvent(ClientEvent.GoToFolder(it)) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Breadcrumbs(path: List<Folder>, onGoToFolder: (String?) -> Unit, modifier: Modifier = Modifier) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(
            text = stringResource(R.string.client_breadcrumb_root),
            isCurrent = path.isEmpty(),
            onClick = { onGoToFolder(null) },
        )
        path.forEachIndexed { index, folder ->
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(dimens.iconSize))
            Crumb(text = folder.title, isCurrent = index == path.lastIndex, onClick = { onGoToFolder(folder.id) })
        }
    }
}

@Composable
private fun Crumb(text: String, isCurrent: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = TalkativeTheme.dimens.minTouchTarget)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Mřížka [grid] vyplní celou dostupnou plochu; chybějící položky na poslední stránce nechají buňky prázdné. */
@Composable
private fun ItemGrid(
    items: List<DictionaryItem>,
    grid: GridSize,
    onFolderClick: (Folder) -> Unit,
    onWordClick: (Word) -> Unit,
    drag: SentenceDragState,
    dragEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
        repeat(grid.rows) { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            ) {
                repeat(grid.columns) { column ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        items.getOrNull(row * grid.columns + column)?.let { item ->
                            ItemTile(item = item, onFolderClick = onFolderClick, onWordClick = onWordClick, drag = drag, dragEnabled = dragEnabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemTile(item: DictionaryItem, onFolderClick: (Folder) -> Unit, onWordClick: (Word) -> Unit, drag: SentenceDragState, dragEnabled: Boolean) {
    val dimens = TalkativeTheme.dimens
    val isFolder = item is Folder
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = {
            when (item) {
                is Folder -> onFolderClick(item)
                is Word -> onWordClick(item)
            }
        },
        modifier = Modifier.fillMaxSize().let {
            // Táhnout lze jen slovíčka a jen v režimu tažení; složky se otevírají klepnutím.
            if (item is Word) it.dragSource(dragEnabled, DragSource.GridWord(item), drag) else it
        },
        shape = MaterialTheme.shapes.large,
        color = if (isFolder) colors.primary else colors.surfaceVariant,
        contentColor = if (isFolder) colors.onPrimary else colors.onSurfaceVariant,
        border = BorderStroke(dimens.borderWidth, colors.outline),
    ) {
        Box(modifier = Modifier.padding(dimens.spaceMedium), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall, Alignment.CenterVertically),
            ) {
                // Slovíčko s obrázkem: obrázek nahoře a titulek pod ním; bez obrázku (nebo se zmizelým souborem) jen text.
                if (item is Word) {
                    WordImage(item.imageFile, Modifier.weight(1f, fill = false).fillMaxWidth())
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isFolder) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.client_folder_marker),
                    modifier = Modifier.align(Alignment.BottomEnd).size(dimens.iconSize),
                )
            }
        }
    }
}

@Composable
private fun PageControls(page: Int, pageCount: Int, onPageChange: (Int) -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onPageChange(page - 1) },
            enabled = page > 0,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget).weight(1f),
        ) {
            Text(stringResource(R.string.client_page_previous))
        }
        Text(
            text = stringResource(R.string.client_page_indicator, page + 1, pageCount),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onPageChange(page + 1) },
            enabled = page < pageCount - 1,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget).weight(1f),
        ) {
            Text(stringResource(R.string.client_page_next))
        }
    }
}
