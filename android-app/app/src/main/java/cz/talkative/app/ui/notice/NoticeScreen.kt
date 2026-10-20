package cz.talkative.app.ui.notice

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.talkative.app.R
import cz.talkative.app.ui.about.AboutContent
import cz.talkative.app.ui.about.versionNameOf
import cz.talkative.app.ui.common.openLink
import cz.talkative.app.ui.theme.TalkativeTheme

/**
 * Brána před celou aplikací: dokud uživatel nevzal na vědomí aktuální oznámení, ukazuje se jen [NoticeScreen].
 * „Odejít“ aplikaci zavře a příště se oznámení ukáže znovu.
 */
@Composable
fun NoticeGateRoute(content: @Composable () -> Unit, viewModel: NoticeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val versionName = remember { versionNameOf(context) }
    NoticeGate(
        state = state,
        versionName = versionName,
        onAcknowledge = viewModel::acknowledge,
        onLeave = { (context as? Activity)?.finishAffinity() },
        onOpenLink = { openLink(context, it) },
        content = content,
    )
}

/** Bezstavová brána: podle [state] nic, úvodní oznámení, nebo [content]. */
@Composable
fun NoticeGate(
    state: NoticeState,
    versionName: String,
    onAcknowledge: () -> Unit,
    onLeave: () -> Unit,
    onOpenLink: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    when (state) {
        NoticeState.LOADING -> Unit
        NoticeState.REQUIRED -> NoticeScreen(versionName, onAcknowledge, onLeave, onOpenLink)
        NoticeState.ACKNOWLEDGED -> content()
    }
}

/** Úvodní oznámení: „O aplikaci“ s uvedením ARASAAC a dvě tlačítka. Systémové Zpět se chová jako „Odejít“. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoticeScreen(
    versionName: String,
    onAcknowledge: () -> Unit,
    onLeave: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = TalkativeTheme.dimens
    BackHandler { onLeave() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Text(stringResource(R.string.notice_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.notice_intro), style = MaterialTheme.typography.bodyLarge)
        AboutContent(versionName = versionName, onOpenLink = onOpenLink, modifier = Modifier.weight(1f))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSmall),
        ) {
            Button(onClick = onAcknowledge, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.notice_acknowledge))
            }
            OutlinedButton(onClick = onLeave, modifier = Modifier.heightIn(min = dimens.minTouchTarget)) {
                Text(stringResource(R.string.notice_leave))
            }
        }
    }
}
