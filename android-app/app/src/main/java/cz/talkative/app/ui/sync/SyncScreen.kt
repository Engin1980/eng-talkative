package cz.talkative.app.ui.sync

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.sync.SyncError
import cz.talkative.app.ui.common.ScrollableColumn
import cz.talkative.app.ui.theme.TalkativeTheme
import java.text.DateFormat
import java.util.Date

/** Značka textu chyby (přihlášení i poslední synchronizace). */
const val SYNC_ERROR_TAG = "sync-error"

/** Napojení obrazovky na view model a na systémový dialog souhlasu s přístupem k Drive. */
@Composable
fun SyncRoute(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onEvent(SyncEvent.ConsentFinished(if (result.resultCode == Activity.RESULT_OK) result.data else null))
    }
    LaunchedEffect(state.consent) {
        val handle = state.consent?.handle as? IntentSender ?: return@LaunchedEffect
        viewModel.onEvent(SyncEvent.ConsentLaunched)
        consentLauncher.launch(IntentSenderRequest.Builder(handle).build())
    }
    SyncScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/** Bezstavová obrazovka Synchronizace: přihlášený účet, odhlášení a stav poslední synchronizace. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncScreen(
    state: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    BackHandler { onBack() }

    ScrollableColumn(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sync_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBack, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.sync_back))
            }
        }
        Text(stringResource(R.string.sync_description), style = MaterialTheme.typography.bodyLarge)

        val account = state.sync.account
        Text(
            text = when {
                account == null -> stringResource(R.string.sync_account_none)
                account.isBlank() -> stringResource(R.string.sync_account_signed_in, stringResource(R.string.sync_account_unknown))
                else -> stringResource(R.string.sync_account_signed_in, account)
            },
            style = MaterialTheme.typography.titleLarge,
        )

        if (account != null) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall)) {
                Text(
                    text = when {
                        state.sync.isRunning -> stringResource(R.string.sync_running)
                        state.sync.lastSuccessMillis != null ->
                            stringResource(R.string.sync_last_success, DateFormat.getDateTimeInstance().format(Date(state.sync.lastSuccessMillis)))
                        else -> stringResource(R.string.sync_never)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.sync.lastError?.let { error ->
                    Text(
                        text = stringResource(R.string.sync_last_error, stringResource(error.messageRes())),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(SYNC_ERROR_TAG),
                    )
                }
            }
        }

        state.signInError?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(SYNC_ERROR_TAG),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            Button(
                onClick = { onEvent(SyncEvent.SignInRequested) },
                enabled = !state.isSigningIn,
                modifier = Modifier.heightIn(min = dimens.minTouchTarget),
            ) {
                Text(stringResource(if (account == null) R.string.sync_sign_in else R.string.sync_change_account))
            }
            if (account != null) {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.SignOutRequested) },
                    enabled = !state.isSigningIn,
                    modifier = Modifier.heightIn(min = dimens.minTouchTarget),
                ) {
                    Text(stringResource(R.string.sync_sign_out))
                }
            }
        }
        if (state.isSigningIn) Text(stringResource(R.string.sync_signing_in), style = MaterialTheme.typography.bodyLarge)
        if (account != null) Text(stringResource(R.string.sync_sign_out_note), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SyncError.messageRes(): Int = when (this) {
    SyncError.NOT_SIGNED_IN -> R.string.sync_error_not_signed_in
    SyncError.NEEDS_CONSENT -> R.string.sync_error_needs_consent
    SyncError.NO_NETWORK -> R.string.sync_error_no_network
    SyncError.UNAUTHORIZED -> R.string.sync_error_unauthorized
    SyncError.SERVER_ERROR -> R.string.sync_error_server
    SyncError.BAD_RESPONSE -> R.string.sync_error_bad_response
    SyncError.NEWER_VERSION -> R.string.sync_error_newer_version
    SyncError.LOCAL_STORAGE -> R.string.sync_error_local_storage
    SyncError.NO_PLAY_SERVICES -> R.string.sync_error_no_play_services
}
