package cz.talkative.app.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import cz.talkative.app.R
import cz.talkative.app.domain.exchange.ImportError
import cz.talkative.app.ui.theme.TalkativeTheme
import cz.talkative.app.ui.common.ScrollableColumn

/** Značky částí importu, podle kterých testy hledají prvky. */
const val IMPORT_STATUS_TAG = "import-status"
const val IMPORT_PLAN_TAG = "import-plan"
const val IMPORT_TARGET_TAG = "import-target"

/** Import: výběr archivu, co a kam se přidá, a výsledek. Součást obrazovky [ExchangeScreen]. */
@Composable
internal fun ImportSection(import: ImportUiState, onEvent: (ExchangeEvent) -> Unit, onChooseArchive: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    Text(stringResource(R.string.exchange_section_import), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.exchange_import_hint), style = MaterialTheme.typography.bodyMedium)
    Button(
        onClick = onChooseArchive,
        enabled = !import.isBusy,
        modifier = Modifier.heightIn(min = dimens.minTouchTarget),
    ) { Text(stringResource(R.string.exchange_choose_archive)) }

    import.archive?.let { archive ->
        Text(
            stringResource(R.string.exchange_import_contents, archive.folderCount, archive.wordCount),
            style = MaterialTheme.typography.titleMedium,
        )
        ChoiceRow(stringResource(R.string.exchange_import_all), import.selectAll, enabled = !import.isBusy) {
            onEvent(ExchangeEvent.ImportSelectAllChanged(true))
        }
        ChoiceRow(stringResource(R.string.exchange_import_some), !import.selectAll, enabled = !import.isBusy) {
            onEvent(ExchangeEvent.ImportSelectAllChanged(false))
        }
        if (!import.selectAll) {
            archive.folders.forEach { row ->
                val inherited = import.isCovered(row.folder.id) && row.folder.id !in import.selectedFolderIds
                CheckRow(
                    label = row.folder.title,
                    checked = import.isCovered(row.folder.id),
                    enabled = !import.isBusy && !inherited,
                    indent = row.depth,
                ) { onEvent(ExchangeEvent.ToggleImportFolder(row.folder.id)) }
            }
            if (archive.hasRootWords) {
                CheckRow(stringResource(R.string.exchange_import_root_words), import.includeRootWords, enabled = !import.isBusy) {
                    onEvent(ExchangeEvent.IncludeRootWordsChanged(!import.includeRootWords))
                }
            }
        }

        SwitchRow(R.string.exchange_import_replace, import.replaceAll, enabled = !import.isBusy) {
            onEvent(ExchangeEvent.ReplaceAllChanged(it))
        }
        if (!import.replaceAll) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
                Text(
                    text = import.target?.let { stringResource(R.string.exchange_import_target_folder, it.title) }
                        ?: stringResource(R.string.exchange_import_target_root),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag(IMPORT_TARGET_TAG),
                )
                OutlinedButton(
                    onClick = { onEvent(ExchangeEvent.ChooseImportTarget) },
                    enabled = !import.isBusy,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) { Text(stringResource(R.string.exchange_import_change_target)) }
            }
        }
        if (archive.hasConfig) {
            SwitchRow(R.string.exchange_import_config, import.importConfig, enabled = !import.isBusy) {
                onEvent(ExchangeEvent.ImportConfigChanged(it))
            }
        }
        Text(
            stringResource(R.string.exchange_import_plan, import.plannedFolders, import.plannedWords),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(IMPORT_PLAN_TAG),
        )
        Button(
            onClick = { onEvent(ExchangeEvent.StartImport) },
            enabled = import.canImport,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget),
        ) { Text(stringResource(R.string.exchange_import_button)) }
    }
    ImportStatusText(import.status)
    if (import.confirmReplace) ConfirmReplaceDialog(onEvent)
}

@Composable
private fun ImportStatusText(status: ImportStatus) {
    val dimens = TalkativeTheme.dimens
    when (status) {
        ImportStatus.Idle -> Unit
        ImportStatus.Reading, ImportStatus.Running -> Column(
            modifier = Modifier.fillMaxWidth().testTag(IMPORT_STATUS_TAG),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            Text(
                stringResource(if (status == ImportStatus.Reading) R.string.exchange_import_reading else R.string.exchange_import_running),
                style = MaterialTheme.typography.bodyLarge,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ImportStatus.Done -> Column(modifier = Modifier.testTag(IMPORT_STATUS_TAG)) {
            Text(stringResource(R.string.exchange_import_done, status.folders, status.words), style = MaterialTheme.typography.bodyLarge)
            if (status.configImported) {
                Text(stringResource(R.string.exchange_import_done_config), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is ImportStatus.Failed -> Text(
            text = stringResource(
                when (status.error) {
                    ImportError.NOT_AN_ARCHIVE -> R.string.exchange_import_error_not_archive
                    ImportError.NEWER_VERSION -> R.string.exchange_import_error_newer
                    ImportError.INVALID_ARCHIVE -> R.string.exchange_import_error_invalid
                    ImportError.UNREADABLE -> R.string.exchange_import_error_unreadable
                    ImportError.STORAGE_FULL -> R.string.exchange_import_error_storage
                    ImportError.TARGET_MISSING -> R.string.exchange_import_error_target
                    ImportError.FAILED -> R.string.exchange_import_error_failed
                },
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(IMPORT_STATUS_TAG),
        )
    }
}

@Composable
private fun ConfirmReplaceDialog(onEvent: (ExchangeEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    AlertDialog(
        onDismissRequest = { onEvent(ExchangeEvent.DismissReplace) },
        title = { Text(stringResource(R.string.exchange_replace_title)) },
        text = { ScrollableColumn { Text(stringResource(R.string.exchange_replace_message)) } },
        confirmButton = {
            Button(onClick = { onEvent(ExchangeEvent.ConfirmReplace) }, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.exchange_replace_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ExchangeEvent.DismissReplace) }, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = dimens.spaceMedium))
    }
}

/** Řádek se zaškrtávátkem; [indent] odsazuje podsložky podle hloubky ve stromu. */
@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, indent: Int = 0, onToggle: () -> Unit) {
    val dimens = TalkativeTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(dimens.spaceLarge * indent))
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = dimens.spaceMedium))
    }
}
