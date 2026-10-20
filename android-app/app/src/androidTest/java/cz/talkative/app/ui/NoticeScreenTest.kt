package cz.talkative.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.ui.notice.NoticeGate
import cz.talkative.app.ui.notice.NoticeState
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoticeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var acknowledged = 0
    private var left = 0
    private val opened = mutableListOf<String>()

    private fun show(state: NoticeState) {
        composeRule.setContent {
            TalkativeTheme {
                NoticeGate(
                    state = state,
                    versionName = "0.1.0",
                    onAcknowledge = { acknowledged++ },
                    onLeave = { left++ },
                    onOpenLink = { opened += it },
                ) {
                    Box { Text("OBSAH APLIKACE") }
                }
            }
        }
    }

    @Test
    fun required_showsAboutWithCreditAndBothButtons_andHidesTheApp() {
        show(NoticeState.REQUIRED)

        composeRule.onNodeWithText("Než začnete").assertIsDisplayed()
        composeRule.onNodeWithText("Piktogramy ARASAAC").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Autor piktogramů:", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Beru na vědomí").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithText("Odejít").assertIsDisplayed().assertHeightIsAtLeast(64.dp)
        composeRule.onAllNodesWithText("OBSAH APLIKACE").assertCountEquals(0)
    }

    @Test
    fun acknowledgeButton_reportsAcknowledgement_leaveButtonLeaves() {
        show(NoticeState.REQUIRED)

        composeRule.onNodeWithText("Beru na vědomí").performClick()
        composeRule.onNodeWithText("Odejít").performClick()

        assertEquals(1, acknowledged)
        assertEquals(1, left)
    }

    @Test
    fun acknowledged_showsTheApp() {
        show(NoticeState.ACKNOWLEDGED)

        composeRule.onNodeWithText("OBSAH APLIKACE").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beru na vědomí").assertCountEquals(0)
    }

    @Test
    fun loading_showsNothing() {
        show(NoticeState.LOADING)

        composeRule.onAllNodesWithText("OBSAH APLIKACE").assertCountEquals(0)
        composeRule.onAllNodesWithText("Beru na vědomí").assertCountEquals(0)
    }
}
