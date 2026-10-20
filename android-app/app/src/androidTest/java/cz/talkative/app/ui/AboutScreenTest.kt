package cz.talkative.app.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.talkative.app.R
import cz.talkative.app.domain.pictogram.ArasaacLinks
import cz.talkative.app.ui.about.ABOUT_LOGO_TAG
import cz.talkative.app.ui.about.AboutScreen
import cz.talkative.app.ui.about.aboutLinkTag
import cz.talkative.app.ui.about.versionNameOf
import cz.talkative.app.ui.theme.TalkativeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var backs = 0

    private fun show(versionName: String = "0.1.0") {
        composeRule.setContent {
            TalkativeTheme {
                AboutScreen(versionName = versionName, onBack = { backs++ }, onOpenLink = { opened += it })
            }
        }
    }

    @Test
    fun showsNameVersionDescriptionAndCredit() {
        show("0.1.0")

        composeRule.onNodeWithText("Talkative").assertIsDisplayed()
        composeRule.onNodeWithText("Verze 0.1.0").assertIsDisplayed()
        composeRule.onNodeWithText("Piktogramy ARASAAC").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ABOUT_LOGO_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(EXPECTED_CREDIT).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unknownVersion_isNotShown() {
        show("")

        composeRule.onNodeWithText("Talkative").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasText("Verze", substring = true)).assertCountEquals(0)
    }

    @Test
    fun explainsLicenseAndPrivacy() {
        show()

        composeRule.onNodeWithText("Licence CC BY-NC-SA", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Soukromí a síť").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("odchází při něm jen hledaný text", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun linksAreVisibleAsText_andOpenTheirAddress() {
        show()

        for (url in listOf(ArasaacLinks.SITE, ArasaacLinks.TERMS, ArasaacLinks.LICENSE)) {
            composeRule.onNodeWithText(url).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(aboutLinkTag(url)).performScrollTo().assertHeightIsAtLeast(64.dp).performClick()
        }

        assertEquals(listOf(ArasaacLinks.SITE, ArasaacLinks.TERMS, ArasaacLinks.LICENSE), opened)
    }

    @Test
    fun logo_isALinkToTheArasaacSite() {
        show()

        composeRule.onNodeWithTag(ABOUT_LOGO_TAG).performScrollTo().performClick()

        assertEquals(listOf(ArasaacLinks.SITE), opened)
    }

    @Test
    fun backButton_returns() {
        show()

        composeRule.onNodeWithText("Zpět do administrace").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun creditWording_matchesTheArasaacTermsVerbatim() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(EXPECTED_CREDIT, context.getString(R.string.arasaac_credit))
        assertEquals(EXPECTED_CREDIT_LONG, context.getString(R.string.arasaac_credit_long))
    }

    @Test
    fun versionName_isReadFromThePackage() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(versionNameOf(context).isNotBlank())
    }

    private companion object {
        // Doslovně podle _arasaac/Terms of use.txt; změna textu v aplikaci musí projít přes tento test.
        const val EXPECTED_CREDIT =
            "Autor piktogramů: Sergio Palao. Původ: ARASAAC (http://www.arasaac.org). Licence: CC (BY-NC-SA). Majitel: vláda Aragonu (Španělsko)"
        const val EXPECTED_CREDIT_LONG =
            "Použité piktografické symboly jsou majetkem Aragonské vlády a vytvořil je Sergio Palao pro ARASAAC (http://www.arasaac.org), který je šíří pod Creative Commons License BY-NC-SA."
    }
}
