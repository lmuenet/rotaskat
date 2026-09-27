package io.rotaskat.app.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rotaskat.app.ui.theme.RotaskatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Der Einstieg im Kartentisch-Look: kurze Texte statt Fliesstext, Klick auf
 * "Ohne Verein" fuehrt weiterhin direkt zu [OnboardingScreen]s `onLocal`.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `zeigt die kurzen Texte und ruft onLocal beim Klick auf Ohne Verein`() {
        var localClicks = 0
        var joinClicks = 0
        compose.setContent {
            RotaskatTheme {
                OnboardingScreen(onLocal = { localClicks++ }, onJoin = { joinClicks++ })
            }
        }

        compose.onNodeWithText("Punkte für eure Skatrunde.").assertExists()
        compose.onNodeWithText("Ohne Verein").assertExists()
        compose.onNodeWithText("Mit Verein").assertExists()
        compose.onNodeWithText("Am Tisch braucht die App keinen Empfang.").assertExists()

        compose.onNodeWithText("Ohne Verein").performClick()
        assertEquals(1, localClicks)
        assertEquals(0, joinClicks)
    }
}
