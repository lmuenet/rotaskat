package io.rotaskat.app.ui.round

import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Rundeneingabe gegen das Tap-Budget und gegen die Zusage, dass ungueltige
 * Kombinationen strukturell unerreichbar sind.
 */
class RoundDraftAuditTest {

    private fun draft() = RoundDraft.forNextRound(
        roundId = "r1",
        seatCount = 4,
        dealerSeat = 3,
        config = ScoringConfig(),
    )

    /** Der Standardfall: gewonnenes Farbspiel ohne Ansage. */
    @Test
    fun standardfallBrauchtVierTaps() {
        var d = draft()
        d = d.copy(declarerSeat = 0)          // Tap 1
        d = d.withGame(GamePick.Colour(Suit.CLUBS)) // Tap 2
        d = d.copy(matadors = 2)              // Tap 3
        assertTrue(d.readyForResult)          // Tap 4 = Gewonnen
        assertEquals(36, d.gameValue)
    }

    /**
     * "Ueberreizt" ueberlebt den Wechsel auf Null nicht.
     *
     * Frueher blieb der Schalter gesetzt, obwohl die Oberflaeche ihn beim
     * Nullspiel gar nicht mehr zeigt: ein getipptes "Gewonnen" buchte dann
     * einen Verlust, ohne dass irgendwo etwas von "ueberreizt" stand.
     */
    @Test
    fun ueberreiztUeberlebtDenWechselAufNullNicht() {
        var d = draft().copy(declarerSeat = 0).withGame(GamePick.Colour(Suit.DIAMONDS))
        d = d.copy(overbid = true, bid = 27)
        d = d.withGame(GamePick.Null).copy(nullVariant = NullVariant.NULL)

        assertFalse(d.overbid, "Der Wechsel raeumt den Schalter weg")
        assertEquals(Scoring.MIN_BID, d.bid)

        val round = assertNotNull(d.toRound(won = true))
        assertEquals(false, round.overbid)
        assertEquals(null, round.bid)
        assertEquals(emptyList(), Scoring.validate(round))

        val score = Scoring.score(round, ScoringConfig())
        assertEquals(2 * 23, score.halfPoints[0], "Gewonnen getippt heisst gewonnen gebucht")
        assertEquals(0, d.extrasCount)
        assertEquals("Null = 23", d.derivation())
    }

    /**
     * Selbst wenn das Feld auf anderem Weg gesetzt waere, darf es beim
     * Nullspiel nicht mehr wirken - die Invariante haengt nicht am UI-Zweig.
     */
    @Test
    fun ueberreiztWirktBeimNullspielNicht() {
        val d = draft().copy(
            declarerSeat = 0,
            game = GamePick.Null,
            nullVariant = NullVariant.NULL,
            overbid = true,
            bid = 27,
        )
        val round = assertNotNull(d.toRound(won = true))
        assertEquals(false, round.overbid)
        assertEquals(2 * 23, Scoring.score(round, ScoringConfig()).halfPoints[0])
    }

    /** Beim Grand gibt es vier Buben und damit vier Spitzen-Kacheln. */
    @Test
    fun grandBietetNurVierSpitzenAn() {
        val d = draft().copy(declarerSeat = 0).withGame(GamePick.Grand)
        assertEquals(listOf(1, 2, 3, 4), d.matadorOptions)

        val hoechster = d.copy(matadors = 4)
        assertEquals(24 * 5, hoechster.gameValue)
        assertTrue(hoechster.gameValue!! <= Scoring.MAX_BID)

        // Und die Grenze steht auch in der Abrechnung, nicht nur in der Kachelreihe.
        val unmoeglich = d.copy(matadors = 7)
        assertTrue(Scoring.validate(assertNotNull(unmoeglich.toRound(won = true))).isNotEmpty())
        assertEquals(null, unmoeglich.gameValue, "Ohne gueltige Runde gibt es keinen Spielwert")
        assertFalse(unmoeglich.readyForResult)
    }

    /** Spitzen wandern beim Wechsel der Spielart nicht ueber ihre Grenze hinaus. */
    @Test
    fun spitzenWerdenBeimWechselAufGrandGekappt() {
        val d = draft().copy(declarerSeat = 0).withGame(GamePick.Colour(Suit.CLUBS)).copy(matadors = 7)
        assertEquals(12 * 8, d.gameValue)

        val grand = d.withGame(GamePick.Grand)
        assertEquals(4, grand.matadors, "7 gibt es beim Grand nicht")
        assertEquals(24 * 5, grand.gameValue)

        // Zurueck auf die Farbe bleibt die gekappte Zahl stehen - erfunden wird
        // nichts, korrigiert wird mit einem Tap.
        assertEquals(4, grand.withGame(GamePick.Colour(Suit.CLUBS)).matadors)
    }

    /** Der Verlierer eines Ramsch folgt den Augen. */
    @Test
    fun ramschVerliererFolgtDenAugen() {
        var d = draft().withGame(GamePick.Ramsch)
        // Gleichstand -> der Tisch entscheidet, Platz 0 gilt als Verlierer.
        d = d.copy(
            ramsch = d.ramsch.copy(cardPoints = mapOf(0 to "40", 1 to "40", 2 to "40")),
        )
        assertTrue(d.ramsch.tied(d.activeSeats))
        d = d.copy(ramsch = d.ramsch.copy(chosenLoser = 0))
        assertEquals(0, d.ramsch.loser(d.activeSeats))

        // Nachgezaehlt: Platz 1 hatte 60, Platz 0 nur 20.
        d = d.copy(ramsch = d.ramsch.withPoints(0, "20").withPoints(1, "60"))
        assertEquals(listOf(1), d.ramsch.leaders(d.activeSeats))

        val round = assertNotNull(d.toRound(won = false))
        assertEquals(RamschGame, round.declaration)
        assertEquals(1, round.ramsch!!.loserSeat)
        assertEquals(60, round.ramsch!!.cardPoints)
        assertEquals(emptyList(), Scoring.validate(round))
    }

    /** Ein Ramsch mit nur einer eingetippten Augenzahl ist nicht speicherbar. */
    @Test
    fun ramschBrauchtAlleAugen() {
        var d = draft().withGame(GamePick.Ramsch).copy(
            ramsch = RamschDraft(cardPoints = mapOf(0 to "82")),
        )
        assertFalse(d.readyForResult, "Ohne die anderen beiden Zahlen gibt es weder Summenprobe noch Gleichstand")
        assertEquals(listOf(1, 2), d.missingRamschSeats)

        d = d.copy(ramsch = d.ramsch.withPoints(1, "30").withPoints(2, "8"))
        assertTrue(d.readyForResult)
        assertEquals(120, d.ramsch.total(d.activeSeats))
        assertEquals(82, assertNotNull(d.toRound(won = false)).ramsch!!.cardPoints)
    }

    /** Der Durchmarsch braucht keine Augen - dort gibt es nichts zu zaehlen. */
    @Test
    fun durchmarschBrauchtKeineAugen() {
        val d = draft().withGame(GamePick.Ramsch).copy(
            ramsch = RamschDraft(durchmarschSeat = 0),
        )
        assertTrue(d.readyForResult)
        assertEquals(120, d.gameValue)
    }
}
