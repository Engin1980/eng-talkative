package cz.talkative.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    outline = LightOutline,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    outline = DarkOutline,
)

/** Barvy orámování slova, které se právě drží: [add] při držení pro přidání do věty, [remove] pro odebrání. */
data class HoldColors(val add: Color, val remove: Color)

private val LightHoldColors = HoldColors(add = LightHoldAdd, remove = LightHoldRemove)
private val DarkHoldColors = HoldColors(add = DarkHoldAdd, remove = DarkHoldRemove)
internal val LocalHoldColors = staticCompositionLocalOf { LightHoldColors }

/**
 * Téma aplikace. Světlý/tmavý režim se řídí systémem; obě varianty mají vysoký kontrast.
 * Barvy a typografie přes [MaterialTheme], rozměry přes [TalkativeTheme.dimens].
 */
@Composable
fun TalkativeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDimens provides Dimens(),
        LocalHoldColors provides if (darkTheme) DarkHoldColors else LightHoldColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = TalkativeTypography,
            content = content,
        )
    }
}

object TalkativeTheme {
    val holdColors: HoldColors
        @Composable get() = LocalHoldColors.current

    val dimens: Dimens
        @Composable get() = LocalDimens.current
}
