package io.rotaskat.shared.scoring

import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Die Ueberlaufpfade, die ein Audit gefunden hat.
 *
 * Vorher liefen sie alle durch und lieferten ein still falsches, aber
 * nullsummiges Ergebnis - genau die Sorte Fehler, gegen die weder
 * [RoundScore.sum] noch der Nullsummen-Trigger der Datenbank etwas ausrichten.
 * Die Tests halten fest, dass jeder dieser Pfade jetzt vorher abbricht.
 */
class AuditOverflowTest {

    private fun round(declaration: io.rotaskat.shared.model.Declaration, won: Boolean = true) = Round(
        id = "audit",
        seatCount = 3,
        declarerSeat = 0,
        sittingOutSeat = null,
        declaration = declaration,
        won = won,
    )

    @Test
    fun `eine absurde Spitzenzahl wird abgelehnt statt uebergelaufen abgerechnet`() {
        val r = round(SuitGame(Suit.CLUBS, matadors = 200_000_000), won = true)

        val errors = Scoring.validate(r)
        assertEquals(1, errors.size, "genau ein Befund erwartet, war: $errors")
        assertTrue(errors.single().startsWith("Spitzenzahl"), errors.single())

        // Und die Abrechnung rechnet so eine Runde gar nicht erst.
        assertFailsWith<IllegalArgumentException> { Scoring.score(r) }
    }

    @Test
    fun `auch der Grand kommt nicht mehr ueber seine vier Buben hinaus`() {
        val r = round(GrandGame(matadors = 100_000_000), won = false)
        assertTrue(Scoring.validate(r).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { Scoring.score(r) }

        // Die Grenze ist keine Rundungsfrage, sondern die Spielregel.
        assertEquals(emptyList(), Scoring.validate(round(GrandGame(matadors = 4))))
        assertTrue(Scoring.validate(round(GrandGame(matadors = 5))).isNotEmpty())
    }

    @Test
    fun `unmoegliche Spitzenzahlen sind keine gueltigen Runden mehr`() {
        // Ein Grand hat hoechstens vier Spitzen, ein Farbspiel hoechstens elf.
        assertTrue(Scoring.validate(round(GrandGame(matadors = 11))).isNotEmpty())
        assertTrue(Scoring.validate(round(SuitGame(Suit.CLUBS, matadors = 12))).isNotEmpty())
        assertEquals(emptyList(), Scoring.validate(round(SuitGame(Suit.CLUBS, matadors = 11))))

        // Damit stimmt auch die Begruendung von MAX_BID wieder: der hoechste
        // ueberhaupt erreichbare Spielwert ist Grand ouvert mit 4.
        val hoechster = Scoring.gameValue(GrandGame(matadors = 4, modifiers = Modifiers(ouvert = true)))
        assertEquals(Scoring.MAX_BID, hoechster)
    }

    @Test
    fun `ein masslose durchmarschValue bricht ab statt ins Negative zu kippen`() {
        val r = Round(
            id = "audit-dm",
            seatCount = 3,
            declarerSeat = null,
            sittingOutSeat = null,
            declaration = RamschGame,
            ramsch = RamschResult(loserSeat = 0, cardPoints = 0, durchmarschSeat = 1),
        )
        assertEquals(emptyList(), Scoring.validate(r))
        assertFailsWith<IllegalArgumentException> {
            Scoring.score(r, ScoringConfig(durchmarschValue = Int.MAX_VALUE))
        }
        // Der reale Wert bleibt selbstverstaendlich abrechenbar.
        assertEquals(120, Scoring.score(r, ScoringConfig()).gameValue)
    }

    @Test
    fun `Ramsch mit Jungfrau und fuenf Schueben bleibt im Rahmen`() {
        val r = Round(
            id = "audit-ramsch",
            seatCount = 3,
            declarerSeat = null,
            sittingOutSeat = null,
            declaration = RamschGame,
            ramsch = RamschResult(loserSeat = 0, cardPoints = 120, jungfrau = true, pushes = 5),
        )
        val score = Scoring.score(r)
        assertEquals(120 * 2 * 32, score.gameValue)
        assertEquals(0, score.sum())
    }
}
