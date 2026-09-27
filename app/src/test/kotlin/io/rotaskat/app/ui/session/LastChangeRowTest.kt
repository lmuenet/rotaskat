package io.rotaskat.app.ui.session

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.suitRound
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Nach "Zurueckgenommen" wurde die Zeile ohne Knopf flacher, und das Layout darunter sprang. */
@RunWith(RobolectricTestRunner::class)
class LastChangeRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val state = SessionState(
        Session(id = "s", clubId = TEST_CLUB.id, seatCount = 4, startedAt = T0, scoring = TEST_CLUB.scoring),
        emptyList(),
        emptyMap(),
    )
    private val names = mapOf(0 to "Anna", 1 to "Ben", 2 to "Johannes", 3 to "Lars")
    private val round = suitRound("r-1", dealerSeat = 3, declarerSeat = 2, matadors = 2)

    private fun show(undone: Boolean) {
        val change = LastChange(LastChange.Kind.SAVED, round, UndoToken.Remove(round.id), undone = undone)
        compose.setContent {
            RotaskatTheme { LastChangeRow(change = change, state = state, names = names, onUndo = {}) }
        }
    }

    @Test
    fun `gespeichert hat die feste Hoehe`() {
        show(undone = false)
        compose.onNodeWithTag(LastChangeTags.ROW).assertHeightIsEqualTo(52.dp)
    }

    @Test
    fun `zurueckgenommen hat dieselbe Hoehe`() {
        show(undone = true)
        compose.onNodeWithTag(LastChangeTags.ROW).assertHeightIsEqualTo(52.dp)
    }

    @Test
    fun `gespeichert mit Punkten nennt das Ausgangswort nicht mehr`() {
        // Das Vorzeichen der Punkte sagt schon, ob gewonnen oder verloren
        // wurde; auf einem 411dp-Telefon schnitt "Kre..." sonst das Spiel ab.
        show(undone = false)
        compose.onNodeWithText("Gespeichert: Johannes · Kreuz mit 2").assertIsDisplayed()
    }
}
