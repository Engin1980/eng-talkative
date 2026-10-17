package cz.talkative.app.ui.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.ui.theme.TalkativeTheme

/** Napojení obrazovky na view model. */
@Composable
fun ClientRoute(onOpenAdmin: () -> Unit, viewModel: ClientViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClientScreen(state = state, onOpenAdmin = onOpenAdmin)
}

/** Bezstavová obrazovka klienta; zatím jen ukázka řetězce UI → doména → data a skrytý vstup do administrace. */
@Composable
fun ClientScreen(
    state: ClientUiState,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(TalkativeTheme.dimens.spaceLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.profileId
                ?.let { stringResource(R.string.client_profile, it) }
                ?: stringResource(R.string.client_loading),
            style = MaterialTheme.typography.headlineMedium,
        )
        HiddenAdminEntry(
            onEntered = onOpenAdmin,
            holdMillis = state.adminHoldMillis,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
