package cz.talkative.app.ui.exchange

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import cz.talkative.app.domain.exchange.ExportError
import cz.talkative.app.ui.common.FolderPickerDialog
import cz.talkative.app.ui.theme.TalkativeTheme

/** Značka stavového řádku exportu (průběh, výsledek nebo chyba). */
const val EXPORT_STATUS_TAG = "export-status"

/** Název souboru, který systémový výběr nabídne při ukládání. */
private const val SUGGESTED_FILE_NAME = "talkative.zip"

/** Napojení obrazovky na view model a na systémový výběr souboru pro uložení archivu. */
@Composable
fun ExchangeRoute(onBack: () -> Unit, viewModel: ExchangeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.onEvent(ExchangeEvent.ExportToFile(uri.toString()))
    }
    ExchangeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onChooseExportFile = { chooseFile.launch(SUGGESTED_FILE_NAME) },
    )
}

/** Bezstavová obrazovka exportu (a později importu). [onChooseExportFile] spustí systémový výběr cílového souboru. */
@Composable
fun ExchangeScreen(
    state: ExchangeUiState,
    onEvent: (ExchangeEvent) -> Unit,
    onBack: () -> Unit,
    onChooseExportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    val running = state.export is ExportStatus.Running
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
                text = stringResource(R.string.exchange_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBack, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.exchange_back))
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            Text(stringResource(R.string.exchange_section_export), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.exchange_export_hint), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = state.exportFolder?.let { stringResource(R.string.exchange_scope_folder, it.title) }
                    ?: stringResource(R.string.exchange_scope_all),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(EXPORT_SCOPE_TAG),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
                OutlinedButton(
                    onClick = { onEvent(ExchangeEvent.ExportWholeDictionary) },
                    enabled = !running && state.exportFolder != null,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) { Text(stringResource(R.string.exchange_whole_dictionary)) }
                OutlinedButton(
                    onClick = { onEvent(ExchangeEvent.ChooseExportFolder) },
                    enabled = !running,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) { Text(stringResource(R.string.exchange_choose_folder)) }
            }
            SwitchRow(R.string.exchange_include_config, state.includeConfig, enabled = !running) {
                onEvent(ExchangeEvent.IncludeConfigChanged(it))
            }
            Button(
                onClick = onChooseExportFile,
                enabled = !running,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) { Text(stringResource(R.string.exchange_export_button)) }
            ExportStatusText(state.export)
        }
    }

    state.picker?.let { picker ->
        FolderPickerDialog(
            title = stringResource(R.string.exchange_picker_title),
            confirmLabel = stringResource(R.string.exchange_picker_select),
            path = picker.path,
            folders = picker.folders,
            onBrowse = { onEvent(ExchangeEvent.BrowsePicker(it)) },
            onConfirm = { onEvent(ExchangeEvent.ConfirmPicker) },
            onDismiss = { onEvent(ExchangeEvent.DismissPicker) },
        )
    }
}

/** Značka textu s vybraným rozsahem exportu. */
const val EXPORT_SCOPE_TAG = "export-scope"

@Composable
private fun ExportStatusText(status: ExportStatus) {
    val dimens = TalkativeTheme.dimens
    when (status) {
        ExportStatus.Idle -> Unit
        is ExportStatus.Running -> Column(
            modifier = Modifier.fillMaxWidth().testTag(EXPORT_STATUS_TAG),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            if (status.total > 0) {
                Text(stringResource(R.string.exchange_export_running_files, status.done, status.total), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { status.done.toFloat() / status.total }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.exchange_export_running), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        is ExportStatus.Done -> Column(modifier = Modifier.testTag(EXPORT_STATUS_TAG)) {
            Text(stringResource(R.string.exchange_export_done, status.folders, status.words), style = MaterialTheme.typography.bodyLarge)
            if (status.missingMedia > 0) {
                Text(stringResource(R.string.exchange_export_done_missing, status.missingMedia), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is ExportStatus.Failed -> Text(
            text = stringResource(
                when (status.error) {
                    ExportError.STORAGE_FULL -> R.string.exchange_export_error_storage
                    ExportError.WRITE_FAILED -> R.string.exchange_export_error_write
                    ExportError.FOLDER_MISSING -> R.string.exchange_export_error_folder
                },
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(EXPORT_STATUS_TAG),
        )
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
