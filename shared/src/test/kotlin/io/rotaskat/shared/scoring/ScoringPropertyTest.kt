package io.rotaskat.shared.scoring

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.property
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Die Eigenschaften, die fuer JEDE Runde gelten muessen. Einzelbeispiele
 * stehen in [ScoringTest]; hier steht, was der Raum als Ganzes erfuellt.
 */
class ScoringPropertyTest {

    private val declarations: List<Declaration> = GameValueTable.allDeclarations()

    private fun arbRound(): Arb<Round> = arbitrary { rs ->
        val seatCount = Arb.of(3, 4).next(rs)
        val dealerSeat = Arb.int(0 until seatCount).next(rs)
        // Am Vierertisch sitzt der Geber aus, am Dreiertisch spielen alle mit.
        val sittingOutSeat = if (seatCount == 4) dealerSeat else null
        val activeSeats = (0 until seatCount).filter { it != sittingOutSeat }

        val declaration = Arb.of(declarations + RamschGame).next(rs)
        val bid = if (Arb.boolean().next(rs)) Arb.int(Scoring.MIN_BID..Scoring.MAX_BID).next(rs) else null

        if (declaration is RamschGame) {
            Round(
                id = "prop-ramsch",
                seatCount = seatCount,
                declarerSeat = null,
                sittingOutSeat = sittingOutSeat,
                declaration = declaration,
                dealerSeat = dealerSeat,
                ramsch = RamschResult(
                    loserSeat = Arb.of(activeSeats).next(rs),
                    cardPoints = Arb.int(0..Scoring.MAX_CARD_POINTS).next(rs),
                    jungfrau = Arb.boolean().next(rs),
                    durchmarschSeat = if (Arb.boolean().next(rs)) Arb.of(activeSeats).next(rs) else null,
                    pushes = Arb.int(0..Scoring.MAX_PUSHES).next(rs),
                ),
            )
        } else {
            Round(
                id = "prop",
                seatCount = seatCount,
                declarerSeat = Arb.of(activeSeats).next(rs),
                sittingOutSeat = sittingOutSeat,
                declaration = declaration,
                won = Arb.boolean().next(rs),
                contra = Arb.of(ContraLevel.entries).next(rs),
                bid = bid,
                overbid = bid != null && Arb.boolean().next(rs),
                dealerSeat = dealerSeat,
            )
        }
    }

    /** Dreht die gesamte Sitzordnung um [shift] Plaetze weiter. */
    private fun Round.rotatedBy(shift: Int): Round {
        fun rotate(seat: Int) = Math.floorMod(seat + shift, seatCount)
        return copy(
            declarerSeat = declarerSeat?.let(::rotate),
            sittingOutSeat = sittingOutSeat?.let(::rotate),
            dealerSeat = rotate(dealerSeat),
            ramsch = ramsch?.copy(
                loserSeat = rotate(ramsch.loserSeat),
                durchmarschSeat = ramsch.durchmarschSeat?.let(::rotate),
            ),
        )
    }

    @Test
    fun `Sitzplatz-Rotation dreht die Punkte exakt mit`() = property {
        // Der wichtigste Test am Vierertisch: wer aussetzt, haengt am Sitzplatz,
        // und genau dort entstehen Off-by-one-Fehler. Wird der ganze Tisch
        // gedreht, darf sich am Ergebnis nichts aendern ausser den Sitznummern.
        checkAll(2000, arbRound(), Arb.int(-5..5)) { round, shift ->
            val original = Scoring.score(round)
            val rotated = Scoring.score(round.rotatedBy(shift))

            assertEquals(original.gameValue, rotated.gameValue, "Der Spielwert haengt nicht am Sitzplatz")
            for (seat in round.seats) {
                val target = Math.floorMod(seat + shift, round.seatCount)
                assertEquals(
                    original.halfPoints[seat],
                    rotated.halfPoints[target],
                    "Sitz $seat -> $target bei shift=$shift, Runde $round",
                )
            }
            assertEquals(0, rotated.sum())
        }
    }

    @Test
    fun `Der Aussetzende bekommt immer genau null und es spielen immer drei`() = property {
        checkAll(500, arbRound()) { round ->
            val score = Scoring.score(round)
            assertEquals(3, round.activeSeats.size, "activeSeats: ${round.activeSeats}")
            round.sittingOutSeat?.let { assertEquals(0, score.halfPoints[it], "Aussetzer $it") }
            assertEquals(round.seatCount, score.halfPoints.size, "jeder Sitzplatz kommt vor")
            assertEquals(0, score.sum())
        }
    }

    @Test
    fun `Ein verlorenes Spiel kostet den Alleinspieler das Doppelte des Gewinns`() {
        // Die 2:1-Relation der Hausregel: +V gegen -2V. In halben Punkten also
        // +2V gegen -4V, und beim Gegenspieler -V gegen +2V.
        for (declaration in declarations) {
            for (contra in ContraLevel.entries) {
                val won = Scoring.score(round(declaration, won = true, contra = contra))
                val lost = Scoring.score(round(declaration, won = false, contra = contra))

                assertEquals(-2 * won.halfPoints.getValue(0), lost.halfPoints.getValue(0), "$declaration $contra")
                assertEquals(-2 * won.halfPoints.getValue(1), lost.halfPoints.getValue(1), "$declaration $contra")
                assertEquals(0, won.sum())
                assertEquals(0, lost.sum())
            }
        }
    }

