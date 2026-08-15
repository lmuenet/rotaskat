package io.rotaskat.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.LocalHaptics
import io.rotaskat.app.ui.common.rememberRotaskatHaptics
import io.rotaskat.app.ui.eval.LeaderboardScreen
import io.rotaskat.app.ui.eval.OverviewScreen
import io.rotaskat.app.ui.eval.ProgressScreen
import io.rotaskat.app.ui.eval.SettlementScreen
import io.rotaskat.app.ui.eval.StatsScreen
import io.rotaskat.app.ui.session.SessionScreen
import io.rotaskat.app.ui.theme.RotaskatTheme

/**
 * Der Navigationsbaum der App.
 *
 * Er kennt die Adressen aus [Routes] und sonst nichts: kein Bildschirm baut
 * seine Ziele selbst zusammen, jeder bekommt [RotaskatNavActions]. Damit
 * beruehrt eine geaenderte Adresse genau zwei Dateien und keinen Bildschirm.
 *
 * Der Einstieg ist die Uebersicht der Abende und nicht der laufende Abend: die
 * App wird oefter aufgemacht, um nachzusehen, als um einzutragen, und wer
 * eintragen will, ist mit einem Tap dort.
 *
 * [Routes.JOIN] und [Routes.NEW_SESSION] sind hier bewusst NICHT eingetragen -
 * die zugehoerigen Bildschirme gibt es noch nicht. Ein Eintrag mit einem
 * Platzhalter dahinter waere schlimmer als keiner: er wuerde eine Adresse
 * anbieten, hinter der nichts steht.
 */
@Composable
fun RotaskatApp(graph: RotaskatGraph, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val actions = remember(navController) { RotaskatNavActions(navController) }
    val haptics = rememberRotaskatHaptics()

    CompositionLocalProvider(
        LocalRotaskatGraph provides graph,
        LocalHaptics provides haptics,
    ) {
        RotaskatTheme {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = modifier,
            ) {
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
