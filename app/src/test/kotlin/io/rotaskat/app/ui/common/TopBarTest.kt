package io.rotaskat.app.ui.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TopBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Zurueck ist ein Pfeil mit Beschriftung fuer TalkBack`() {
        var backs = 0
        compose.setContent {
            RotaskatTheme { RotaskatTopBar(title = "Neuer Abend", onBack = { backs++ }) }
        }
        compose.onNodeWithText("Neuer Abend").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `ohne onBack gibt es keinen Pfeil`() {
        compose.setContent { RotaskatTheme { RotaskatTopBar(title = "Rangliste") } }
        compose.onNodeWithContentDescription("Zurück").assertDoesNotExist()
    }
}
