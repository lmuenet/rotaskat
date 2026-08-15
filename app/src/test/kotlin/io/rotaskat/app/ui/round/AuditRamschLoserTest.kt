package io.rotaskat.app.ui.round

import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Der Ramsch-Verlierer folgt den Augen, nicht einer alten Antwort.
 *
 * Die Tie-Break-Frage ist die Antwort auf einen Gleichstand und war frueher ein
 * Vorrang davor: einmal gesetzt, schlug `chosenLoser` die eingetippten Zahlen
 * dauerhaft. Das Ergebnis war nullsummig und damit fuer jede Invariante
 * unsichtbar - und die Herleitung nennt den Verlierer nicht namentlich.
 */
class AuditRamschLoserTest {

    private fun draft() = RoundDraft(
        roundId = "audit",
        seatCount = 3,
        dealerSeat = 0,
        config = ScoringConfig(),
        game = GamePick.Ramsch,
    )

    private fun points(vararg pairs: Pair<Int, Int>) =
        pairs.associate { (seat, value) -> seat to value.toString() }

    @Test
    fun `eine Tie-Break-Antwort ueberlebt die Korrektur der Augen nicht`() {
        // 1. Gleichstand 60:60:0, der Tisch entscheidet sich fuer Platz 1.
        var d = draft().copy(
            ramsch = RamschDraft(cardPoints = points(0 to 60, 1 to 60, 2 to 0), chosenLoser = 1),
        )
        assertEquals(1, d.ramsch.loser(d.activeSeats))

        // 2. Vertippt: es war 70:50:0. Der Gleichstand ist damit weg, das
        //    Tie-Break-Feld verschwindet aus der Oberflaeche (RamschPanel).
        d = d.copy(ramsch = d.ramsch.withPoints(0, "70").withPoints(1, "50"))
        assertEquals(false, d.ramsch.tied(d.activeSeats))
        assertEquals(listOf(0), d.ramsch.leaders(d.activeSeats), "Platz 0 hat die meisten Augen")
        assertNull(d.ramsch.chosenLoser, "Die Antwort gehoert zu den Zahlen, zu denen sie gegeben wurde")

        // 3. Gespeichert wird, was die Augen sagen.
        assertEquals(0, d.ramsch.loser(d.activeSeats))

        val round = d.toRound(won = false)!!
        val ramsch = round.ramsch!!
        assertEquals(0, ramsch.loserSeat)
        assertEquals(70, ramsch.cardPoints)

        val score = Scoring.score(round)
        assertEquals(0, score.sum())
        assertEquals(-140, score.halfPoints.getValue(0), "Platz 0 zahlt fuer seine 70 Augen")
        assertEquals(70, score.halfPoints.getValue(1))
        assertEquals(70, score.halfPoints.getValue(2))
    }

    /**
     * Auch ohne Aenderung der Augen darf die Antwort nur bei einem echten
     * Gleichstand gelten - sonst schluege eine Sitzverschiebung durch.
     */
    @Test
    fun `eine Antwort ausserhalb der Spitzengruppe wird ignoriert`() {
        val d = draft().copy(
            ramsch = RamschDraft(cardPoints = points(0 to 70, 1 to 50, 2 to 0), chosenLoser = 1),
        )
        assertEquals(0, d.ramsch.loser(d.activeSeats))
    }

    @Test
    fun `beim Korrigieren einer Ramsch-Runde laesst sich der Verlierer aendern`() {
        val stored = Round(
            id = "audit-2",
            seatCount = 3,
            declarerSeat = null,
            sittingOutSeat = null,
            declaration = RamschGame,
            ramsch = RamschResult(loserSeat = 1, cardPoints = 50),
        )
        // fromRound belegt keine Tie-Break-Antwort mehr vor: sie wuerde die neu
        // eingetippten Augen dauerhaft schlagen.
        var d = RoundDraft.fromRound(stored, ScoringConfig())
        assertNull(d.ramsch.chosenLoser)
        assertEquals(1, d.ramsch.loser(d.activeSeats), "Ohne weitere Zahlen bleibt es beim gespeicherten Stand")
        assertTrue(
            d.readyForResult,
            "Eine gespeicherte Runde bleibt speicherbar - die Augen der anderen beiden " +
                "standen nie in den Rohdaten",
        )

        // Der Tisch traegt die richtigen Augen nach: Platz 0 hatte 70.
        d = d.copy(ramsch = d.ramsch.withPoints(0, "70").withPoints(1, "50").withPoints(2, "0"))
        assertEquals(false, d.ramsch.tied(d.activeSeats), "kein Gleichstand, also keine Rueckfrage")

        val corrected = d.toRound(won = false)!!
        assertEquals(0, corrected.ramsch!!.loserSeat, "der Verlierer folgt den Augen")
        assertEquals(70, corrected.ramsch!!.cardPoints)
    }
}
