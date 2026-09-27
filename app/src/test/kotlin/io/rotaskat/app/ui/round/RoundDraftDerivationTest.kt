package io.rotaskat.app.ui.round

import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Suit
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Die Herleitung neben dem Spielwert. Bei Ouvert wurde sie im Geraetetest nach
 * drei Zeilen abgeschnitten - ausgerechnet "= 12 x 9 = 108" fehlte. Die
 * mitgesetzten Stufen stehen bei den Zusaetzen, hier steht nur die Ansage.
 */
class RoundDraftDerivationTest {

    private fun draft(game: GamePick, matadors: Int) = RoundDraft.forNextRound(
        roundId = "r0",
        seatCount = 3,
        dealerSeat = 0,
        config = TEST_CLUB.scoring,
    ).copy(declarerSeat = 1).withGame(game).copy(matadors = matadors)

    @Test
    fun `Farbspiel ohne Zusaetze`() {
        assertEquals("Kreuz mit 2 = 12 × 3", draft(GamePick.Colour(Suit.CLUBS), 2).derivation())
    }

    @Test
    fun `Ouvert nennt nur die Ansage`() {
        val d = draft(GamePick.Colour(Suit.CLUBS), 2).copy(announcement = Announcement.OUVERT)
        assertEquals("Kreuz mit 2 · Ouvert = 12 × 9", d.derivation())
    }

    @Test
    fun `Hand mit erreichtem Schneider nennt beides`() {
        val d = draft(GamePick.Colour(Suit.SPADES), 1)
            .copy(announcement = Announcement.HAND, achieved = Achieved.SCHNEIDER)
        assertEquals("Pik mit 1 · Hand · Schneider = 11 × 4", d.derivation())
    }

    @Test
    fun `Kontra haengt den Faktor und das Ergebnis an`() {
        val d = draft(GamePick.Colour(Suit.CLUBS), 2).copy(contra = ContraLevel.KONTRA)
        assertEquals("Kreuz mit 2 = 12 × 3 × 2 (Kontra) = 72", d.derivation())
    }

    @Test
    fun `Grand`() {
        assertEquals("Grand mit 1 = 24 × 2", draft(GamePick.Grand, 1).derivation())
    }
}
