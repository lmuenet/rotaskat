package io.rotaskat.shared.model

import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableRotationTest {

    @Test
    fun `Am Vierertisch setzt der Geber aus`() {
        val rotation = TableRotation(seatCount = 4, dealerSeat = 2)
        assertEquals(2, rotation.sittingOutSeat)
        assertEquals(listOf(0, 1, 3), rotation.activeSeats)
    }

    @Test
    fun `Am Dreiertisch setzt niemand aus`() {
        val rotation = TableRotation(seatCount = 3, dealerSeat = 2)
        assertNull(rotation.sittingOutSeat)
        assertEquals(listOf(0, 1, 2), rotation.activeSeats)
    }

    @Test
    fun `Das Geben wandert reihum und kommt nach einer Runde zurueck`() {
        for (seatCount in 3..4) {
            var rotation = TableRotation(seatCount, dealerSeat = 0)
            val seen = mutableListOf<Int>()
            repeat(seatCount) {
                seen += rotation.dealerSeat
                rotation = rotation.next()
            }
            // Jeder war genau einmal Geber, danach ist die Stellung wieder da.
            assertEquals((0 until seatCount).toList(), seen)
            assertEquals(TableRotation(seatCount, 0), rotation)
        }
    }

    @Test
    fun `Eine Korrektur am Tisch setzt den Geber direkt`() {
        val rotation = TableRotation(4, dealerSeat = 0).withDealer(3)
        assertEquals(3, rotation.dealerSeat)
        assertEquals(3, rotation.sittingOutSeat)
        assertEquals(0, rotation.next().dealerSeat)
    }

    @Test
    fun `Ein Sitzplatz ausserhalb des Tisches wird abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { TableRotation(4, dealerSeat = 4) }
        assertFailsWith<IllegalArgumentException> { TableRotation(4, dealerSeat = -1) }
        assertFailsWith<IllegalArgumentException> { TableRotation(2, dealerSeat = 0) }
    }

    @Test
    fun `Eine Runde kennt ihre eigene Sitzordnung`() {
        val round = Round(
            id = "r1",
            seatCount = 4,
            declarerSeat = 0,
            sittingOutSeat = 1,
            declaration = GrandGame(matadors = 1),
        )
        // dealerSeat folgt per Vorgabe dem Aussetzenden, damit Rohdaten ohne
        // das Feld nicht ploetzlich an einer anderen Stellung weiterrotieren.
        assertEquals(1, round.dealerSeat)
        assertEquals(TableRotation(4, 1), round.rotation())
        assertEquals(2, round.rotation().next().dealerSeat)
        assertTrue(Scoring.validate(round).isEmpty())
    }

    @Test
    fun `Ein Geber der nicht aussetzt ist am Vierertisch ungueltig`() {
        val round = Round(
            id = "r2",
            seatCount = 4,
            declarerSeat = 0,
            sittingOutSeat = 3,
            declaration = GrandGame(matadors = 1),
            dealerSeat = 2,
        )
        assertTrue(Scoring.validate(round).any { it.contains("Geber") })
    }
}

class ClubAndSessionTest {

    private val start = Instant.parse("2026-01-09T19:30:00Z")

    private val club = Club(
        id = "club-1",
        name = "Skatrunde Hinterzimmer",
        scoring = ScoringConfig(pushDoubles = false),
        centsPerPoint = 5,
        roster = listOf(Player("p1", "Anna"), Player("p2", "Bert"), Player("p3", "Cem")),
    )

    @Test
    fun `Eine Session friert die Hausregeln des Vereins ein`() {
        val session = Session.startedFor(
            id = "s1",
            club = club,
            seatCount = 3,
            seats = mapOf(0 to "p1", 1 to "p2", 2 to "p3"),
            startedAt = start,
        )

        assertEquals(club.scoring, session.scoring)
        assertEquals(5, session.centsPerPoint)
        assertEquals(Scoring.SCORING_VERSION, session.scoringVersion)

        // Aendert der Verein spaeter seine Regeln, bleibt der Abend, wie er war.
        val changed = club.copy(scoring = ScoringConfig(pushDoubles = true), centsPerPoint = 20)
        assertEquals(ScoringConfig(pushDoubles = false), session.scoring)
        assertEquals(5, session.centsPerPoint)
        assertTrue(changed.centsPerPoint != session.centsPerPoint)
    }

    @Test
    fun `Die Session schreibt den Geber fort`() {
        val session = Session.startedFor("s1", club, seatCount = 4, seats = emptyMap(), startedAt = start)
        assertEquals(0, session.dealerSeat)
        assertEquals(1, session.withNextDealer().dealerSeat)
        assertEquals(0, session.withNextDealer().withNextDealer().withNextDealer().withNextDealer().dealerSeat)
    }

    @Test
    fun `Ein negativer Cent-Satz wird abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { club.copy(centsPerPoint = -1) }
    }
}
