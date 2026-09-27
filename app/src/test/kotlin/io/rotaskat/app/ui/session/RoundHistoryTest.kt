package io.rotaskat.app.ui.session

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.suitRound
import io.rotaskat.app.ui.round.RoundDraft
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.scoring.Scoring
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Die Rundenliste des Abends. Zurueckgenommene Runden standen frueher als
 * "4 · Herz mit 1 · Anna - gewonnen - geloescht" in der Liste, und die
 * Nummern bekamen Luecken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class RoundHistoryTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = Session(
        id = "s",
        clubId = TEST_CLUB.id,
        seatCount = 4,
        seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
        startedAt = T0,
        scoring = TEST_CLUB.scoring,
        dealerSeat = 3,
    )

    private fun scored(round: Round, sequence: Int, deleted: Boolean = false) = ScoredRound(
        id = round.id,
        sequence = sequence,
        revision = 1,
        round = round,
        score = Scoring.score(round, session.scoring),
        deletedAt = if (deleted) T0 else null,
        pendingSync = false,
    )

    @Test
    fun `geloeschte Runden fehlen und die Nummern bleiben lueckenlos`() {
        val rounds = listOf(
            scored(suitRound("r0", dealerSeat = 0, declarerSeat = 1), 0),
            scored(suitRound("r1", dealerSeat = 1, declarerSeat = 2), 1, deleted = true),
            scored(
                suitRound("r2", dealerSeat = 2, declarerSeat = 0, won = false)
                    .copy(overbid = true, bid = 20),
                2,
            ),
        )
        show(SessionState(session, rounds, (0..3).associateWith { 0L }))

        compose.onNodeWithText("Runden (2)").performScrollTo()
        compose.onNodeWithText("Anna · überreizt, gereizt bis 20").performScrollTo()
        assertEquals(0, compose.onAllNodesWithText("Carl ·", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("3").fetchSemanticsNodes().size, "Es gibt keine Runde 3")
    }

    private fun show(state: SessionState) {
        compose.setContent {
            RotaskatTheme {
                SessionBody(
                    state = state,
                    draft = RoundDraft.forNextRound("next", 4, 3, TEST_CLUB.scoring),
                    names = TEST_CLUB.roster.withIndex().associate { (seat, player) -> seat to player.displayName },
                    editing = false,
                    scrollState = rememberScrollState(),
                    onDraftChange = {},
                    onDealerChange = {},
                    onCommit = {},
                    onEditRound = {},
                    onCancelEdit = {},
                    onDelete = {},
                )
            }
        }
    }
}
