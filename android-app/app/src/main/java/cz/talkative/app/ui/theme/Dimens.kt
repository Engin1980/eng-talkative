package cz.talkative.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Rozměry aplikace. Obrazovky berou velikosti odsud, ne natvrdo. */
data class Dimens(
    /** Minimální velikost dotykové plochy (přístupnost). */
    val minTouchTarget: Dp = 64.dp,
    val iconSize: Dp = 32.dp,
    val borderWidth: Dp = 2.dp,
    val thumbnailSize: Dp = 56.dp,
    /** Výška obrázku ve slově ve větě (menší dlaždice než v mřížce). */
    val sentenceImageSize: Dp = 64.dp,
    val imagePreviewSize: Dp = 160.dp,
    /** Největší šířka loga ARASAAC na obrazovce „O aplikaci“. */
    val logoMaxWidth: Dp = 320.dp,
    /** Nejmenší šířka dlaždice piktogramu ve výsledcích hledání. */
    val pictogramTileMinSize: Dp = 160.dp,
    /** Nejmenší šířka panelu nástrojů vedle plátna (kreslení na šířku). */
    val drawToolbarMinWidth: Dp = 360.dp,
    /** Nejmenší výška panelu nástrojů pod plátnem (kreslení na výšku). */
    val drawToolbarMinHeight: Dp = 360.dp,
    val swatchSelectedBorder: Dp = 4.dp,
    /** Šířka jezdce posuvníku (výška je dotyková plocha). */
    val sliderThumbWidth: Dp = 24.dp,
    val swatchInset: Dp = 6.dp,
    /** Šířka a nejmenší délka jezdce ukazatele posunu u posouvaného obsahu. */
    val scrollIndicatorWidth: Dp = 6.dp,
    val scrollIndicatorMinLength: Dp = 32.dp,
    /** Jak daleko za pravý okraj posouvaného obsahu končí jezdec; leží v okraji, který obsah obklopuje. */
    val scrollIndicatorOutset: Dp = 12.dp,
    val sentenceBarMinHeight: Dp = 80.dp,
    val sentenceBarMaxHeight: Dp = 176.dp,
    val spaceSmall: Dp = 8.dp,
    val spaceMedium: Dp = 16.dp,
    val spaceLarge: Dp = 24.dp,
)

internal val LocalDimens = staticCompositionLocalOf { Dimens() }
