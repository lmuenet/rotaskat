package io.rotaskat.app.ui.common

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ChipRowTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `gewaehlter Chip ist ausgewaehlt, Tap meldet die Auswahl`() {
        var picked: String? = null
        compose.setContent {
            RotaskatTheme {
                ChipRow(options = listOf("Alle Jahre", "2026"), selected = "2026", onSelect = { picked = it }, label = { it })
            }
        }
        compose.onNodeWithText("2026").assertIsSelected()
        compose.onNodeWithText("Alle Jahre").assertIsNotSelected().performClick()
        assertEquals("Alle Jahre", picked)
    }
}
