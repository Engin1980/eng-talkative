package cz.talkative.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.ui.arasaac.ARASAAC_QUERY_ARG
import cz.talkative.app.ui.navigation.Routes
import cz.talkative.app.ui.navigation.TalkativeNavHost
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cesta administrace → výběr piktogramu → zpět s výsledkem, jen s pahýly obrazovek. */
@RunWith(AndroidJUnit4::class)
class PictogramNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    private fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            TalkativeTheme {
                TalkativeNavHost(
                    navController = navController,
                    client = {},
                    admin = { navigation ->
                        Column {
                            Text("ADMINISTRACE převzato: ${navigation.pickedImageUri}")
                            Button(onClick = { navigation.openArasaac("dům a zahrada") }) { Text("Najít piktogram") }
                            Button(onClick = navigation.onPickedImageConsumed) { Text("Převzít") }
                        }
                    },
                    arasaac = { onPicked, openAbout, back ->
                        val query = navController.currentBackStackEntry?.arguments?.getString(ARASAAC_QUERY_ARG)
                        Column {
                            Text("VÝBĚR hledané: $query")
                            Button(onClick = { onPicked("file:///cache/pictograms/p.png") }) { Text("Vybrat") }
                            Button(onClick = openAbout) { Text("Licence") }
                            Button(onClick = back) { Text("Zpět bez výběru") }
                        }
                    },
                    about = { back -> Button(onClick = back) { Text("O APLIKACI zpět") } },
                )
            }
        }
        composeRule.runOnUiThread { navController.navigate(Routes.ADMIN) }
    }

    private fun route() = navController.currentBackStackEntry?.destination?.route

    @Test
    fun adminOpensThePicker_withTheTitleAsQuery() {
        setUp()

        composeRule.onNodeWithText("Najít piktogram").performClick()

        assertEquals(Routes.ARASAAC, route())
        composeRule.onNodeWithText("VÝBĚR hledané: dům a zahrada").assertIsDisplayed() // mezery a diakritika přežijí trasu
    }

    @Test
    fun pickedPictogram_isReturnedToAdmin_andThePickerCloses() {
        setUp()
        composeRule.onNodeWithText("Najít piktogram").performClick()

        composeRule.onNodeWithText("Vybrat").performClick()

        assertEquals(Routes.ADMIN, route())
        composeRule.onNodeWithText("ADMINISTRACE převzato: file:///cache/pictograms/p.png").assertIsDisplayed()
    }

    @Test
    fun resultIsConsumedOnce_soItIsNotProcessedAgain() {
        setUp()
        composeRule.onNodeWithText("Najít piktogram").performClick()
        composeRule.onNodeWithText("Vybrat").performClick()

        composeRule.onNodeWithText("Převzít").performClick()

        composeRule.onNodeWithText("ADMINISTRACE převzato: null").assertIsDisplayed()
        assertNull(navController.getBackStackEntry(Routes.ADMIN).savedStateHandle.get<String>(Routes.PICKED_IMAGE_KEY))
    }

    @Test
    fun backWithoutPicking_changesNothing() {
        setUp()
        composeRule.onNodeWithText("Najít piktogram").performClick()

        composeRule.onNodeWithText("Zpět bez výběru").performClick()

        assertEquals(Routes.ADMIN, route())
        composeRule.onNodeWithText("ADMINISTRACE převzato: null").assertIsDisplayed()
    }

    @Test
    fun licenseLink_opensAbout_andBackReturnsToThePicker() {
        setUp()
        composeRule.onNodeWithText("Najít piktogram").performClick()

        composeRule.onNodeWithText("Licence").performClick()
        assertEquals(Routes.ABOUT, route())
        composeRule.onNodeWithText("O APLIKACI zpět").performClick()

        assertEquals(Routes.ARASAAC, route())
        composeRule.onNodeWithText("VÝBĚR hledané: dům a zahrada").assertIsDisplayed()
    }
}
