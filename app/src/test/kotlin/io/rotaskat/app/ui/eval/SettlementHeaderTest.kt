package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Die Kopfzeile der Abrechnung zeigt Icons statt Textknoepfe: Teilen und
 * Punkteverlauf. Beide muessen ueber ihre contentDescription auffindbar sein
 * (Screenreader) und ihren jeweiligen Callback ausloesen.
 */
@RunWith(RobolectricTestRunner::class)
class SettlementHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Icons zeigen Beschriftung und rufen ihre Callbacks auf`() {
        var shared = 0
        var toHistory = 0
        compose.setContent {
            RotaskatTheme {
                Row {
                    SettlementActions(onShare = { shared++ }, onHistory = { toHistory++ })
                }
            }
        }

        compose.onNodeWithContentDescription("Teilen").performClick()
        compose.onNodeWithContentDescription("Punkteverlauf").performClick()

        assertEquals(1, shared)
        assertEquals(1, toHistory)
    }
}
