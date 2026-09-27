package io.rotaskat.app.ui.theme

import org.junit.Test
import kotlin.test.assertEquals

class ScoreColorsTest {

    private val colors = RotaskatScoreColorsDark

    @Test
    fun `Vorzeichen bestimmt die Farbe`() {
        assertEquals(colors.gain, colors.forValue(1))
        assertEquals(colors.loss, colors.forValue(-1))
        assertEquals(colors.neutral, colors.forValue(0))
    }
}
