package io.rotaskat.app.ui.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Achsenteilung des Punkteverlaufs.
 *
 * Sie ist der einzige Teil des selbstgezeichneten Diagramms, in dem gerechnet
 * wird - und damit der einzige, der still falsch sein kann. Ein Diagramm ohne
 * Nulllinie oder mit einer Linie ausserhalb der Achse faellt beim Hinsehen nicht
 * unbedingt auf.
 */
class PointsChartTest {

    @Test
    fun `Die Nulllinie ist immer dabei`() {
        val scale = chartScale(listOf(20L, 60L, 140L))
        assertTrue(0L in scale.lines, "Ohne Nulllinie ist nicht zu sehen, wer im Plus steht")
        assertTrue(scale.min <= 0L && scale.max >= 140L)
    }

    @Test
    fun `Alle Werte liegen innerhalb der Achse`() {
        val values = listOf(-233L, -12L, 0L, 45L, 907L)
        val scale = chartScale(values)
        assertTrue(values.all { it in scale.min..scale.max })
        assertEquals(0L, Math.floorMod(scale.min, scale.step))
    }

    @Test
    fun `Ein flacher Verlauf bekommt trotzdem eine Hoehe`() {
        // Sonst waere die Spanne 0 und die Umrechnung auf den Bildschirm eine
        // Division durch null.
        val scale = chartScale(listOf(0L, 0L, 0L))
        assertTrue(scale.max > scale.min)
        assertTrue(scale.lines.size >= 2)
    }

    @Test
    fun `Die Teilung liegt auf ganzen Punkten`() {
        // Halbe Punkte sind die Speicherform, nicht die Anzeigeform. Eine Achse
        // mit der Beschriftung "+2,5" waere richtig und trotzdem unlesbar.
        for (span in listOf(4L, 30L, 200L, 5000L)) {
            val scale = chartScale(listOf(-span, span))
            assertEquals(0L, scale.step % 2, "Schritt ${scale.step} ist kein ganzer Punkt")
        }
    }
}
