package io.rotaskat.app.ui.eval

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LabelPositionsTest {

    @Test
    fun `weit auseinander bleibt alles, wo es ist`() {
        assertEquals(listOf(10f, 100f, 200f), labelPositions(listOf(10f, 100f, 200f), minGap = 20f, top = 0f, bottom = 300f))
    }

    @Test
    fun `gleicher Stand wird auseinandergeschoben, Reihenfolge bleibt`() {
        val placed = labelPositions(listOf(150f, 150f), minGap = 20f, top = 0f, bottom = 300f)
        assertTrue(placed[1] - placed[0] >= 20f, "$placed")
    }

    @Test
    fun `am unteren Rand wird nach oben ausgewichen`() {
        val placed = labelPositions(listOf(300f, 300f, 300f), minGap = 20f, top = 0f, bottom = 300f)
        assertTrue(placed.all { it in 0f..300f }, "$placed")
        val sorted = placed.sorted()
        assertTrue(sorted[1] - sorted[0] >= 20f && sorted[2] - sorted[1] >= 20f, "$placed")
    }

    @Test
    fun `die Reihenfolge der Wunschpositionen bleibt erhalten`() {
        val placed = labelPositions(listOf(50f, 40f, 45f), minGap = 20f, top = 0f, bottom = 300f)
        // Index 1 (40) ist oben, dann Index 2 (45), dann Index 0 (50).
        assertTrue(placed[1] < placed[2] && placed[2] < placed[0], "$placed")
    }
}
