package io.rotaskat.shared.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueft die berechneten Spielwerte gegen die eingecheckte Referenztabelle.
 *
 * Die Datei wird NIE automatisch regeneriert. Schlaegt dieser Test fehl, hat
 * sich das Ergebnis der Abrechnung geaendert - entweder versehentlich, dann
 * gehoert der Code korrigiert, oder absichtlich, dann wird die Tabelle mit
 * `:shared:generateGameValueTable` neu geschrieben und der Diff gelesen.
 */
class GameValueTableTest {

    private val resourcePath = "/scoring/spielwerte.tsv"

    private fun goldenLines(): List<String> {
        val stream = javaClass.getResourceAsStream(resourcePath)
        assertNotNull(stream, "Golden-Datei $resourcePath fehlt im Testklassenpfad")
        return stream.bufferedReader().readText()
            .split("\n")
            .map { it.removeSuffix("\r") }
            .filter { it.isNotBlank() }
    }

    @Test
    fun `Die Spielwerttabelle stimmt mit der Referenzdatei ueberein`() {
        val expected = goldenLines()
        val actual = GameValueTable.rows()

        val firstDiff = expected.zip(actual).indexOfFirst { (e, a) -> e != a }
        if (firstDiff >= 0) {
            assertEquals(
                expected[firstDiff],
                actual[firstDiff],
                "Abweichung in Zeile ${firstDiff + 1} von $resourcePath. " +
                    "Wenn die Regelaenderung gewollt ist: :shared:generateGameValueTable ausfuehren und den Diff pruefen.",
            )
        }
        assertEquals(
            expected.size,
            actual.size,
            "Die Referenztabelle hat ${expected.size} Zeilen, berechnet wurden ${actual.size}",
        )
    }

    @Test
    fun `Die Tabelle deckt den gesamten Ansageraum ab`() {
        // 4 Farben * 11 Spitzen * 10 Stufenkombinationen + 4 Grand-Spitzen * 10
        // + 4 Nullvarianten. Faellt eine Kombination weg, faellt es hier auf.
        assertEquals(10, GameValueTable.legalModifiers().size)
        assertEquals(4 * 11 * 10 + 4 * 10 + 4, GameValueTable.allDeclarations().size)
        assertEquals(GameValueTable.allDeclarations().size, GameValueTable.allDeclarations().distinct().size)
    }

    @Test
    fun `Normalisierte Stufen sind Fixpunkte`() {
        // Waere das nicht so, haetten zwei verschiedene Tabellenzeilen dieselbe
        // Ansage: die Aufzaehlung waere doppelt und der Wertevergleich wertlos.
        for (modifiers in GameValueTable.legalModifiers()) {
            assertEquals(modifiers, modifiers.normalized(), "nicht normalisiert: $modifiers")
        }
    }

    @Test
    fun `Kein Spielwert der Tabelle liegt ueber dem hoechsten Reizwert`() {
        for (declaration in GameValueTable.allDeclarations()) {
            val value = Scoring.gameValue(declaration)
            assertTrue(value in 1..Scoring.MAX_BID, "Spielwert $value fuer $declaration")
        }
    }
}
