package cz.talkative.app.ui.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.ui.common.onHold
import cz.talkative.app.ui.theme.TalkativeTheme

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
    Box(
        modifier = modifier
            .size(TalkativeTheme.dimens.minTouchTarget)
            .testTag(HIDDEN_ADMIN_ENTRY_TAG)
            .onHold(holdMillis, onHold = onEntered),
    )
}
