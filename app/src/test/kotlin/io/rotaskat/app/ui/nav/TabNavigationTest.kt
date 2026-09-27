package io.rotaskat.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Stapel beim Wechsel zwischen den Zielen der unteren Leiste.
 *
 * [RotaskatNavActions.toTopLevel] darf den Stapel nicht wachsen lassen -
 * zurueck soll von jedem Ziel der Leiste direkt in die Uebersicht fuehren,
 * egal wie oft davor zwischen den Reitern gewechselt wurde.
 */
@RunWith(RobolectricTestRunner::class)
class TabNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var actions: RotaskatNavActions

    @Composable
    private fun TestGraph() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        actions = RotaskatNavActions(navController)

        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) { Box {} }
            composable(Routes.LEADERBOARD) { Box {} }
            composable(Routes.STATS) { Box {} }
        }
    }

    private fun currentRoute(): String? = navController.currentBackStackEntry?.destination?.route

    /** Wie viele Ziele der drei Reiter aktuell im Stapel liegen. */
    private fun tabStackSize(): Int =
        navController.currentBackStack.value.count { it.destination.route in Routes.TOP_LEVEL }

    @Test
    fun `von Start ueber Rangliste zurueck zu Start landet auf Start`() {
        compose.setContent { TestGraph() }

        compose.runOnIdle { actions.toTopLevel(Routes.LEADERBOARD) }
        assertEquals(Routes.LEADERBOARD, currentRoute())

        compose.runOnIdle { actions.toTopLevel(Routes.HOME) }
        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun `von Start zur Statistik und zurueck landet auf Start`() {
        compose.setContent { TestGraph() }

        compose.runOnIdle { actions.toTopLevel(Routes.STATS) }
        assertEquals(Routes.STATS, currentRoute())

        compose.runOnIdle { actions.back() }
        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun `wiederholtes Wechseln laesst den Stapel nicht wachsen`() {
        compose.setContent { TestGraph() }

        repeat(5) {
            compose.runOnIdle {
                actions.toTopLevel(Routes.LEADERBOARD)
                actions.toTopLevel(Routes.STATS)
                actions.toTopLevel(Routes.HOME)
            }
        }

        assertTrue(tabStackSize() <= 2, "Stapel der Reiter ist auf ${tabStackSize()} Eintraege gewachsen")
    }
}
