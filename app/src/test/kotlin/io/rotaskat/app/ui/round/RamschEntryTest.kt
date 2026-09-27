package io.rotaskat.app.ui.round

import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Ramsch-Eingabe nach dem ersten Geraetetest: 60/40/30 liess sich trotz
 * "Summe 130 - bitte nachzaehlen" speichern, und nach der ersten Zahl stand
 * schon "60 Augen = 60" da, obwohl noch niemand wusste, wer verliert.
 */
class RamschEntryTest {

    private fun draft() = RoundDraft(
        roundId = "r",
        seatCount = 3,
        dealerSeat = 0,
        config = ScoringConfig(),
        game = GamePick.Ramsch,
    )

    @Test
    fun `das letzte Feld ergibt sich aus den beiden anderen`() {
        val d = draft().withRamschPoints(0, "60").withRamschPoints(1, "40")

        assertEquals("20", d.ramsch.cardPoints[2])
        assertEquals(2, d.ramsch.autoSeat)
        assertTrue(d.readyForResult)
        assertEquals(60, d.gameValue)
    }

    /** Beim Tippen von "60" kommt erst die "6" an - die Ergaenzung muss nachziehen. */
    @Test
    fun `das berechnete Feld folgt beim Weitertippen`() {
        var d = draft().withRamschPoints(0, "60").withRamschPoints(1, "4")
        assertEquals("56", d.ramsch.cardPoints[2])

        d = d.withRamschPoints(1, "40")
        assertEquals("20", d.ramsch.cardPoints[2])
    }

    @Test
    fun `wer das berechnete Feld selbst aendert, hat es uebernommen`() {
        val d = draft().withRamschPoints(0, "60").withRamschPoints(1, "40").withRamschPoints(2, "30")

        assertNull(d.ramsch.autoSeat)
        assertEquals(130, d.ramsch.total(d.activeSeats))
        assertFalse(d.readyForResult, "Summe 130 statt 120 darf nicht gespeichert werden")
    }

    @Test
    fun `wird ein Feld wieder geleert, verschwindet die Ergaenzung`() {
        val d = draft().withRamschPoints(0, "60").withRamschPoints(1, "40").withRamschPoints(1, "")

        assertNull(d.ramsch.autoSeat)
        assertFalse(d.ramsch.entered(2))
    }

    @Test
    fun `mehr als 120 Augen in zwei Feldern ergaenzen nichts`() {
        val d = draft().withRamschPoints(0, "90").withRamschPoints(1, "40")

        assertFalse(d.ramsch.entered(2))
        assertFalse(d.readyForResult)
    }

    @Test
    fun `ohne alle Augen gibt es noch keinen Wert`() {
        val d = draft().withRamschPoints(0, "60")

        assertNull(d.displayedGameValue)
        assertEquals("Augen fehlen noch", d.derivation())
    }
}
