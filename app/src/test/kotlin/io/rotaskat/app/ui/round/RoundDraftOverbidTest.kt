package io.rotaskat.app.ui.round

import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ueberreizt heisst: gereizt wurde HOEHER, als das Spiel wert ist.
 *
 * Frueher stand beim Einschalten immer 18 im Reizwert, auch bei einem Spiel
 * von 108 - die Herleitung lautete dann "= 108, ueberreizt auf 18 = 108", und
 * die Runde liess sich so speichern.
 */
class RoundDraftOverbidTest {

    private fun kreuzMitZweiOuvert() = RoundDraft.forNextRound(
        roundId = "r1",
        seatCount = 4,
        dealerSeat = 3,
        config = ScoringConfig(),
    ).copy(declarerSeat = 0, matadors = 2, announcement = Announcement.OUVERT)
        .withGame(GamePick.Colour(Suit.CLUBS))

    @Test
    fun `beim Einschalten steht der kleinste Reizwert ueber dem Spielwert`() {
        val d = kreuzMitZweiOuvert().withOverbid(true)

        assertEquals(108, d.regularGameValue)
        assertEquals(110, d.bid)
    }

    @Test
    fun `angeboten werden nur Reizwerte ueber dem Spielwert`() {
        val d = kreuzMitZweiOuvert().withOverbid(true)

        assertTrue(d.overbidOptions.all { it > 108 })
        assertEquals(110, d.overbidOptions.first())
    }

    @Test
    fun `steigt der Spielwert ueber das Gebot, wird das Gebot nachgezogen`() {
        // Karo mit 1 = 18, gereizt bis 20: eine echte Ueberreizung.
        var d = RoundDraft.forNextRound("r1", 4, 3, ScoringConfig())
            .copy(declarerSeat = 0)
            .withGame(GamePick.Colour(Suit.DIAMONDS))
            .withOverbid(true)
            .copy(bid = 20)
            .withBidAboveValue()
        assertEquals(20, d.bid)

        // Jetzt mit 3 = 36: 20 ist keine Ueberreizung mehr.
        d = d.copy(matadors = 3).withBidAboveValue()
        assertEquals(40, d.bid)
    }

    @Test
    fun `ein Gebot unter dem Spielwert laesst sich nicht speichern`() {
        val d = kreuzMitZweiOuvert().copy(overbid = true, bid = 18)
        assertFalse(d.readyForResult)
    }

    @Test
    fun `Karo mit 1 gereizt bis 20 bleibt wie in SCORING md`() {
        val d = RoundDraft.forNextRound("r1", 4, 3, ScoringConfig())
            .copy(declarerSeat = 0)
            .withGame(GamePick.Colour(Suit.DIAMONDS))
            .withOverbid(true)

        assertEquals(20, d.bid)
        assertTrue(d.readyForResult)
        assertEquals(27, d.gameValue)
        // Ueberreizt ist immer verloren: beide Ausgaenge buchen denselben Verlust.
        assertEquals(d.declarerHalfPoints(won = false), d.declarerHalfPoints(won = true))
    }
}
