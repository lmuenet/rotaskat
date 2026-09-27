package io.rotaskat.app.ui.eval

import io.rotaskat.app.data.CLUB_ID
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.suitRound
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.scoring.Scoring
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementShareTest {

    private val names = mapOf(0 to "Anna", 1 to "Bert", 2 to "Carl", 3 to "Dora")

    private fun state(rounds: List<Round>, status: SessionStatus = SessionStatus.CLOSED): SessionState {
        val session = Session(
            id = "s",
            clubId = CLUB_ID,
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            status = status,
            startedAt = T0,
            centsPerPoint = 10,
        )
        val scored = rounds.mapIndexed { index, round ->
            ScoredRound(round.id, index, 1, round, Scoring.score(round, session.scoring), null, false)
        }
        val totals = (0..3).associateWith { seat ->
            scored.sumOf { (it.score.halfPoints[seat] ?: 0).toLong() }
        }
        return SessionState(session, scored, totals)
    }

    /** Bert verliert Kreuz mit 1 (24): er zahlt beiden Gegenspielern je 24 Punkte. */
    @Test
    fun `der Text nennt Stand und Zahlungen wie der Bildschirm`() {
        val text = settlementShareText(
            state(listOf(suitRound("r0", dealerSeat = 0, declarerSeat = 1, won = false))),
            names,
            TimeZone.UTC,
        )

        assertEquals(
            """
            Skat am 14.03.2026 – Endstand nach 1 Runde
            Carl +24, Dora +24, Anna +0, Bert -48

            Zahlungen (10 Cent je Punkt):
            Bert zahlt 2,40 € an Carl
            Bert zahlt 2,40 € an Dora
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `ein laufender Abend ist ein Zwischenstand`() {
        val text = settlementShareText(state(emptyList(), SessionStatus.OPEN), names, TimeZone.UTC)

        assertEquals(
            """
            Skat am 14.03.2026 – Zwischenstand nach 0 Runden
            Anna +0, Bert +0, Carl +0, Dora +0

            Keine Zahlungen – alle stehen auf null.
            """.trimIndent(),
            text,
        )
    }
}
