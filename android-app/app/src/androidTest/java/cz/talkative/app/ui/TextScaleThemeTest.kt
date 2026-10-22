package cz.talkative.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TextScaleThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textScale_multipliesAllTextStyles_keepingTheirRatios() {
        var normal = emptyList<Float>()
        var scaled = emptyList<Float>()
        composeRule.setContent {
            TalkativeTheme { Sizes { normal = it } }
            TalkativeTheme(textScale = 1.5f) { Sizes { scaled = it } }
        }
        composeRule.waitForIdle()

        assertEquals(normal.size, scaled.size)
        normal.zip(scaled).forEach { (before, after) -> assertEquals(before * 1.5f, after, 0.01f) }
    }
}

/** Velikosti (písmo a řádkování) několika stylů z aktuálního tématu. */
@Composable
private fun Sizes(report: (List<Float>) -> Unit) {
    val t = MaterialTheme.typography
    report(
        listOf(t.headlineMedium, t.titleLarge, t.titleMedium, t.bodyLarge, t.bodyMedium, t.labelLarge)
            .flatMap { listOf(it.fontSize.value, it.lineHeight.value) },
    )
}
