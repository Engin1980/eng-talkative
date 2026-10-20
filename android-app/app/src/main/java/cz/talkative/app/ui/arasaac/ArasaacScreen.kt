package cz.talkative.app.ui.arasaac

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cz.talkative.app.R
import cz.talkative.app.domain.pictogram.Pictogram
import cz.talkative.app.domain.pictogram.PictogramError
import cz.talkative.app.ui.theme.TalkativeTheme

/** Značky prvků obrazovky, podle kterých testy hledají. */
const val ARASAAC_SEARCH_FIELD_TAG = "arasaac-search-field"
const val ARASAAC_STATUS_TAG = "arasaac-status"
const val ARASAAC_DOWNLOAD_TAG = "arasaac-download"

fun pictogramTag(id: Int) = "pictogram-$id"

/**
 * Napojení obrazovky na view model. Stažený piktogram ([onPicked] dostane URI souboru v cache) se předá jen jednou
 * a view model si pak vynuluje.
 */
@Composable
fun ArasaacRoute(
    onPicked: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    viewModel: ArasaacViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.pickedImageUri) {
        state.pickedImageUri?.let {
            onPicked(it)
            viewModel.onEvent(ArasaacEvent.PickedConsumed)
        }
    }
    ArasaacScreen(state = state, onEvent = viewModel::onEvent, onOpenAbout = onOpenAbout, onBack = onBack)
}

/** Bezstavová obrazovka hledání a výběru piktogramu ARASAAC. */
@Composable
fun ArasaacScreen(
    state: ArasaacUiState,
    onEvent: (ArasaacEvent) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    BackHandler { onBack() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.arasaac_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBack, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.arasaac_back))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(ArasaacEvent.QueryChanged(it)) },
                label = { Text(stringResource(R.string.arasaac_query_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onEvent(ArasaacEvent.Search) }),
                modifier = Modifier.weight(1f).heightIn(min = dimens.minTouchTarget).testTag(ARASAAC_SEARCH_FIELD_TAG),
            )
            Button(
                onClick = { onEvent(ArasaacEvent.Search) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) { Text(stringResource(R.string.arasaac_search)) }
        }

        state.downloadingId?.let {
            Column(modifier = Modifier.fillMaxWidth().testTag(ARASAAC_DOWNLOAD_TAG), verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
                Text(stringResource(R.string.arasaac_downloading), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        state.downloadError?.let {
            Text(
                text = stringResource(errorText(it)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(ARASAAC_DOWNLOAD_TAG),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SearchContent(state, onEvent)
        }

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
            Text(stringResource(R.string.arasaac_credit), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenAbout, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.arasaac_about_link))
            }
        }
    }
}

@Composable
private fun SearchContent(state: ArasaacUiState, onEvent: (ArasaacEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    when (val search = state.search) {
        SearchStatus.NotSearched -> Message(R.string.arasaac_hint_start)
        SearchStatus.Searching -> Column(
            modifier = Modifier.fillMaxWidth().testTag(ARASAAC_STATUS_TAG),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            Text(stringResource(R.string.arasaac_searching), style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        SearchStatus.Nothing -> Message(R.string.arasaac_nothing)
        is SearchStatus.Failed -> Column(
            modifier = Modifier.fillMaxWidth().testTag(ARASAAC_STATUS_TAG),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
        ) {
            Text(stringResource(errorText(search.error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { onEvent(ArasaacEvent.Retry) }, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.arasaac_retry))
            }
        }
        is SearchStatus.Results -> LazyVerticalGrid(
            columns = GridCells.Adaptive(dimens.pictogramTileMinSize),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(search.pictograms, key = { it.id }) { pictogram ->
                PictogramTile(pictogram, enabled = state.downloadingId == null) { onEvent(ArasaacEvent.Select(pictogram)) }
            }
        }
    }
}

@Composable
private fun Message(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().testTag(ARASAAC_STATUS_TAG),
    )
}

/** Dlaždice s náhledem a titulkem; celá je klepnutelná. Piktogramy mají průhledné pozadí, proto bílý podklad. */
@Composable
private fun PictogramTile(pictogram: Pictogram, enabled: Boolean, onClick: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(dimens.borderWidth, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .testTag(pictogramTag(pictogram.id))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(dimens.spaceSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            AsyncImage(
                model = pictogram.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Text(pictogram.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

private fun errorText(error: PictogramError): Int = when (error) {
    PictogramError.NO_NETWORK -> R.string.arasaac_error_no_network
    PictogramError.TIMEOUT -> R.string.arasaac_error_timeout
    PictogramError.SERVER_ERROR -> R.string.arasaac_error_server
    PictogramError.BAD_RESPONSE -> R.string.arasaac_error_bad_response
}
