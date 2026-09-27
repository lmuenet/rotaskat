package io.rotaskat.app.ui.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SessionTopBarTest {

    @get:Rule
    val compose = createComposeRule()

    private var ended = 0
    private var settlement = 0

    private fun show(open: Boolean, editing: Boolean = false, empty: Boolean = false, loading: Boolean = false) {
        compose.setContent {
            RotaskatTheme {
                SessionTopBar(
                    title = "Sa 14.3. · Runde 9",
                    open = open,
                    editing = editing,
                    empty = empty,
                    loading = loading,
                    onBack = {},
                    onHistory = {},
                    onSettlement = { settlement++ },
                    onChangeDealer = {},
                    onEnd = { ended++ },
                )
            }
        }
    }

    @Test
    fun `Abend beenden liegt im Menue`() {
        show(open = true)
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Geber ändern").assertExists()
        compose.onNodeWithText("Abend beenden …").performClick()
        assertEquals(1, ended)
    }

    @Test
    fun `ein leerer Abend wird verworfen statt beendet`() {
        show(open = true, empty = true)
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Abend verwerfen …").assertExists()
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
    }

    @Test
    fun `beendeter Abend zeigt die Abrechnung direkt`() {
        show(open = false)
        compose.onNodeWithContentDescription("Abrechnung").performClick()
        assertEquals(1, settlement)
        compose.onNodeWithContentDescription("Weitere Optionen").performClick()
        compose.onNodeWithText("Abend beenden …").assertDoesNotExist()
    }

    @Test
    fun `in der Korrektur gibt es kein Menue`() {
        show(open = true, editing = true)
        compose.onNodeWithContentDescription("Weitere Optionen").assertDoesNotExist()
    }

    @Test
    fun `waehrend des Ladens keine Aktionen`() {
        show(open = false, loading = true)
        compose.onNodeWithContentDescription("Abrechnung").assertDoesNotExist()
        compose.onNodeWithContentDescription("Weitere Optionen").assertDoesNotExist()
    }

    @Test
    fun `Titel nennt Datum und naechste Runde`() {
        val state = SessionState(
            Session(id = "s", clubId = TEST_CLUB.id, seatCount = 3, startedAt = T0, scoring = TEST_CLUB.scoring),
            emptyList(),
            emptyMap(),
        )
        assertEquals("Sa 14.3. · Runde 1", sessionTitle(state, editRoundId = null, zone = TimeZone.UTC))
        val closed = state.copy(session = state.session.copy(status = SessionStatus.CLOSED))
        assertEquals("Sa 14.3. · beendet", sessionTitle(closed, editRoundId = null, zone = TimeZone.UTC))
        assertEquals("Runde korrigieren", sessionTitle(state, editRoundId = "fehlt", zone = TimeZone.UTC))
    }
}
