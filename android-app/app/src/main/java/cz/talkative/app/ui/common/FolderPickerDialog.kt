package cz.talkative.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import cz.talkative.app.R
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.ui.theme.TalkativeTheme

/**
 * Procházení stromu složek a výběr jedné z nich. [path] vede ke složce, kterou dialog ukazuje (prázdná = kořen),
 * [folders] jsou její podsložky. Tlačítko [confirmLabel] vybere zobrazenou složku, v kořeni je vypnuté.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    confirmLabel: String,
    path: List<Folder>,
    folders: List<Folder>,
    onBrowse: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = TalkativeTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    PickerCrumb(stringResource(R.string.admin_breadcrumb_root), isCurrent = path.isEmpty()) { onBrowse(null) }
                    path.forEachIndexed { index, folder ->
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        PickerCrumb(folder.title, isCurrent = index == path.lastIndex) { onBrowse(folder.id) }
                    }
                }
                if (folders.isEmpty()) {
                    Text(stringResource(R.string.move_no_subfolders), style = MaterialTheme.typography.bodyLarge)
                }
                folders.forEach { folder ->
                    OutlinedButton(
                        onClick = { onBrowse(folder.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouchTarget),
                    ) {
                        Text(text = folder.title, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = path.isNotEmpty(), modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun PickerCrumb(text: String, isCurrent: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = TalkativeTheme.dimens.minTouchTarget)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
