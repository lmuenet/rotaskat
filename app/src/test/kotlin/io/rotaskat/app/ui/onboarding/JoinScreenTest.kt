package io.rotaskat.app.ui.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Die Spielerzeile beim Beitritt im Kartentisch-Look: eine Flaeche mit Rand,
 * ein TalkBack-Fokusstopp statt zwei (Zeile und Radiobutton getrennt).
 */
@RunWith(RobolectricTestRunner::class)
class JoinScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Spielerzeile ist ein Fokusstopp und meldet die Auswahl`() {
        compose.setContent {
            RotaskatTheme {
                PlayerPickRow(label = "Anna", selected = true, onClick = {})
            }
        }
        compose.onNodeWithText("Anna").assertIsSelected()
        // Genau eine antippbare Flaeche im (unzusammengefuehrten) Baum - der
        // Radiobutton meldet keinen eigenen Klick mehr, sonst waeren es fuer
        // TalkBack zwei Fokusstopps statt einem.
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `nicht gewaehlte Spielerzeile meldet das auch`() {
        compose.setContent {
            RotaskatTheme {
                PlayerPickRow(label = "Ben", selected = false, onClick = {})
            }
        }
        compose.onNodeWithText("Ben").assertIsNotSelected()
    }
}
