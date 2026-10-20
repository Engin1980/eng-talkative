package cz.talkative.app.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Zavolá [onHold], až když prst zůstane na prvku po celou dobu [holdMillis]. Kratší dotek (nebo přerušení,
 * např. posunem rodičovského seznamu) nedělá nic. Akce se při jednom stisku spustí nejvýše jednou.
 * [onHeldChange] hlásí, zda je prvek právě držen (od dotyku do uvolnění nebo přerušení), aby šlo zvýraznit stisk.
 */
@Composable
fun Modifier.onHold(holdMillis: Long, onHeldChange: (Boolean) -> Unit = {}, onHold: () -> Unit): Modifier {
    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnHeldChange by rememberUpdatedState(onHeldChange)
    return pointerInput(holdMillis) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentOnHeldChange(true)
            try {
                var released = false
                withTimeoutOrNull(holdMillis) {
                    waitForUpOrCancellation()
                    released = true
                }
                if (!released) {
                    currentOnHold()
                    waitForUpOrCancellation()
                }
            } finally {
                currentOnHeldChange(false)
            }
        }
    }
}
