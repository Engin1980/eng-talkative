package cz.talkative.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigLimitsTest {

    @Test
    fun defaults_areWithinLimits_soSanitizingChangesNothing() {
        assertEquals(AppConfig(), AppConfig().sanitized())
    }

    @Test
    fun grid_isClampedToLimits() {
        val config = AppConfig(gridPortrait = GridSize(0, 99), gridLandscape = GridSize(-3, 7)).sanitized()

        assertEquals(GridSize(ConfigLimits.MIN_GRID_SIDE, ConfigLimits.MAX_GRID_SIDE), config.gridPortrait)
        assertEquals(GridSize(ConfigLimits.MIN_GRID_SIDE, ConfigLimits.MAX_GRID_SIDE), config.gridLandscape)
    }

    @Test
    fun holdIntervals_areClampedToLimits() {
        val low = AppConfig(removeHoldMillis = 0, adminHoldMillis = 100).sanitized()
        val high = AppConfig(removeHoldMillis = 999_999, adminHoldMillis = 999_999).sanitized()

        assertEquals(ConfigLimits.MIN_REMOVE_HOLD_MILLIS, low.removeHoldMillis)
        assertEquals(ConfigLimits.MIN_ADMIN_HOLD_MILLIS, low.adminHoldMillis)
        assertEquals(ConfigLimits.MAX_REMOVE_HOLD_MILLIS, high.removeHoldMillis)
        assertEquals(ConfigLimits.MAX_ADMIN_HOLD_MILLIS, high.adminHoldMillis)
    }

    @Test
    fun addHold_isClampedToLimits_andZeroMeansImmediately() {
        assertEquals(0L, AppConfig(addHoldMillis = 0).sanitized().addHoldMillis)
        assertEquals(0L, AppConfig(addHoldMillis = -5).sanitized().addHoldMillis)
        assertEquals(ConfigLimits.MAX_ADD_HOLD_MILLIS, AppConfig(addHoldMillis = 999_999).sanitized().addHoldMillis)
        assertEquals(1_500L, AppConfig(addHoldMillis = 1_500).sanitized().addHoldMillis)
    }

    @Test
    fun valuesInsideLimits_stayUntouched() {
        val config = AppConfig(removeHoldMillis = 2_500, adminHoldMillis = 7_000, gridPortrait = GridSize(2, 5))

        assertEquals(config, config.sanitized())
    }

    @Test
    fun blankCollation_fallsBackToDefault() {
        assertEquals("cs", AppConfig(collationLanguageTag = " ").sanitized().collationLanguageTag)
    }
}
