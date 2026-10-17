package cz.talkative.app.ui.client

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.ui.theme.TalkativeTheme
import kotlinx.coroutines.withTimeoutOrNull

const val HIDDEN_ADMIN_ENTRY_TAG = "hidden-admin-entry"

/**
 * Neviditelná plocha, jejíž podržení po dobu [holdMillis] vstoupí do administrace.
 * Kratší dotek nedělá nic. Neslouží k zabezpečení, jen jako ochrana před náhodným vstupem.
 */
@Composable
fun HiddenAdminEntry(
    onEntered: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Long = AppConfig().adminHoldMillis,
) {
    val currentOnEntered by rememberUpdatedState(onEntered)
    Box(
        modifier = modifier
            .size(TalkativeTheme.dimens.minTouchTarget)
            .testTag(HIDDEN_ADMIN_ENTRY_TAG)
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var released = false
                    withTimeoutOrNull(holdMillis) {
                        waitForUpOrCancellation()
                        released = true
                    }
                    if (!released) {
                        currentOnEntered()
                        waitForUpOrCancellation()
                    }
                }
            },
    )
}
