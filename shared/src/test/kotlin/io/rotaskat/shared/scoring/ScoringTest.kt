package io.rotaskat.shared.scoring

import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScoringTest {

    private fun round(
        declaration: io.rotaskat.shared.model.Declaration,
        won: Boolean = true,
        seatCount: Int = 3,
        declarerSeat: Int? = 0,
        sittingOutSeat: Int? = null,
        contra: ContraLevel = ContraLevel.NONE,
        bid: Int? = null,
        overbid: Boolean = false,
        ramsch: RamschResult? = null,
    ) = Round(
        id = "test",
        seatCount = seatCount,
        declarerSeat = declarerSeat,
        sittingOutSeat = sittingOutSeat,
        declaration = declaration,
        won = won,
        contra = contra,
        bid = bid,
        overbid = overbid,
        ramsch = ramsch,
    )

    // --- Spielwerte nach Skatordnung ---

    @Test
    fun `Kreuz mit zwei Hand ergibt 48`() {
        val d = SuitGame(Suit.CLUBS, matadors = 2, modifiers = Modifiers(hand = true))
        assertEquals(48, Scoring.gameValue(d))
    }

    @Test
    fun `Karo ohne eins ergibt 18`() {
        val d = SuitGame(Suit.DIAMONDS, matadors = 1)
        assertEquals(18, Scoring.gameValue(d))
    }

    @Test
    fun `Grand ouvert mit vier ergibt 264`() {
        // Ouvert impliziert Hand, Schneider, Schneider angesagt, Schwarz und
        // Schwarz angesagt, also 6 Stufen plus Spitzen plus das Spiel selbst.
        val d = GrandGame(matadors = 4, modifiers = Modifiers(ouvert = true))
        assertEquals(11, Scoring.gameLevel(4, Modifiers(ouvert = true)))
        assertEquals(264, Scoring.gameValue(d))
    }

    @Test
    fun `Nullspiele haben feste Werte`() {
        assertEquals(23, Scoring.gameValue(NullGame(NullVariant.NULL)))
        assertEquals(35, Scoring.gameValue(NullGame(NullVariant.NULL_HAND)))
        assertEquals(46, Scoring.gameValue(NullGame(NullVariant.NULL_OUVERT)))
        assertEquals(59, Scoring.gameValue(NullGame(NullVariant.NULL_HAND_OUVERT)))
    }

    // --- Verteilung ---

    @Test
    fun `Gewonnenes Spiel gibt dem Alleinspieler den vollen Wert`() {
        val score = Scoring.score(round(GrandGame(matadors = 1), won = true))
        // Spielwert 48: Alleinspieler +48, Gegner je -24.
        assertEquals(96, score.halfPoints[0])
        assertEquals(-48, score.halfPoints[1])
        assertEquals(-48, score.halfPoints[2])
        assertEquals(48.0, score.points[0])
        assertEquals(-24.0, score.points[1])
    }

    @Test
    fun `Verlorenes Spiel kostet den Alleinspieler den doppelten Wert`() {
        val score = Scoring.score(round(GrandGame(matadors = 1), won = false))
        assertEquals(-192, score.halfPoints[0])
        assertEquals(96, score.halfPoints[1])
        assertEquals(96, score.halfPoints[2])
        assertEquals(-96.0, score.points[0])
        assertEquals(48.0, score.points[1])
    }

    @Test
    fun `Ungerader Spielwert bleibt in halben Punkten exakt`() {
        val score = Scoring.score(round(NullGame(NullVariant.NULL), won = true))
        // Null = 23, die Gegner bekommen je -11,5.
        assertEquals(46, score.halfPoints[0])
        assertEquals(-23, score.halfPoints[1])
        assertEquals(-23, score.halfPoints[2])
        assertEquals(-11.5, score.points[1])
        assertEquals(0, score.sum())
    }

    @Test
    fun `Kontra verdoppelt und Re vervierfacht`() {
        val base = Scoring.score(round(SuitGame(Suit.HEARTS, 1))).halfPoints[0]!!
        val kontra = Scoring.score(round(SuitGame(Suit.HEARTS, 1), contra = ContraLevel.KONTRA))
        val re = Scoring.score(round(SuitGame(Suit.HEARTS, 1), contra = ContraLevel.RE))
        assertEquals(base * 2, kontra.halfPoints[0])
        assertEquals(base * 4, re.halfPoints[0])
    }

    // --- Vierertisch ---

    @Test
    fun `Der Aussetzende bekommt null Punkte`() {
        val score = Scoring.score(
            round(GrandGame(matadors = 1), won = true, seatCount = 4, declarerSeat = 0, sittingOutSeat = 3)
        )
        assertEquals(96, score.halfPoints[0])
        assertEquals(-48, score.halfPoints[1])
        assertEquals(-48, score.halfPoints[2])
        assertEquals(0, score.halfPoints[3])
        assertEquals(0, score.sum())
    }

    // --- Ramsch ---

    @Test
    fun `Ramsch verteilt die Augen des Verlierers halb an die anderen`() {
        val score = Scoring.score(
            round(
                RamschGame,
                declarerSeat = null,
                ramsch = RamschResult(loserSeat = 1, cardPoints = 82),
            )
        )
        assertEquals(-164, score.halfPoints[1])
        assertEquals(82, score.halfPoints[0])
        assertEquals(82, score.halfPoints[2])
        assertEquals(-82.0, score.points[1])
        assertEquals(41.0, score.points[0])
        assertEquals(0, score.sum())
    }

    @Test
    fun `Jungfrau verdoppelt die Augen`() {
        val score = Scoring.score(
            round(
                RamschGame,
                declarerSeat = null,
                ramsch = RamschResult(loserSeat = 2, cardPoints = 60, jungfrau = true),
            )
        )
        assertEquals(-240, score.halfPoints[2])
        assertEquals(120, score.halfPoints[0])
        assertEquals(0, score.sum())
    }

    @Test
    fun `Durchmarsch wird wie ein gewonnener Grand gewertet`() {
        val score = Scoring.score(
            round(
                RamschGame,
                declarerSeat = null,
                ramsch = RamschResult(loserSeat = 0, cardPoints = 120, durchmarschSeat = 0),
            )
        )
        assertEquals(240, score.halfPoints[0])
        assertEquals(-120, score.halfPoints[1])
        assertEquals(-120, score.halfPoints[2])
        assertEquals(0, score.sum())
    }

    @Test
    fun `Jeder Schub verdoppelt die Augen`() {
        val r = round(
            RamschGame,
            declarerSeat = null,
            ramsch = RamschResult(loserSeat = 1, cardPoints = 40, pushes = 2),
        )
        // Voreinstellung: zwei Schuebe vervierfachen 40 auf 160.
        assertEquals(-320, Scoring.score(r).halfPoints[1])
        assertEquals(-160.0, Scoring.score(r).points[1])
        // Abschaltbar, falls die Hausregel sich aendert.
        assertEquals(-80, Scoring.score(r, ScoringConfig(pushDoubles = false)).halfPoints[1])
    }

    @Test
    fun `Zu viele Schuebe werden abgelehnt statt still zu ueberlaufen`() {
        // 1 shl 32 ist in Kotlin wieder 1, weil nur die unteren 5 Bit zaehlen.
        // Ohne Obergrenze waere das Ergebnis still falsch und trotzdem
        // nullsummig, also durch die Invariante nicht zu entdecken.
        val invalid = round(
            RamschGame,
            declarerSeat = null,
            ramsch = RamschResult(loserSeat = 1, cardPoints = 100, pushes = 32),
        )
        assertTrue(Scoring.validate(invalid).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { Scoring.score(invalid) }
    }

    @Test
    fun `Schuebe bis zur Obergrenze bleiben nullsummig und positiv`() {
        for (pushes in 0..Scoring.MAX_PUSHES) {
            val score = Scoring.score(
                round(
                    RamschGame,
                    declarerSeat = null,
                    ramsch = RamschResult(loserSeat = 1, cardPoints = Scoring.MAX_CARD_POINTS, pushes = pushes),
                )
            )
            assertEquals(0, score.sum(), "pushes=$pushes")
            assertTrue(score.halfPoints[1]!! < 0, "Verlierer muss negativ bleiben, pushes=$pushes")
        }
    }

    // --- Ueberreizt ---

    @Test
    fun `Ueberreizt zaehlt das naechste Vielfache des Grundwerts und gilt als verloren`() {
        // Karo mit 1 ist 18 wert, gereizt wurde aber 20 -> 27 (naechstes Vielfaches von 9).
        val d = SuitGame(Suit.DIAMONDS, matadors = 1)
        assertEquals(27, Scoring.overbidValue(d, bid = 20))

        val score = Scoring.score(round(d, won = true, bid = 20, overbid = true))
        assertEquals(-108, score.halfPoints[0])
        assertEquals(54, score.halfPoints[1])
        assertEquals(0, score.sum())
    }

    @Test
    fun `Ueberreizt genau auf dem Spielwert bleibt beim Spielwert`() {
        val d = SuitGame(Suit.DIAMONDS, matadors = 1)
        assertEquals(18, Scoring.overbidValue(d, bid = 18))
        assertEquals(27, Scoring.overbidValue(d, bid = 19))
        assertEquals(27, Scoring.overbidValue(d, bid = 27))
        assertEquals(36, Scoring.overbidValue(d, bid = 28))
    }

    @Test
    fun `Ein absurder Reizwert haengt nicht die Berechnung auf`() {
        // Frueher wurde in Einerschritten hochgezaehlt: bei einem sehr grossen
        // bid lief der Zaehler ins Int-Overflow und die Schleife terminierte
        // nie. Jetzt aufrundende Division mit Long-Zwischenrechnung.
        val d = SuitGame(Suit.DIAMONDS, matadors = 1)
        val value = Scoring.overbidValue(d, bid = Int.MAX_VALUE)
        assertTrue(value > 0, "Ergebnis darf nicht ins Negative kippen")

        // Ueber score() ist der Wert ohnehin auf MAX_BID begrenzt.
        val invalid = round(d, bid = Int.MAX_VALUE, overbid = true)
        assertTrue(Scoring.validate(invalid).isNotEmpty())
    }

    @Test
    fun `Reizwerte unterhalb von 18 werden abgelehnt`() {
        val invalid = round(SuitGame(Suit.CLUBS, 1), bid = 17, overbid = true)
        assertTrue(Scoring.validate(invalid).isNotEmpty())
    }

    // --- Invarianten ---

    @Test
    fun `Jede Runde ist nullsummig`() {
        val declarations = listOf(
            SuitGame(Suit.DIAMONDS, 1),
            SuitGame(Suit.CLUBS, 4, Modifiers(hand = true, schneiderAnnounced = true)),
            SuitGame(Suit.SPADES, 2, Modifiers(ouvert = true)),
            GrandGame(1),
            GrandGame(3, Modifiers(hand = true)),
            NullGame(NullVariant.NULL),
            NullGame(NullVariant.NULL_OUVERT),
            NullGame(NullVariant.NULL_HAND_OUVERT),
        )
        for (declaration in declarations) {
            for (won in listOf(true, false)) {
                for (contra in ContraLevel.entries) {
                    val three = Scoring.score(round(declaration, won = won, contra = contra))
                    assertEquals(0, three.sum(), "Dreiertisch $declaration won=$won contra=$contra")

                    val four = Scoring.score(
                        round(
                            declaration,
                            won = won,
                            contra = contra,
                            seatCount = 4,
                            declarerSeat = 1,
                            sittingOutSeat = 3,
                        )
                    )
                    assertEquals(0, four.sum(), "Vierertisch $declaration won=$won contra=$contra")
                    assertEquals(0, four.halfPoints[3], "Aussetzer bekommt 0")
                }
            }
        }
    }

    @Test
    fun `Alle Ramsch Augenzahlen sind nullsummig`() {
        for (cardPoints in 0..Scoring.MAX_CARD_POINTS) {
            for (jungfrau in listOf(true, false)) {
                val score = Scoring.score(
                    round(
                        RamschGame,
                        declarerSeat = null,
                        ramsch = RamschResult(loserSeat = 1, cardPoints = cardPoints, jungfrau = jungfrau),
                    )
                )
                assertEquals(0, score.sum(), "cardPoints=$cardPoints jungfrau=$jungfrau")
            }
        }
    }

    // --- Validierung ---

    @Test
    fun `Der Aussetzende darf nicht Alleinspieler sein`() {
        val invalid = round(GrandGame(1), seatCount = 4, declarerSeat = 3, sittingOutSeat = 3)
        assertTrue(Scoring.validate(invalid).isNotEmpty())
        assertFailsWith<IllegalArgumentException> { Scoring.score(invalid) }
    }

    @Test
    fun `Vierertisch ohne Aussetzenden ist ungueltig`() {
        val invalid = round(GrandGame(1), seatCount = 4, declarerSeat = 0, sittingOutSeat = null)
        assertTrue(Scoring.validate(invalid).isNotEmpty())
    }

    @Test
    fun `Ramsch ohne Ergebnis ist ungueltig`() {
        val invalid = round(RamschGame, declarerSeat = null, ramsch = null)
        assertTrue(Scoring.validate(invalid).isNotEmpty())
    }

    @Test
    fun `Gueltige Runden melden keine Fehler`() {
        assertEquals(emptyList(), Scoring.validate(round(GrandGame(1))))
        assertEquals(
            emptyList(),
            Scoring.validate(round(RamschGame, declarerSeat = null, ramsch = RamschResult(1, 70))),
        )
    }
}
