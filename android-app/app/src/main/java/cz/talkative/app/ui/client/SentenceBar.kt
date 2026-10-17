package cz.talkative.app.ui.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cz.talkative.app.R
import cz.talkative.app.domain.model.SentenceEntry
import cz.talkative.app.ui.common.onHold
import cz.talkative.app.ui.theme.TalkativeTheme

const val SENTENCE_BAR_TAG = "sentence-bar"
const val SENTENCE_WORD_TAG = "sentence-word"

/**
 * Řádek věty: slova za sebou se zalamují do více řádků; když se věta nevejde do maximální výšky, posouvá se
 * (a po přidání slova se posune na konec). Slovo se odebere podržením po dobu [removeHoldMillis],
 * krátké klepnutí nic nedělá.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceBar(
    sentence: List<SentenceEntry>,
    removeHoldMillis: Long,
    onRemove: (entryId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    LaunchedEffect(sentence.size) { scrollState.animateScrollTo(scrollState.maxValue) }

    Surface(
        modifier = modifier.testTag(SENTENCE_BAR_TAG),
        shape = MaterialTheme.shapes.large,
        color = colors.background,
        border = BorderStroke(dimens.borderWidth, colors.outline),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = dimens.sentenceBarMinHeight, max = dimens.sentenceBarMaxHeight)
                .verticalScroll(scrollState)
                .padding(dimens.spaceSmall),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (sentence.isEmpty()) {
                Text(
                    text = stringResource(R.string.client_sentence_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = dimens.spaceSmall),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
                    verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
                ) {
                    sentence.forEach { entry ->
                        key(entry.entryId) {
                            SentenceChip(
                                title = entry.word.title,
                                onHold = { onRemove(entry.entryId) },
                                holdMillis = removeHoldMillis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentenceChip(title: String, holdMillis: Long, onHold: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.testTag(SENTENCE_WORD_TAG).onHold(holdMillis, onHold),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(dimens.borderWidth, colors.outline),
    ) {
        Box(
            modifier = Modifier.heightIn(min = dimens.minTouchTarget).padding(horizontal = dimens.spaceMedium),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Tlačítko pro smazání celé věty; bez slov je neaktivní. */
@Composable
fun ClearSentenceButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = TalkativeTheme.dimens
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.minTouchTarget),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(dimens.iconSize))
        Text(text = stringResource(R.string.client_clear_sentence), modifier = Modifier.padding(start = dimens.spaceSmall))
    }
}
