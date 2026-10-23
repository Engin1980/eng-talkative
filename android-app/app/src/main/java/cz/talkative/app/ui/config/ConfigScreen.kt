package cz.talkative.app.ui.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.model.AddWordMode
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.domain.model.ConfigLimits
import cz.talkative.app.domain.model.GridSize
import cz.talkative.app.ui.theme.TalkativeTheme
import cz.talkative.app.ui.common.ScrollableColumn
import java.util.Locale

/** Značka hodnoty u nastavení, např. `config-value-remove-hold`; testy podle ní čtou zobrazenou hodnotu. */
fun configValueTag(key: String) = "config-value-$key"

/** Napojení obrazovky na view model. */
@Composable
fun ConfigRoute(onBack: () -> Unit, viewModel: ConfigViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConfigScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/** Bezstavová obrazovka konfigurace zařízení; každá změna se uloží hned a klient ji uvidí po návratu. */
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onEvent: (ConfigEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    val config = state.config
    BackHandler { onBack() }
    val immediately = stringResource(R.string.config_add_hold_immediately)

    // Hlavička i nastavení jsou jeden posouvaný celek, aby na nízkém okně hlavička nezabrala celou výšku.
    ScrollableColumn(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.config_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBack, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.config_back))
            }
        }
        Text(stringResource(R.string.config_local_note), style = MaterialTheme.typography.bodyMedium)

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
            SectionTitle(R.string.config_section_sentence)
            Text(stringResource(R.string.config_add_word_mode), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                label = stringResource(R.string.config_add_mode_tap),
                selected = config.addWordMode == AddWordMode.TAP,
                onSelect = { onEvent(ConfigEvent.AddWordModeChanged(AddWordMode.TAP)) },
            )
            ChoiceRow(
                label = stringResource(R.string.config_add_mode_drag),
                selected = config.addWordMode == AddWordMode.DRAG,
                onSelect = { onEvent(ConfigEvent.AddWordModeChanged(AddWordMode.DRAG)) },
            )
            if (config.addWordMode == AddWordMode.TAP) {
                StepperRow(
                    label = stringResource(R.string.config_add_hold),
                    key = "add-hold",
                    value = config.addHoldMillis,
                    min = ConfigLimits.MIN_ADD_HOLD_MILLIS,
                    max = ConfigLimits.MAX_ADD_HOLD_MILLIS,
                    step = ConfigLimits.ADD_HOLD_STEP_MILLIS,
                    format = { if (it == 0L) immediately else seconds(it) },
                    onChange = { onEvent(ConfigEvent.AddHoldChanged(it)) },
                )
            }
            SwitchRow(R.string.config_allow_reorder, config.allowReorderInSentence) { onEvent(ConfigEvent.AllowReorderChanged(it)) }
            SwitchRow(R.string.config_speak_on_tap, config.speakWordOnTap) { onEvent(ConfigEvent.SpeakWordOnTapChanged(it)) }
            SwitchRow(R.string.config_hide_captions, config.hideCaptionsInSentence) { onEvent(ConfigEvent.HideCaptionsChanged(it)) }
            SwitchRow(R.string.config_auto_clear, config.autoClearAfterPlayback) { onEvent(ConfigEvent.AutoClearChanged(it)) }
            StepperRow(
                label = stringResource(R.string.config_remove_hold),
                key = "remove-hold",
                value = config.removeHoldMillis,
                min = ConfigLimits.MIN_REMOVE_HOLD_MILLIS,
                max = ConfigLimits.MAX_REMOVE_HOLD_MILLIS,
                step = ConfigLimits.REMOVE_HOLD_STEP_MILLIS,
                format = { seconds(it) },
                onChange = { onEvent(ConfigEvent.RemoveHoldChanged(it)) },
            )

            HorizontalDivider()
            SectionTitle(R.string.config_section_appearance)
            StepperRow(
                label = stringResource(R.string.config_text_scale),
                key = "text-scale",
                value = config.textScalePercent.toLong(),
                min = ConfigLimits.MIN_TEXT_SCALE_PERCENT.toLong(),
                max = ConfigLimits.MAX_TEXT_SCALE_PERCENT.toLong(),
                step = ConfigLimits.TEXT_SCALE_STEP_PERCENT.toLong(),
                format = { "$it %" },
                onChange = { onEvent(ConfigEvent.TextScaleChanged(it.toInt())) },
            )

            HorizontalDivider()
            SectionTitle(R.string.config_section_grid)
            GridRows(R.string.config_grid_portrait, "portrait", config.gridPortrait) { onEvent(ConfigEvent.GridChanged(false, it)) }
            GridRows(R.string.config_grid_landscape, "landscape", config.gridLandscape) { onEvent(ConfigEvent.GridChanged(true, it)) }

            HorizontalDivider()
            SectionTitle(R.string.config_section_admin)
            StepperRow(
                label = stringResource(R.string.config_admin_hold),
                key = "admin-hold",
                value = config.adminHoldMillis,
                min = ConfigLimits.MIN_ADMIN_HOLD_MILLIS,
                max = ConfigLimits.MAX_ADMIN_HOLD_MILLIS,
                step = ConfigLimits.ADMIN_HOLD_STEP_MILLIS,
                format = { seconds(it) },
                onChange = { onEvent(ConfigEvent.AdminHoldChanged(it)) },
            )
            Text(stringResource(R.string.config_collation), style = MaterialTheme.typography.titleMedium)
            val languages = (ConfigLimits.COLLATION_LANGUAGES + config.collationLanguageTag).distinct()
            languages.forEach { tag ->
                ChoiceRow(
                    label = languageName(tag),
                    selected = config.collationLanguageTag == tag,
                    onSelect = { onEvent(ConfigEvent.CollationChanged(tag)) },
                )
            }

            HorizontalDivider()
            OutlinedButton(
                onClick = { onEvent(ConfigEvent.ResetRequested) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.config_reset))
            }
        }
    }

    if (state.confirmReset) ResetDialog(onEvent)
}

