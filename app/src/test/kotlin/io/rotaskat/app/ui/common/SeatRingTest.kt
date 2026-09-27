package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Ohne Mitte behandelte [SeatRing] den ERSTEN Messbaren immer als Mitte -
 * `DealerPicker` uebergibt keine Mitte, also landete Platz 0 in der Mitte und
 * die uebrigen Plaetze bildeten ein Dreieck statt eines Vierecks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xxhdpi")
class SeatRingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `ohne Mitte bilden vier Plaetze ein Viereck`() {
        compose.setContent {
            RotaskatTheme {
                SeatRing(seatCount = 4) { seat ->
                    Box(Modifier.size(40.dp).testTag("seat$seat"))
                }
            }
        }
        val seat0 = compose.onNodeWithTag("seat0").fetchSemanticsNode()
        val seat1 = compose.onNodeWithTag("seat1").fetchSemanticsNode()
        val seat2 = compose.onNodeWithTag("seat2").fetchSemanticsNode()
        val seat3 = compose.onNodeWithTag("seat3").fetchSemanticsNode()

        assertTrue(
            seat0.boundsInRoot.top > seat2.boundsInRoot.top,
            "Platz 0 (unten) muss unter Platz 2 (oben) liegen",
        )
        assertTrue(
            seat1.boundsInRoot.left < seat3.boundsInRoot.left,
            "Platz 1 (links) muss links von Platz 3 (rechts) liegen",
        )

        val ringCenterY = (seat0.boundsInRoot.top + seat2.boundsInRoot.top + seat0.boundsInRoot.height) / 2f
        val seat0CenterY = seat0.boundsInRoot.top + seat0.boundsInRoot.height / 2f
        val minDistancePx = with(compose.density) { 50.dp.toPx() }
        assertTrue(
            kotlin.math.abs(seat0CenterY - ringCenterY) >= minDistancePx,
            "Platz 0 darf nicht in der Ringmitte liegen",
        )
    }

    @Test
    fun `mit Mitte liegt sie zwischen Platz 0 und Platz 2`() {
        compose.setContent {
            RotaskatTheme {
                SeatRing(
                    seatCount = 4,
                    center = { Box(Modifier.size(40.dp).testTag("center")) },
                ) { seat ->
                    Box(Modifier.size(40.dp).testTag("seat$seat"))
                }
            }
        }
        val seat0 = compose.onNodeWithTag("seat0").fetchSemanticsNode()
        val seat2 = compose.onNodeWithTag("seat2").fetchSemanticsNode()
        val center = compose.onNodeWithTag("center").fetchSemanticsNode()

        val centerOfCenter = center.boundsInRoot.top + center.boundsInRoot.height / 2f
        val topSeatCenter = seat2.boundsInRoot.top + seat2.boundsInRoot.height / 2f
        val bottomSeatCenter = seat0.boundsInRoot.top + seat0.boundsInRoot.height / 2f

        assertTrue(
            centerOfCenter in topSeatCenter..bottomSeatCenter,
            "Die Mitte muss zwischen Platz 2 (oben) und Platz 0 (unten) liegen",
        )
    }
}
