package io.rotaskat.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Im Geraetetest oeffnete ein Tap aufs Zahnrad kurz nach "Zurueck" noch den
 * Dialog "Abend beenden?" des verlassenen Abends.
 */
@RunWith(RobolectricTestRunner::class)
class LeavingGuardTest {

    @get:Rule
    val compose = createComposeRule()

    private var visible by mutableStateOf(true)
    private var clicks = 0

    private fun show() {
        compose.setContent {
            AnimatedVisibility(visible = visible, exit = fadeOut(tween(1_000))) {
                LeavingGuard {
                    Box(Modifier.size(200.dp).testTag("screen").clickable { clicks++ })
                }
            }
        }
    }

    @Test
    fun `ein Tap auf den sichtbaren Bildschirm kommt an`() {
        show()
        compose.onNodeWithTag("screen").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `ein Tap auf den Bildschirm, der gerade verschwindet, kommt nicht an`() {
        show()
        compose.mainClock.autoAdvance = false
        visible = false
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("screen").performClick()
        assertEquals(0, clicks)
    }
}