private fun seconds(millis: Long) = String.format(Locale.getDefault(), "%.1f s", millis / 1000.0)

@Composable
private fun languageName(tag: String): String = when (tag) {
    "cs" -> stringResource(R.string.config_language_cs)
    "sk" -> stringResource(R.string.config_language_sk)
    "en" -> stringResource(R.string.config_language_en)
    "de" -> stringResource(R.string.config_language_de)
    "pl" -> stringResource(R.string.config_language_pl)
    else -> tag
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = TalkativeTheme.dimens.spaceSmall),
    )
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = dimens.spaceMedium))
    }
}

@Composable
private fun GridRows(labelRes: Int, key: String, grid: GridSize, onChange: (GridSize) -> Unit) {
    Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
    StepperRow(
        label = stringResource(R.string.config_grid_columns),
        key = "$key-columns",
        value = grid.columns.toLong(),
        min = ConfigLimits.MIN_GRID_SIDE.toLong(),
        max = ConfigLimits.MAX_GRID_SIDE.toLong(),
        step = 1,
        format = { it.toString() },
        onChange = { onChange(grid.copy(columns = it.toInt())) },
    )
    StepperRow(
        label = stringResource(R.string.config_grid_rows),
        key = "$key-rows",
        value = grid.rows.toLong(),
        min = ConfigLimits.MIN_GRID_SIDE.toLong(),
        max = ConfigLimits.MAX_GRID_SIDE.toLong(),
        step = 1,
        format = { it.toString() },
        onChange = { onChange(grid.copy(rows = it.toInt())) },
    )
}

/** Hodnota s tlačítky − a +; tlačítka na mezích jsou neaktivní, takže nejde zadat nesmyslnou hodnotu. */
@Composable
private fun StepperRow(
    label: String,
    key: String,
    value: Long,
    min: Long,
    max: Long,
    step: Long,
    format: (Long) -> String,
    onChange: (Long) -> Unit,
) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { onChange(value - step) },
            enabled = value - step >= min,
            modifier = Modifier.size(dimens.minTouchTarget),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = format(value),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.widthIn(min = dimens.minTouchTarget).testTag(configValueTag(key)),
        )
        OutlinedButton(
            onClick = { onChange(value + step) },
            enabled = value + step <= max,
            modifier = Modifier.size(dimens.minTouchTarget),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ResetDialog(onEvent: (ConfigEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    AlertDialog(
        onDismissRequest = { onEvent(ConfigEvent.ResetDismissed) },
        title = { Text(stringResource(R.string.config_reset_title)) },
        text = { ScrollableColumn { Text(stringResource(R.string.config_reset_message)) } },
        confirmButton = {
            Button(
                onClick = { onEvent(ConfigEvent.ResetConfirmed) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.config_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ConfigEvent.ResetDismissed) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
