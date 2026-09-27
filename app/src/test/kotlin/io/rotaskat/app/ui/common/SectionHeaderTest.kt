package io.rotaskat.app.ui.common

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Erklaerungen stehen nicht mehr dauerhaft ueber der Tabelle, sondern hinter dem ⓘ. */
@RunWith(RobolectricTestRunner::class)
class SectionHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Info-Symbol oeffnet die Erklaerung`() {
        compose.setContent { RotaskatTheme { SectionHeader(title = "Punkte", info = "Die Quote zählt nur Alleinspiele.") } }
        compose.onNodeWithText("Die Quote zählt nur Alleinspiele.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Erklärung zu Punkte").performClick()
        compose.onNodeWithText("Die Quote zählt nur Alleinspiele.").assertIsDisplayed()
    }

    @Test
    fun `ohne Erklaerung kein Info-Symbol`() {
        compose.setContent { RotaskatTheme { SectionHeader(title = "Stand") } }
        compose.onNodeWithContentDescription("Erklärung zu Stand").assertDoesNotExist()
    }

    @Test
    fun `Info-Symbol erreicht das Tap-Ziel`() {
        compose.setContent { RotaskatTheme { SectionHeader(title = "Punkte", info = "Die Quote zählt nur Alleinspiele.") } }
        compose.onNodeWithContentDescription("Erklärung zu Punkte")
            .assertWidthIsAtLeast(56.dp)
            .assertHeightIsAtLeast(56.dp)
    }
}
