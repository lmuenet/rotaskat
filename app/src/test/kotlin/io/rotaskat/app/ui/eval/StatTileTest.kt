package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatTileTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Kachel zeigt Label, Wert, Detail und Warnung`() {
        compose.setContent {
            RotaskatTheme {
                StatTile(label = "Gewinnquote allein", value = "100 %", detail = "1 von 1", warning = "Unter 5 Alleinspielen wenig aussagekräftig")
            }
        }
        compose.onNodeWithText("Gewinnquote allein").assertIsDisplayed()
        compose.onNodeWithText("100 %").assertIsDisplayed()
        compose.onNodeWithText("1 von 1").assertIsDisplayed()
        compose.onNodeWithText("Unter 5 Alleinspielen wenig aussagekräftig").assertIsDisplayed()
    }
}
