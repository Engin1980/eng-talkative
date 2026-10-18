package cz.talkative.app.ui.client

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import cz.talkative.app.R
import cz.talkative.app.domain.speech.SpeechError
import cz.talkative.app.ui.theme.TalkativeTheme

/** Ikona Stop (vyplněný čtverec); sada základních ikon Material ji neobsahuje. */
private val StopIcon: ImageVector = materialIcon(name = "Filled.Stop") {
    materialPath {
        moveTo(6f, 6f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        close()
    }
}

/** Přehrát; během řeči se mění na Zastavit (aktivní i při smazané větě, aby šlo řeč vždy zastavit). */
@Composable
fun PlayStopButton(isSpeaking: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = TalkativeTheme.dimens
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = dimens.minTouchTarget),
    ) {
        Icon(
            imageVector = if (isSpeaking) StopIcon else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(dimens.iconSize),
        )
        Text(
            text = stringResource(if (isSpeaking) R.string.client_stop else R.string.client_play),
            modifier = Modifier.padding(start = dimens.spaceSmall),
        )
    }
}

/** Srozumitelná hláška o selhání řeči s nápovědou, co dělat. */
@Composable
fun SpeechErrorDialog(error: SpeechError, onDismiss: () -> Unit) {
    val message = when (error) {
        SpeechError.ENGINE_UNAVAILABLE -> R.string.client_speech_error_engine
        SpeechError.LANGUAGE_MISSING -> R.string.client_speech_error_language
        SpeechError.FAILED -> R.string.client_speech_error_failed
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.client_speech_error_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.heightIn(min = TalkativeTheme.dimens.minTouchTarget)) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
    )
}
