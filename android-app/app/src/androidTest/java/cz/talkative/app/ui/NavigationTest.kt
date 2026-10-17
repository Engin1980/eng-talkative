package cz.talkative.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.compose.ui.platform.LocalContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.client.HIDDEN_ADMIN_ENTRY_TAG
import cz.talkative.app.ui.navigation.Routes
import cz.talkative.app.ui.navigation.TalkativeNavHost
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private fun setUpNavHost() {
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            TalkativeTheme {
                TalkativeNavHost(
                    navController = navController,
                    client = { openAdmin ->
                        ClientScreen(ClientUiState(profileId = "default"), onOpenAdmin = openAdmin)
                    },
                    admin = { back -> AdminScreen(state = AdminUiState(isLoading = false), onEvent = {}, onBackToClient = back) },
                )
            }
        }
    }

    @Test
    fun app_startsInClientMode() {
        setUpNavHost()

        composeRule.onNodeWithText("Profil: default").assertIsDisplayed()
        assertEquals(Routes.CLIENT, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun navigatesToAdminAndBackToClient() {
        setUpNavHost()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(HIDDEN_ADMIN_ENTRY_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(10_500)
        composeRule.mainClock.autoAdvance = true
        composeRule.onRoot().performTouchInput { up() } // uvolní prst; ovládací prvek už zmizel
        composeRule.onNodeWithText("Administrace").assertIsDisplayed()
        assertEquals(Routes.ADMIN, navController.currentBackStackEntry?.destination?.route)

        composeRule.onNodeWithText("Zpět do klienta").performClick()
        composeRule.onNodeWithText("Profil: default").assertIsDisplayed()
        assertEquals(Routes.CLIENT, navController.currentBackStackEntry?.destination?.route)
    }
}
