package io.rotaskat.app.ui.common

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Row
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Suit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Die Auswahlkacheln muessen TalkBack sagen, was gewaehlt ist. Im
 * Geraetetest meldeten sie weder "ausgewaehlt" noch eine Rolle.
 */
@RunWith(RobolectricTestRunner::class)
class OptionTileTest {

    @get:Rule
    val compose = createComposeRule()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `gewaehlte Kachel meldet ausgewaehlt und Rolle`() {
        compose.setContent {
            RotaskatTheme {
                Row {
                    OptionTile(label = "Anna", selected = true, onClick = {})
                    OptionTile(label = "Ben", selected = false, onClick = {})
                }
            }
        }
        compose.onNodeWithText("Anna").assertIsSelected().assert(radio)
        compose.onNodeWithText("Ben").assertIsNotSelected().assert(radio)
    }

    @Test
    fun `deaktivierte Kachel ist nicht antippbar`() {
        var clicks = 0
        compose.setContent {
            RotaskatTheme {
                OptionTile(label = "Lars", selected = false, enabled = false, onClick = { clicks++ })
            }
        }
        compose.onNodeWithText("Lars").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `Farbkachel liest den Namen, nicht das Symbol`() {
        var picked: Suit? = null
        compose.setContent {
            RotaskatTheme {
                SuitTile(suit = Suit.CLUBS, selected = true, onClick = { picked = Suit.CLUBS })
            }
        }
        compose.onNodeWithText("Kreuz").assertIsSelected().assert(radio).performClick()
        compose.onAllNodesWithText("♣").assertCountEquals(0)
        assertEquals(Suit.CLUBS, picked)
    }
}
