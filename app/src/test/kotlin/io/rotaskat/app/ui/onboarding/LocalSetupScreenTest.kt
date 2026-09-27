package io.rotaskat.app.ui.onboarding

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Der Haupt-Button des Einrichtens braucht die Tap-Zielgroesse, auch wenn
 * `heightIn` vor dem `padding` steht - sonst frisst das Padding die
 * Mindesthoehe wieder auf.
 */
@RunWith(RobolectricTestRunner::class)
class LocalSetupScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Los-geht's-Button erreicht das Tap-Ziel`() {
        compose.setContent {
            RotaskatTheme {
                LocalSetupPrimaryButton(label = "Los geht's", enabled = true, onClick = {})
            }
        }
        compose.onNodeWithText("Los geht's").assertHeightIsAtLeast(56.dp)
    }
}
