package io.rotaskat.app.ui.eval

import io.rotaskat.app.data.CLUB_ID
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.suitRound
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.scoring.Scoring
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Auswertung rechnet aus denselben Rohdaten wie die Abrechnung. Geprueft
 * wird deshalb nicht, ob sie ueberhaupt Zahlen liefert, sondern ob sie dieselben
 * Regeln anwendet: der Aussetzende spielt nicht mit, ein Tombstone ist nicht
 * passiert, und ueberreizt ist verloren.
 */
class StatisticsTest {

    private val zone = TimeZone.UTC

    @Test
    fun `Punkte, Abende und Runden je Spieler`() {
        val standings = Statistics.standings(listOf(abend), TEST_CLUB.roster)

        assertEquals(listOf("Anna", "Dora", "Carl", "Bert"), standings.map { it.player.displayName })
        val anna = standings.first { it.player.displayName == "Anna" }
        assertEquals(48L, anna.halfPoints)
        assertEquals(1, anna.sessions)
        // Anna gibt in der zweiten Runde und setzt damit aus - sie hat eine
        // Runde gespielt, nicht zwei.
        assertEquals(1, anna.rounds)
        assertEquals(1, anna.soloRounds)
        assertEquals(1, anna.soloWins)

        val bert = standings.first { it.player.displayName == "Bert" }
        assertEquals(-120L, bert.halfPoints)
        assertEquals(2, bert.rounds)
        assertEquals(0, bert.soloWins)
    }

    @Test
    fun `Ein Spieler ohne Alleinspiel hat keine Quote, nicht null Prozent`() {
        val standings = Statistics.standings(listOf(abend), TEST_CLUB.roster)
        assertNull(standings.first { it.player.displayName == "Carl" }.soloWinRate)
    }

    @Test
    fun `Kadermitglieder ohne eine einzige Runde stehen nicht in der Rangliste`() {
        val roster = TEST_CLUB.roster + Player("p9", "Emil")
        val standings = Statistics.standings(listOf(abend), roster)
        assertTrue(standings.none { it.player.displayName == "Emil" })
    }

    @Test
    fun `Ueberreizt zaehlt als verloren, auch wenn die Stiche gereicht haben`() {
        val overbid = Round(
            id = "r-overbid",
            seatCount = 4,
            declarerSeat = 0,
            sittingOutSeat = 3,
            declaration = SuitGame(Suit.DIAMONDS, 1),
            won = true,
            bid = 20,
            overbid = true,
            dealerSeat = 3,
        )
        val state = sessionState("s-overbid", T_MAERZ, listOf(overbid))

        val anna = Statistics.standings(listOf(state), TEST_CLUB.roster)
            .first { it.player.displayName == "Anna" }
        assertEquals(1, anna.soloRounds)
        assertEquals(0, anna.soloWins)
        assertTrue(anna.halfPoints < 0)
    }

    @Test
    fun `Eine geloeschte Runde ist nicht passiert`() {
        val rounds = listOf(
            suitRound("r1", dealerSeat = 3, declarerSeat = 0),
            suitRound("r2", dealerSeat = 0, declarerSeat = 1, won = false),
        )
        val state = sessionState("s-del", T_MAERZ, rounds, deleted = setOf("r2"))

        val anna = Statistics.standings(listOf(state), TEST_CLUB.roster)
            .first { it.player.displayName == "Anna" }
        assertEquals(48L, anna.halfPoints)
        assertEquals(1, anna.rounds)
    }

    @Test
    fun `Die Saison ist das Kalenderjahr des Anpfiffs`() {
        val silvester = sessionState(
            "s-2025",
            Instant.parse("2025-12-31T23:30:00Z"),
            listOf(suitRound("r1", dealerSeat = 3, declarerSeat = 0)),
        )
        val neujahr = sessionState(
            "s-2026",
            Instant.parse("2026-01-01T18:00:00Z"),
            listOf(suitRound("r2", dealerSeat = 3, declarerSeat = 1)),
        )
        val states = listOf(silvester, neujahr)

        assertEquals(listOf(2026, 2025), Statistics.seasons(states, zone))
        assertEquals(
            listOf("s-2025"),
            Statistics.filter(states, Period.Season(2025), zone).map { it.session.id },
        )
        assertEquals(states.size, Statistics.filter(states, Period.AllTime, zone).size)
    }

    @Test
    fun `Der Verlauf beginnt bei null und hat einen Punkt je Runde`() {
        val verlauf = Statistics.cumulativeBySeat(abend)

        assertEquals(4, verlauf.size)
        assertEquals(listOf(0L, 48L, 48L), verlauf.getValue(0))
        assertEquals(listOf(0L, -24L, -120L), verlauf.getValue(1))
        // Der Aussetzende bekommt keine Punkte, seine Linie bleibt waagerecht.
        assertEquals(listOf(0L, 0L, 48L), verlauf.getValue(3))
    }

    @Test
    fun `Das haeufigste Alleinspiel zaehlt nur angesagte Spiele`() {
        val rounds = listOf(
            suitRound("r1", dealerSeat = 3, declarerSeat = 0, suit = Suit.CLUBS),
            suitRound("r2", dealerSeat = 0, declarerSeat = 1),
            suitRound("r3", dealerSeat = 1, declarerSeat = 0, suit = Suit.CLUBS),
            suitRound("r4", dealerSeat = 2, declarerSeat = 0, suit = Suit.HEARTS),
        )
        val anna = Statistics.standings(listOf(sessionState("s", T_MAERZ, rounds)), TEST_CLUB.roster)
            .first { it.player.displayName == "Anna" }

        assertEquals(GameKind.KREUZ, anna.favouriteGame?.key)
        assertEquals(2, anna.favouriteGame?.value)
        assertEquals(3, anna.soloRounds)
    }

    // --- Fixtures ---------------------------------------------------------

    private val T_MAERZ = Instant.parse("2026-03-14T19:30:00Z")

    /** Zwei Runden am Vierertisch, einmal gewonnen und einmal verloren. */
    private val abend: SessionState by lazy {
        sessionState(
            id = "s-maerz",
            startedAt = T_MAERZ,
            rounds = listOf(
                suitRound("r1", dealerSeat = 3, declarerSeat = 0),
                suitRound("r2", dealerSeat = 0, declarerSeat = 1, won = false),
            ),
        )
    }

    private fun sessionState(
        id: String,
        startedAt: Instant,
        rounds: List<Round>,
        deleted: Set<String> = emptySet(),
    ): SessionState {
        val session = Session(
            id = id,
            clubId = CLUB_ID,
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            status = SessionStatus.CLOSED,
            startedAt = startedAt,
            centsPerPoint = 10,
        )
        val scored = rounds.mapIndexed { index, round ->
            ScoredRound(
                id = round.id,
                sequence = index,
                revision = 1,
                round = round,
                score = Scoring.score(round, session.scoring),
                deletedAt = if (round.id in deleted) startedAt else null,
                pendingSync = false,
            )
        }
        val totals = (0 until session.seatCount).associateWith { seat ->
            scored.filter { !it.deleted }.sumOf { (it.score.halfPoints[seat] ?: 0).toLong() }
        }
        return SessionState(session, scored, totals)
    }
}
