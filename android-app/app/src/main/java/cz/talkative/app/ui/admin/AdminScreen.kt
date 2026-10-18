package cz.talkative.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.io.IOException
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.verticalScroll
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.Context
import android.content.ActivityNotFoundException
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.model.DictionaryItem
import cz.talkative.app.domain.model.Folder
import cz.talkative.app.domain.model.Word
import cz.talkative.app.ui.common.WordImage
import cz.talkative.app.ui.theme.TalkativeTheme

/** Napojení obrazovky na view model. */
@Composable
fun AdminRoute(onBackToClient: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AdminScreen(state = state, onEvent = viewModel::onEvent, onBackToClient = onBackToClient)
}

/** Bezstavová obrazovka administrace: výpis aktuální složky, navigace ve stromu a editace. */
@Composable
fun AdminScreen(
    state: AdminUiState,
    onEvent: (AdminEvent) -> Unit,
    onBackToClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    val atRoot = state.path.isEmpty()
    // Systémové "zpět" jde o úroveň výš; v kořeni ho zpracuje navigace (návrat do klienta).
    BackHandler(enabled = !atRoot) { onEvent(AdminEvent.NavigateUp) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.admin_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onBackToClient,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.admin_back_to_client))
            }
        }

        Breadcrumbs(path = state.path, onGoToFolder = { onEvent(AdminEvent.GoToFolder(it)) })

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium)) {
            if (!atRoot) {
                OutlinedButton(
                    onClick = { onEvent(AdminEvent.NavigateUp) },
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) {
                    Text(stringResource(R.string.admin_up))
                }
            }
            Button(
                onClick = { onEvent(AdminEvent.AddFolder) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.admin_add_folder))
            }
            Button(
                onClick = { onEvent(AdminEvent.AddWord) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.admin_add_word))
            }
        }

        when {
            state.isLoading -> Unit
            state.items.isEmpty() -> EmptyFolderHint(modifier = Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
            ) {
                items(state.items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = {
                            if (item is Folder) onEvent(AdminEvent.GoToFolder(item.id)) else onEvent(AdminEvent.EditItem(item))
                        },
                        onEdit = { onEvent(AdminEvent.EditItem(item)) },
                        onDelete = { onEvent(AdminEvent.DeleteItem(item)) },
                    )
                }
            }
        }
    }

    state.dialog?.let { AdminDialogHost(dialog = it, onEvent = onEvent) }
}

@Composable
private fun Breadcrumbs(path: List<Folder>, onGoToFolder: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(
            text = stringResource(R.string.admin_breadcrumb_root),
            isCurrent = path.isEmpty(),
            onClick = { onGoToFolder(null) },
        )
        path.forEachIndexed { index, folder ->
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            Crumb(
                text = folder.title,
                isCurrent = index == path.lastIndex,
                onClick = { onGoToFolder(folder.id) },
            )
        }
    }
}

@Composable
private fun Crumb(text: String, isCurrent: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = TalkativeTheme.dimens.minTouchTarget),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EmptyFolderHint(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.admin_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.admin_empty_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: DictionaryItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = TalkativeTheme.dimens
    val isFolder = item is Folder
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(start = dimens.spaceMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item is Word) {
                WordImage(
                    imageFile = item.imageFile,
                    modifier = Modifier.padding(end = dimens.spaceMedium).size(dimens.thumbnailSize),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(vertical = dimens.spaceSmall)) {
                Text(text = item.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(if (isFolder) R.string.admin_kind_folder else R.string.admin_kind_word),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (isFolder) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconSize),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(dimens.minTouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.admin_edit_item, item.title),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(dimens.minTouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.admin_delete_item, item.title),
                )
            }
        }
    }
}

@Composable
private fun AdminDialogHost(dialog: AdminDialog, onEvent: (AdminEvent) -> Unit) {
    when (dialog) {
        is AdminDialog.Edit -> EditDialog(dialog, onEvent)
        is AdminDialog.ConfirmDelete -> ConfirmDeleteDialog(dialog, onEvent)
    }
}

