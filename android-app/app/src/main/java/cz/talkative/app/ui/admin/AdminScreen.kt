package cz.talkative.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cz.talkative.app.R
import cz.talkative.app.ui.theme.TalkativeTheme

/** Prázdná obrazovka administrace; obsah přibude v kroku 1. */
@Composable
fun AdminScreen(onBackToClient: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = TalkativeTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMedium),
    ) {
        Text(
            text = stringResource(R.string.admin_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(
            onClick = onBackToClient,
            modifier = Modifier.heightIn(min = dimens.minTouchTarget),
        ) {
            Text(stringResource(R.string.admin_back_to_client))
        }
    }
}
