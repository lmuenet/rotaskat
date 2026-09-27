package io.rotaskat.app.ui.eval

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class StatTileTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Kachel zeigt Label, Wert, Detail und Warnung`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Gewinnquote allein", value = "100 %", detail = "1 von 1", warning = "Unter 5 Alleinspielen wenig aussagekräftig")
            }
        }
        compose.onNodeWithText("Gewinnquote allein").assertIsDisplayed()
        compose.onNodeWithText("100 %").assertIsDisplayed()
        compose.onNodeWithText("1 von 1").assertIsDisplayed()
        compose.onNodeWithText("Unter 5 Alleinspielen wenig aussagekräftig").assertIsDisplayed()
    }

    @Test
    fun `Kachel ist ein Fokusstopp und enthaelt Label und Wert`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Punkte", value = "+42")
            }
        }
        val merged = compose.onNodeWithText("Punkte").fetchSemanticsNode()
        val texts = merged.config.getOrNull(SemanticsProperties.Text)?.map { it.text }
        assertTrue(texts?.contains("Punkte") == true)
        assertTrue(texts?.contains("+42") == true)
    }

    @Test
    fun `Wert Gedankenstrich meldet keine Angabe`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Ø je Runde", value = "–")
            }
        }
        compose.onNodeWithContentDescription("keine Angabe").assertIsDisplayed()
    }

    @Test
    fun `labelDescription ueberschreibt die Vorlesung des Labels`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Ø je Runde", value = "+3,2", labelDescription = "Durchschnitt je Runde")
            }
        }
        compose.onNodeWithContentDescription("Durchschnitt je Runde").assertIsDisplayed()
    }
}
