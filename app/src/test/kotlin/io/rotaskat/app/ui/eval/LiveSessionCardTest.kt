package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Auf der alten Startseite stand die Fuehrungszahl (+50) ohne Namen da; wer
 * fuehrt, musste man unten in der Zeile suchen.
 */
@RunWith(RobolectricTestRunner::class)
class LiveSessionCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val names = mapOf(0 to "Anna", 1 to "Ben", 2 to "Johannes", 3 to "Lars")

    private fun state(totals: Map<Int, Long>) = SessionState(
        Session(
            id = "s",
            clubId = TEST_CLUB.id,
            seatCount = totals.size,
            seats = totals.keys.associateWith { "p$it" },
            startedAt = T0,
            scoring = TEST_CLUB.scoring,
        ),
        emptyList(),
        totals,
    )

    @Test
    fun `die Fuehrung steht mit Namen neben der Zahl`() {
        var continued = 0
        compose.setContent {
            RotaskatTheme {
                LiveSessionCard(
                    state = state(mapOf(0 to 96L, 1 to -24L, 2 to 6L, 3 to 0L)),
                    names = names,
                    onContinue = { continued++ },
                )
            }
        }
        compose.onNodeWithText("Anna führt").assertIsDisplayed()
        compose.onNodeWithText("+48").assertIsDisplayed()
        compose.onNodeWithText("Johannes +3 · Lars +0 · Ben -12").assertIsDisplayed()
        compose.onNodeWithText("Weiterspielen").performClick()
        assertEquals(1, continued)
    }

    @Test
    fun `bei Gleichstand an der Spitze steht kein Name`() {
        assertEquals("Gleichstand", leaderLine(listOf("Anna" to 10L, "Ben" to 10L, "Lars" to -20L)))
        assertEquals("Anna führt", leaderLine(listOf("Anna" to 12L, "Ben" to 10L)))
        assertEquals("Noch keine Runde", leaderLine(listOf("Anna" to 0L, "Ben" to 0L, "Lars" to 0L)))
    }
}
