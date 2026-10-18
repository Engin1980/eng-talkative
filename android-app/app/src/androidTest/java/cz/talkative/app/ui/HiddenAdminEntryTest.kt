package cz.talkative.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.domain.model.AppConfig
import cz.talkative.app.ui.client.HIDDEN_ADMIN_ENTRY_TAG
import cz.talkative.app.ui.client.HiddenAdminEntry
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HiddenAdminEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var entered = 0

    @Before
    fun setUp() {
        entered = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            // Výchozí interval (10 s) – ověřuje i výchozí hodnotu.
            TalkativeTheme { HiddenAdminEntry(onEntered = { entered++ }) }
        }
    }

    private fun entry() = composeRule.onNodeWithTag(HIDDEN_ADMIN_ENTRY_TAG)

    @Test
    fun defaultHoldIntervalIsTenSeconds() {
        assertEquals(10_000L, AppConfig().adminHoldMillis)
    }

    @Test
    fun shortPress_doesNothing() {
        entry().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(500)
        entry().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(AppConfig().adminHoldMillis * 2)

        assertEquals(0, entered)
    }

    @Test
    fun holdJustBelowInterval_thenRelease_doesNothing() {
        entry().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(AppConfig().adminHoldMillis - 1_000)
        entry().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(AppConfig().adminHoldMillis * 2)

        assertEquals(0, entered)
    }

    @Test
    fun holdForWholeInterval_entersAdminOnce() {
        entry().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(AppConfig().adminHoldMillis - 1_000)
        assertEquals(0, entered) // ještě nedržel dost dlouho

        composeRule.mainClock.advanceTimeBy(2_000)
        assertEquals(1, entered)

        composeRule.mainClock.advanceTimeBy(AppConfig().adminHoldMillis)
        entry().performTouchInput { up() }
        assertEquals(1, entered) // další držení vstup neopakuje
    }

    @Test
    fun hiddenControl_hasMinimumTouchTarget() {
        entry().assertHeightIsAtLeast(64.dp).assertWidthIsAtLeast(64.dp)
    }
}
