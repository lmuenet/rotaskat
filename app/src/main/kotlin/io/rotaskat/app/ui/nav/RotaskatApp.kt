package io.rotaskat.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.LocalHaptics
import io.rotaskat.app.ui.common.rememberRotaskatHaptics
import io.rotaskat.app.ui.eval.LeaderboardScreen
import io.rotaskat.app.ui.eval.OverviewScreen
import io.rotaskat.app.ui.eval.ProgressScreen
import io.rotaskat.app.ui.eval.SettlementScreen
import io.rotaskat.app.ui.eval.StatsScreen
import io.rotaskat.app.ui.onboarding.JoinScreen
import io.rotaskat.app.ui.onboarding.LocalSetupScreen
import io.rotaskat.app.ui.onboarding.OnboardingScreen
import io.rotaskat.app.ui.session.NewSessionScreen
import io.rotaskat.app.ui.session.SessionScreen
import io.rotaskat.app.ui.theme.RotaskatTheme
import kotlinx.coroutines.flow.map

/**
 * Der Navigationsbaum der App.
 *
 * Er kennt die Adressen aus [Routes] und sonst nichts: kein Bildschirm baut
 * seine Ziele selbst zusammen, jeder bekommt [RotaskatNavActions]. Damit
 * beruehrt eine geaenderte Adresse genau zwei Dateien und keinen Bildschirm.
 *
 * Nach der Installation faengt alles im Einstieg an, danach in der Uebersicht
 * der Abende - nicht im laufenden Abend: die App wird oefter aufgemacht, um
 * nachzusehen, als um einzutragen, und wer eintragen will, ist mit einem Tap
 * dort.
 */
@Composable
fun RotaskatApp(graph: RotaskatGraph, modifier: Modifier = Modifier) {
    val haptics = rememberRotaskatHaptics()

    // Solange der Modus noch aus dem DataStore kommt, wird NICHTS gezeichnet.
    // Erst den Einstieg zu zeigen und ihn eine Zehntelsekunde spaeter gegen die
    // Uebersicht auszutauschen, saehe bei jedem App-Start nach einem Fehler aus.
    val mode by graph.settings.mode
        .map { LoadedMode(it) }
        .collectAsState(initial = null)

    CompositionLocalProvider(
        LocalRotaskatGraph provides graph,
        LocalHaptics provides haptics,
    ) {
        RotaskatTheme {
            mode?.let { loaded -> RotaskatNavHost(loaded, modifier) }
        }
    }
}

/** Der geladene Modus. Die Huelle unterscheidet "noch nicht geladen" von "nicht gewaehlt". */
private data class LoadedMode(val mode: AppMode?)

@Composable
private fun RotaskatNavHost(loaded: LoadedMode, modifier: Modifier) {
    val navController = rememberNavController()
    val actions = remember(navController) { RotaskatNavActions(navController) }

    // Einmal festgehalten: der Beitritt schaltet den Modus mitten im Ablauf von
    // null auf CLUB um. Ein daran haengendes startDestination wuerde den
    // Navigationsbaum unter dem Nutzer neu aufbauen.
    val startDestination = remember {
        if (loaded.mode == null) Routes.ONBOARDING else Routes.HOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onLocal = { actions.toLocalSetup() },
                onJoin = { actions.toJoin() },
            )
        }

        composable(Routes.LOCAL_SETUP) {
            LocalSetupScreen(
                onDone = { actions.toHome() },
                onBack = { actions.back() },
            )
        }

        composable(Routes.JOIN) {
            JoinScreen(
                onDone = { actions.toHome() },
                onBack = { actions.back() },
            )
        }

        composable(Routes.NEW_SESSION) {
            NewSessionScreen(
                onStarted = { sessionId -> actions.toSession(sessionId, replace = true) },
                onBack = { actions.back() },
            )
        }
        composable(Routes.HOME) {
            OverviewScreen(actions = actions)
        }

        composable(
            route = Routes.SESSION_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.sessionId() ?: return@composable
            SessionScreen(sessionId = sessionId, actions = actions)
        }

        composable(
            route = Routes.ROUND_EDIT_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val sessionId = entry.sessionId() ?: return@composable
            val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID) ?: return@composable
            SessionScreen(
                sessionId = sessionId,
                actions = actions,
                editRoundId = roundId,
            )
        }

        composable(
            route = Routes.SETTLEMENT_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.sessionId() ?: return@composable
            SettlementScreen(sessionId = sessionId, actions = actions)
        }

        composable(
            route = Routes.HISTORY_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.sessionId() ?: return@composable
            ProgressScreen(sessionId = sessionId, actions = actions)
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(actions = actions)
        }

        composable(Routes.STATS) {
            StatsScreen(actions = actions)
        }
    }
}

/**
 * Die Session-Id aus den Argumenten.
 *
 * Fehlt sie, wird nichts gezeichnet statt abgestuerzt. Das kann nur ueber einen
 * von aussen geschickten Link passieren - dann ist ein leerer Bildschirm die
 * richtige Antwort.
 */
private fun androidx.navigation.NavBackStackEntry.sessionId(): String? =
    arguments?.getString(Routes.ARG_SESSION_ID)
