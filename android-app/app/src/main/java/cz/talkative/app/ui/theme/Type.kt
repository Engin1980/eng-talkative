package cz.talkative.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Větší písmo než výchozí Material 3, aby byl text dobře čitelný na tabletu.
internal val TalkativeTypography = Typography(
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
)

/**
 * Typografie se všemi styly zvětšenými (zmenšenými) o [scale]; velikost i řádkování, takže poměry mezi styly
 * (nadpis větší než text) zůstanou. Všechen text aplikace bere styl z [androidx.compose.material3.MaterialTheme].
 */
internal fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = displayLarge.scaled(), displayMedium = displayMedium.scaled(), displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(), headlineMedium = headlineMedium.scaled(), headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(), titleMedium = titleMedium.scaled(), titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(), bodyMedium = bodyMedium.scaled(), bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(), labelMedium = labelMedium.scaled(), labelSmall = labelSmall.scaled(),
    )
}
