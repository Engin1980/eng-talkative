package cz.talkative.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import java.io.File

const val WORD_IMAGE_TAG = "word-image"

/** Jak z názvu souboru obrázku získat soubor. Zajišťuje ho aplikace (úložiště obrázků), testy ho mohou nahradit. */
val LocalImageResolver = compositionLocalOf<(String) -> File?> { { null } }

/**
 * Obrázek slovíčka. Když název chybí nebo soubor neexistuje, nezobrazí se nic a vrátí `false`
 * (volající pak ukáže jen text). Načítání, zmenšení na velikost prvku a cache řeší Coil.
 */
@Composable
fun WordImage(
    imageFile: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
): Boolean {
    val file = imageFile?.let { LocalImageResolver.current(it) } ?: return false
    AsyncImage(
        model = file,
        contentDescription = null,
        modifier = modifier.testTag(WORD_IMAGE_TAG),
        contentScale = contentScale,
    )
    return true
}
