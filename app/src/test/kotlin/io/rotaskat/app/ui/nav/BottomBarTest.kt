package io.rotaskat.app.ui.nav

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BottomBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `die Leiste steht nur auf Abende, Rangliste und Statistik`() {
        assertTrue(Routes.isTopLevel(Routes.HOME))
        assertTrue(Routes.isTopLevel(Routes.LEADERBOARD))
        assertTrue(Routes.isTopLevel(Routes.STATS))
        assertFalse(Routes.isTopLevel(Routes.SESSION_PATTERN))
        assertFalse(Routes.isTopLevel(Routes.SETTINGS))
        assertFalse(Routes.isTopLevel(Routes.ONBOARDING))
        assertFalse(Routes.isTopLevel(null))
    }

    @Test
    fun `aktives Ziel ist gewaehlt, ein Tap meldet das neue Ziel`() {
        var selected: String? = null
        compose.setContent {
            RotaskatTheme { RotaskatBottomBar(currentRoute = Routes.LEADERBOARD, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("Rangliste").assertIsSelected()
        compose.onNodeWithText("Abende").assertIsNotSelected()
        compose.onNodeWithText("Statistik").performClick()
        assertEquals(Routes.STATS, selected)
    }

    @Test
    fun `ein Tap auf das aktive Ziel navigiert nicht erneut`() {
        var selected: String? = null
        compose.setContent {
            RotaskatTheme { RotaskatBottomBar(currentRoute = Routes.HOME, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("Abende").performClick()
        assertEquals(null, selected)
    }

    @Test
    fun `die Reihenfolge der Beschriftungen entspricht Routes_TOP_LEVEL`() {
        compose.setContent {
            RotaskatTheme { RotaskatBottomBar(currentRoute = Routes.HOME, onSelect = {}) }
        }
        val labelForRoute = mapOf(
            Routes.HOME to "Abende",
            Routes.LEADERBOARD to "Rangliste",
            Routes.STATS to "Statistik",
        )
        val expected = Routes.TOP_LEVEL.map { labelForRoute.getValue(it) }
        val actual = expected.sortedBy { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.x }
        assertEquals(expected, actual)
    }
}
