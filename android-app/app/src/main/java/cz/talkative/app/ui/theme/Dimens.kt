package cz.talkative.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Rozměry aplikace. Obrazovky berou velikosti odsud, ne natvrdo. */
data class Dimens(
    /** Minimální velikost dotykové plochy (přístupnost). */
    val minTouchTarget: Dp = 64.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
)

internal val LocalDimens = staticCompositionLocalOf { Dimens() }
