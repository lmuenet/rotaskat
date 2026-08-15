package io.rotaskat.shared.settlement

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.property
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettlementTest {

    /** Nullsummige halbe Punkte, so wie sie ein Abend liefert. */
    private fun arbHalfPoints(): Arb<Map<Int, Long>> = arbitrary { rs ->
        val playerCount = Arb.int(2..6).next(rs)
        val values = (1 until playerCount).map { Arb.long(-4000L..4000L).next(rs) }
        // Der letzte Spieler traegt den Rest, damit die Summe exakt 0 ist.
        val points = values + listOf(-values.sum())
        points.withIndex().associate { (seat, value) -> seat to value }
    }

    // --- Die zentrale Zusicherung ---

    @Test
    fun `Die Zahlungen bringen jeden Spieler exakt auf seinen Saldo`() = property {
        checkAll(2000, arbHalfPoints(), Arb.int(0..50)) { halfPoints, centsPerPoint ->
            val settlement = Settlements.settle(halfPoints, centsPerPoint)

            assertEquals(0L, settlement.balances.sumOf { it.cents }, "Salden muessen sich zu 0 summieren")

            val expected = settlement.balances.associate { it.player to it.cents }
            assertEquals(expected, settlement.netTransfers(), "Zahlungen fuer $halfPoints zu $centsPerPoint ct")

            for (payment in settlement.payments) {
                assertTrue(payment.cents > 0, "Zahlungen sind immer positiv: $payment")
                assertTrue(payment.from != payment.to, "Niemand zahlt an sich selbst: $payment")
            }
        }
    }

    @Test
    fun `Es gibt hoechstens eine Zahlung weniger als Spieler`() = property {
        // Jeder Schritt streicht mindestens einen Spieler aus der Liste, also
        // bleiben nie mehr als n-1 Zahlungen uebrig. Das ist der Punkt des
        // Greedy-Verfahrens: nicht jeder mit jedem.
        checkAll(1000, arbHalfPoints(), Arb.int(1..50)) { halfPoints, centsPerPoint ->
            val settlement = Settlements.settle(halfPoints, centsPerPoint)
            assertTrue(
                settlement.payments.size <= halfPoints.size - 1,
                "${settlement.payments.size} Zahlungen fuer ${halfPoints.size} Spieler",
            )
        }
    }

    @Test
    fun `Ein ungerader Cent-Satz bleibt trotz halber Punkte exakt`() {
        // 23 halbe Punkte sind 11,5 Punkte; bei 5 ct je Punkt waeren das
        // 57,5 Cent. Einzeln gerundet ginge die Summe nicht mehr auf.
        val halfPoints = mapOf(0 to 46L, 1 to -23L, 2 to -23L)
        val settlement = Settlements.settle(halfPoints, centsPerPoint = 5)

        assertEquals(0L, settlement.balances.sumOf { it.cents })
        assertEquals(115L, settlement.balances.first { it.player == 0 }.cents)
        // Die beiden Gegenspieler teilen sich die 115 Cent so, dass kein
        // halber Cent verloren geht.
        assertEquals(
            listOf(-58L, -57L),
            settlement.balances.filter { it.player != 0 }.map { it.cents }.sorted(),
        )
        assertEquals(settlement.balances.associate { it.player to it.cents }, settlement.netTransfers())
    }

    @Test
    fun `Ein gerader Cent-Satz rechnet ohne Rest`() {
        val settlement = Settlements.settle(mapOf(0 to 46L, 1 to -23L, 2 to -23L), centsPerPoint = 10)
        assertEquals(230L, settlement.balances.first { it.player == 0 }.cents)
        assertEquals(-115L, settlement.balances.first { it.player == 1 }.cents)
    }

    // --- Minimale Anzahl Zahlungen ---

    @Test
    fun `Passende Paare werden direkt verrechnet`() {
        val settlement = Settlements.settle(
            mapOf(0 to 200L, 1 to -200L, 2 to 100L, 3 to -100L),
            centsPerPoint = 2,
        )
        // Ohne Verrechnung waeren es vier Zahlungen, mit dem Greedy-Verfahren
        // sind es zwei: groesster Glaeubiger gegen groessten Schuldner.
        assertEquals(2, settlement.payments.size)
        assertTrue(settlement.payments.any { it.from == 1 && it.to == 0 })
        assertTrue(settlement.payments.any { it.from == 3 && it.to == 2 })
    }

    @Test
    fun `Ein Spieler auf null taucht in keiner Zahlung auf`() {
        val settlement = Settlements.settle(mapOf(0 to 100L, 1 to -100L, 2 to 0L), centsPerPoint = 10)
        assertEquals(1, settlement.payments.size)
        assertTrue(settlement.payments.none { it.from == 2 || it.to == 2 })
        assertEquals(0L, settlement.balances.first { it.player == 2 }.cents)
    }

    @Test
    fun `Ohne Punkte gibt es keine Zahlung`() {
        val settlement = Settlements.settle(mapOf(0 to 0L, 1 to 0L, 2 to 0L), centsPerPoint = 10)
        assertTrue(settlement.payments.isEmpty())
    }

    @Test
    fun `Ein nicht nullsummiger Abend wird abgelehnt statt schief abgerechnet`() {
        // Wenn die Summe nicht 0 ist, ist irgendwo eine Runde kaputt. Dann
        // stimmt auch die Zahlungsliste nicht, und still weiterrechnen waere
        // das Schlechteste.
        assertFailsWith<IllegalArgumentException> {
            Settlements.payments(listOf(Balance(0, 100L), Balance(1, -50L)))
        }
    }

    // --- Zusammenspiel mit der Abrechnung ---

    @Test
    fun `Ein kompletter Abend rechnet sich glatt ab`() {
        val rounds = listOf(
            round(0, SuitGame(Suit.CLUBS, 2, Modifiers(hand = true)), won = true),
            round(1, GrandGame(1), won = false),
            round(2, NullGame(NullVariant.NULL), won = true, contra = ContraLevel.KONTRA),
            Round(
                id = "r4",
                seatCount = 3,
                declarerSeat = null,
                sittingOutSeat = null,
                declaration = RamschGame,
                ramsch = RamschResult(loserSeat = 2, cardPoints = 71),
            ),
        )
        val scores = rounds.map { Scoring.score(it) }
        val totals = Settlements.accumulateHalfPoints(scores)

        assertEquals(0L, totals.values.sum(), "Ein Abend ist die Summe nullsummiger Runden")
        assertEquals(setOf(0, 1, 2), totals.keys)

        val settlement = Settlements.settleRounds(scores, centsPerPoint = 10)
        assertEquals(settlement.balances.associate { it.player to it.cents }, settlement.netTransfers())
        assertTrue(settlement.payments.size <= 2)
    }

    @Test
    fun `Die Aufsummierung rechnet in Long`() {
        // Ein einzelner Abend passt in Int, eine Saison nicht zwangslaeufig.
        // Die Nullsummen-Invariante wuerde einen Int-Ueberlauf nicht bemerken,
        // weil sie modulo 2^32 genauso aufgeht.
        val huge = mapOf(0 to Int.MAX_VALUE.toLong(), 1 to -Int.MAX_VALUE.toLong())
        val settlement = Settlements.settle(huge, centsPerPoint = 2)
        assertEquals(Int.MAX_VALUE.toLong(), settlement.balances.first { it.player == 0 }.cents)
        assertEquals(1, settlement.payments.size)
        assertEquals(Int.MAX_VALUE.toLong(), settlement.payments.first().cents)
    }

    @Test
    fun `Ein absurd hoher Punktestand ueberlaeuft nicht still`() {
        assertFailsWith<ArithmeticException> {
            Settlements.balances(mapOf(0 to Long.MAX_VALUE / 2, 1 to -(Long.MAX_VALUE / 2)), centsPerPoint = 50)
        }
    }

    private fun round(
        declarerSeat: Int,
        declaration: Declaration,
        won: Boolean,
        contra: ContraLevel = ContraLevel.NONE,
    ) = Round(
        id = "r$declarerSeat",
        seatCount = 3,
        declarerSeat = declarerSeat,
        sittingOutSeat = null,
        declaration = declaration,
        won = won,
        contra = contra,
    )
}
