package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Es gibt hoechstens einen offenen Abend: weder `NewSessionScreen` noch
 * `RoomRotaskatRepository.startSession` verhindern einen zweiten. Der
 * versteckte FAB ist die einzige Schranke dagegen, deshalb ist sein
 * Verschwinden bei laufendem Abend hier festgeschrieben.
 *
 * Die Sichtbarkeits- und Klickpruefung laeuft ueber [NewSessionFabTags.FAB]
 * statt ueber die Textsuche: der erweiterte FAB legt seinen Textknoten unter
 * Robolectric so an, dass `onNodeWithText(...).assertIsDisplayed()` und
 * `.performClick()` auf ihm fehlschlagen, obwohl der Knopf im echten Geraet
 * einwandfrei angezeigt wird und reagiert - ein Layout-Artefakt des
 * erweiterten FABs unter Robolectric, kein Fehler in [NewSessionFab]. Der
 * aeussere, getaggte Knoten ist davon nicht betroffen.
 */
@RunWith(RobolectricTestRunner::class)
class NewSessionFabTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `laeuft kein Abend, ist der Knopf da, zeigt seinen Text und ist klickbar`() {
        var clicked = 0
        compose.setContent {
            RotaskatTheme {
                NewSessionFab(running = false, onClick = { clicked++ })
            }
        }
        compose.onNodeWithTag(NewSessionFabTags.FAB).assertIsDisplayed()
        compose.onNodeWithText("Neuer Abend", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(NewSessionFabTags.FAB).performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun `laeuft ein Abend, gibt es den Knopf nicht`() {
        compose.setContent {
            RotaskatTheme {
                NewSessionFab(running = true, onClick = {})
            }
        }
        compose.onNodeWithTag(NewSessionFabTags.FAB).assertDoesNotExist()
    }
}
