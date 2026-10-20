package cz.talkative.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
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
import cz.talkative.app.ui.about.AboutScreen
import cz.talkative.app.ui.admin.AdminScreen
import cz.talkative.app.ui.admin.AdminUiState
import cz.talkative.app.ui.client.ClientScreen
import cz.talkative.app.ui.client.ClientUiState
import cz.talkative.app.ui.client.HIDDEN_ADMIN_ENTRY_TAG
import cz.talkative.app.ui.config.ConfigScreen
import cz.talkative.app.ui.config.ConfigUiState
import cz.talkative.app.ui.exchange.ExchangeScreen
import cz.talkative.app.ui.exchange.ExchangeUiState
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
                        ClientScreen(ClientUiState(isLoading = false), onEvent = {}, onOpenAdmin = openAdmin)
                    },
                    admin = { back, openConfig, openExchange, openAbout ->
                        AdminScreen(
                            state = AdminUiState(isLoading = false),
                            onEvent = {},
                            onBackToClient = back,
                            onOpenConfig = openConfig,
                            onOpenExchange = openExchange,
                            onOpenAbout = openAbout,
                        )
                    },
                    config = { back -> ConfigScreen(ConfigUiState(isLoading = false), onEvent = {}, onBack = back) },
                    about = { back -> AboutScreen(versionName = "1.2.3", onBack = back, onOpenLink = {}) },
                    exchange = { back ->
                        ExchangeScreen(ExchangeUiState(), onEvent = {}, onBack = back, onChooseExportFile = {}, onChooseArchive = {})
                    },
                )
            }
        }
    }

    @Test
    fun app_startsInClientMode() {
        setUpNavHost()

        composeRule.onNodeWithText("Tato složka je prázdná.").assertIsDisplayed()
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
        composeRule.onNodeWithText("Tato složka je prázdná.").assertIsDisplayed()
        assertEquals(Routes.CLIENT, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun adminOpensConfig_andBackReturnsToAdmin() {
        setUpNavHost()
        composeRule.runOnUiThread { navController.navigate(Routes.ADMIN) }
        composeRule.onNodeWithText("Administrace").assertIsDisplayed()

        composeRule.onNodeWithText("Nastavení").performClick()
        assertEquals(Routes.CONFIG, navController.currentBackStackEntry?.destination?.route)
        composeRule.onNodeWithText("Nastavení platí jen pro toto zařízení a nesynchronizuje se.").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět do administrace").performClick()
        assertEquals(Routes.ADMIN, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun adminOpensExchange_andBackReturnsToAdmin() {
        setUpNavHost()
        composeRule.runOnUiThread { navController.navigate(Routes.ADMIN) }

        composeRule.onNodeWithText("Export a import").performClick()
        assertEquals(Routes.EXCHANGE, navController.currentBackStackEntry?.destination?.route)
        composeRule.onNodeWithText("Exportovat do souboru…").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět do administrace").performClick()
        assertEquals(Routes.ADMIN, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun adminOpensAbout_andBackReturnsToAdmin() {
        setUpNavHost()
        composeRule.runOnUiThread { navController.navigate(Routes.ADMIN) }

        composeRule.onNodeWithText("O aplikaci").performClick()
        assertEquals(Routes.ABOUT, navController.currentBackStackEntry?.destination?.route)
        composeRule.onNodeWithText("Verze 1.2.3").assertIsDisplayed()

        composeRule.onNodeWithText("Zpět do administrace").performClick()
        assertEquals(Routes.ADMIN, navController.currentBackStackEntry?.destination?.route)
    }

    @Test
    fun client_doesNotOfferAbout() {
        setUpNavHost()

        composeRule.onAllNodesWithText("O aplikaci").assertCountEquals(0)
    }
}