@Composable
private fun EditDialog(dialog: AdminDialog.Edit, onEvent: (AdminEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val isNew = dialog.itemId == null
    val titleRes = when (dialog.kind) {
        ItemKind.FOLDER -> if (isNew) R.string.dialog_folder_new else R.string.dialog_folder_edit
        ItemKind.WORD -> if (isNew) R.string.dialog_word_new else R.string.dialog_word_edit
    }
    AlertDialog(
        onDismissRequest = { onEvent(AdminEvent.DismissDialog) },
        // Okno dialogu se musí zmenšit nad klávesnicí, jinak v režimu na šířku zakryje tlačítka Uložit/Zrušit.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.imePadding(),
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            ) {
                OutlinedTextField(
                    value = dialog.title,
                    onValueChange = { onEvent(AdminEvent.TitleChanged(it)) },
                    label = { Text(stringResource(R.string.dialog_title_label)) },
                    singleLine = true,
                    isError = dialog.showEmptyError,
                    supportingText = if (dialog.showEmptyError) {
                        { Text(stringResource(R.string.dialog_title_empty_error)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dialog.kind == ItemKind.WORD) {
                    OutlinedTextField(
                        value = dialog.alternateText,
                        onValueChange = { onEvent(AdminEvent.AlternateTextChanged(it)) },
                        label = { Text(stringResource(R.string.dialog_alternate_text_label)) },
                        supportingText = { Text(stringResource(R.string.dialog_alternate_text_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ImageSection(dialog, onEvent)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(AdminEvent.ConfirmEdit) },
                enabled = !dialog.isProcessingImage,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(AdminEvent.DismissDialog) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/** Náhled obrázku slovíčka a tlačítka pro výběr z galerie, pořízení fotky a odebrání. */
@Composable
private fun ImageSection(dialog: AdminDialog.Edit, onEvent: (AdminEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onEvent(AdminEvent.ImagePicked(uri.toString()))
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        // Zrušené focení (taken == false) není chyba, jen se nic nemění.
        if (taken) cameraUri?.let { onEvent(AdminEvent.ImagePicked(it.toString())) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
        Text(text = stringResource(R.string.image_label), style = MaterialTheme.typography.titleLarge)
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.imagePreviewSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                dialog.isProcessingImage -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(dimens.iconSize))
                    Text(
                        text = stringResource(R.string.image_processing),
                        modifier = Modifier.padding(start = dimens.spaceMedium),
                    )
                }
                else -> if (!WordImage(dialog.imageFile, Modifier.size(dimens.imagePreviewSize))) {
                    Text(text = stringResource(R.string.image_none), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        dialog.imageProblem?.let { problem ->
            Text(
                text = stringResource(problem.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(IMAGE_PROBLEM_TAG),
            )
        }
        Button(
            onClick = {
                try {
                    pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: ActivityNotFoundException) {
                    onEvent(AdminEvent.ImageSelectionFailed(ImageProblem.NO_GALLERY))
                }
            },
            enabled = !dialog.isProcessingImage,
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouchTarget),
        ) {
            Text(stringResource(R.string.image_pick_gallery))
        }
        Button(
            onClick = {
                try {
                    val uri = createCameraUri(context)
                    cameraUri = uri
                    takePicture.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    onEvent(AdminEvent.ImageSelectionFailed(ImageProblem.NO_CAMERA))
                } catch (e: SecurityException) {
                    onEvent(AdminEvent.ImageSelectionFailed(ImageProblem.NO_CAMERA))
                } catch (e: IOException) {
                    onEvent(AdminEvent.ImageSelectionFailed(ImageProblem.STORAGE_FULL))
                }
            },
            enabled = !dialog.isProcessingImage,
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouchTarget),
        ) {
            Text(stringResource(R.string.image_take_photo))
        }
        if (dialog.imageFile != null) {
            OutlinedButton(
                onClick = { onEvent(AdminEvent.RemoveImage) },
                enabled = !dialog.isProcessingImage,
                modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.image_remove))
            }
        }
    }
}

const val IMAGE_PROBLEM_TAG = "image-problem"

private fun ImageProblem.messageRes(): Int = when (this) {
    ImageProblem.UNREADABLE -> R.string.image_problem_unreadable
    ImageProblem.STORAGE_FULL -> R.string.image_problem_storage_full
    ImageProblem.NO_GALLERY -> R.string.image_problem_no_gallery
    ImageProblem.NO_CAMERA -> R.string.image_problem_no_camera
}

/** Dočasný soubor v `cache/camera` pro fotoaparát; zmenšená verze se uloží do úložiště obrázků. */
private fun createCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File.createTempFile("photo-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun ConfirmDeleteDialog(dialog: AdminDialog.ConfirmDelete, onEvent: (AdminEvent) -> Unit) {
    val dimens = TalkativeTheme.dimens
    val item = dialog.item
    val isFolder = item is Folder
    val message = when {
        !isFolder -> stringResource(R.string.delete_word_message, item.title)
        dialog.contentCount == 0 -> stringResource(R.string.delete_folder_message_empty, item.title)
        else -> stringResource(R.string.delete_folder_message_with_items, item.title, dialog.contentCount)
    }
    AlertDialog(
        onDismissRequest = { onEvent(AdminEvent.DismissDialog) },
        title = {
            Text(stringResource(if (isFolder) R.string.delete_folder_title else R.string.delete_word_title))
        },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onEvent(AdminEvent.ConfirmDelete) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(AdminEvent.DismissDialog) },
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
