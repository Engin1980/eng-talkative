package cz.talkative.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backButton_callsCallback_andHasMinimumTouchTarget() {
        var clicked = false
        composeRule.setContent {
            TalkativeTheme { AdminScreen(onBackToClient = { clicked = true }) }
        }

        composeRule.onNodeWithText("Zpět do klienta")
            .assertHeightIsAtLeast(64.dp)
            .performClick()

        assertTrue(clicked)
    }
}