    @Test
    fun `Ueberreiztes Spiel gilt als verloren, auch wenn es gewonnen wurde`() {
        for (declaration in declarations) {
            val overbid = Scoring.score(round(declaration, won = true, bid = Scoring.MAX_BID, overbid = true))
            assertTrue(overbid.halfPoints.getValue(0) < 0, "$declaration")
        }
    }

    // --- validate() und score() sagen dasselbe ---

    /** Baut aus einer gueltigen Runde eine mit genau einem Defekt. */
    private fun arbBrokenRound(): Arb<Round> = arbitrary { rs ->
        val round = arbRound().next(rs)
        when (Arb.int(0..7).next(rs)) {
            0 -> round.copy(seatCount = Arb.of(0, 1, 2, 5).next(rs))
            1 -> round.copy(declarerSeat = round.sittingOutSeat ?: round.seatCount)
            2 -> round.copy(sittingOutSeat = if (round.seatCount == 4) null else 0)
            3 -> round.copy(dealerSeat = round.seatCount)
            4 -> round.copy(bid = Arb.of(0, 17, Scoring.MAX_BID + 1, Int.MAX_VALUE).next(rs), overbid = true)
            5 -> round.copy(ramsch = round.ramsch?.copy(pushes = 32) ?: RamschResult(0, 60))
            6 -> round.copy(declaration = SuitGame(Suit.CLUBS, matadors = 0))
            else -> round.copy(declaration = RamschGame, ramsch = null, declarerSeat = round.declarerSeat ?: 0)
        }
    }

    @Test
    fun `validate ist genau dann leer wenn score nicht wirft`() = property {
        for (arb in listOf(arbRound(), arbBrokenRound())) {
            checkAll(1000, arb) { round ->
                val errors = Scoring.validate(round)
                if (errors.isEmpty()) {
                    assertEquals(0, Scoring.score(round).sum(), "gueltige Runde muss nullsummig sein: $round")
                } else {
                    val failure = assertFailsWith<IllegalArgumentException>("erwartet: $errors fuer $round") {
                        Scoring.score(round)
                    }
                    assertTrue(
                        failure.message.orEmpty().contains(round.id),
                        "Die Fehlermeldung soll die Runde benennen: ${failure.message}",
                    )
                }
            }
        }
    }

    // --- overbidValue() ---

    @Test
    fun `Ueberreizwert ist ein Vielfaches des Grundwerts und erreicht mindestens den Reizwert`() = property {
        // Nullspiele sind ausgenommen, ihr Wert ist fest.
        val bidable = declarations.filter { it !is NullGame }
        checkAll(2000, Arb.of(bidable), Arb.int(Scoring.MIN_BID..Scoring.MAX_BID)) { declaration, bid ->
            val base = Scoring.baseValue(declaration)
            val target = maxOf(Scoring.gameValue(declaration), bid)
            val value = Scoring.overbidValue(declaration, bid)

            assertEquals(0, value % base, "$value ist kein Vielfaches von $base ($declaration)")
            assertTrue(value >= target, "$value unterschreitet $target bei $declaration, bid=$bid")
            // Und es ist das KLEINSTE solche Vielfache: eines weniger reicht nicht.
            assertTrue(value - base < target, "$value ist nicht das kleinste passende Vielfache ($declaration, bid=$bid)")
        }
    }

    @Test
    fun `Ueberreizwert steigt monoton mit dem Reizwert`() {
        for (declaration in declarations) {
            var previous = 0
            for (bid in Scoring.MIN_BID..Scoring.MAX_BID) {
                val value = Scoring.overbidValue(declaration, bid)
                assertTrue(value >= previous, "$declaration: bid=$bid ergibt $value nach $previous")
                previous = value
            }
        }
    }

    @Test
    fun `Nullspiele kennen keinen Ueberreizwert`() {
        for (declaration in declarations.filterIsInstance<NullGame>()) {
            for (bid in Scoring.MIN_BID..Scoring.MAX_BID) {
                assertEquals(declaration.variant.gameValue, Scoring.overbidValue(declaration, bid))
            }
        }
    }

    @Test
    fun `Grand rechnet mit dem Grundwert 24`() {
        for (declaration in declarations.filterIsInstance<GrandGame>()) {
            assertEquals(
                Scoring.GRAND_BASE * Scoring.gameLevel(declaration.matadors, declaration.modifiers),
                Scoring.gameValue(declaration),
            )
        }
    }

    private fun round(
        declaration: Declaration,
        won: Boolean = true,
        contra: ContraLevel = ContraLevel.NONE,
        bid: Int? = null,
        overbid: Boolean = false,
    ) = Round(
        id = "prop",
        seatCount = 3,
        declarerSeat = 0,
        sittingOutSeat = null,
        declaration = declaration,
        won = won,
        contra = contra,
        bid = bid,
        overbid = overbid,
    )
}
