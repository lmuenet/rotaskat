package io.rotaskat.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Deaktiviert waren die Ergebnisbuttons im Geraetetest fast unsichtbar - man
 * sah nicht, dass dort gleich etwas kommt.
 */
@RunWith(RobolectricTestRunner::class)
class CommitButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `nicht bereit bleibt sichtbar, zeigt Platzhalter und ist nicht klickbar`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Gewonnen",
                    container = Color.Green,
                    onContainer = Color.White,
                    enabled = false,
                    onClick = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("+ ?").assertIsDisplayed()
        compose.onNodeWithText("Gewonnen").assertIsDisplayed().assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `bereit zeigt die Punkte und speichert`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Gewonnen",
                    container = Color.Green,
                    onContainer = Color.White,
                    enabled = true,
                    detail = "+36",
                    onClick = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("+36").assertIsDisplayed()
        compose.onNodeWithText("Gewonnen").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `in der Korrektur steht der bisherige Ausgang dabei`() {
        compose.setContent {
            RotaskatTheme {
                CommitButton(
                    label = "Verloren",
                    container = Color.Red,
                    onContainer = Color.White,
                    enabled = true,
                    detail = "-72",
                    previous = true,
                    onClick = {},
                )
            }
        }
        compose.onNodeWithText("-72 · bisher").assertIsDisplayed()
    }
}
