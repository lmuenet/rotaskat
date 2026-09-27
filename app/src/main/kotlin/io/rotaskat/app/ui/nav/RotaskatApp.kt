package io.rotaskat.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import io.rotaskat.app.ui.settings.SettingsScreen
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Waehrend die Leiste ausblendet, ist `currentRoute` schon auf dem naechsten,
    // nicht-obersten Ziel - ohne die zuletzt gueltige Route wuerde die Leiste
    // beim Ausblenden auf kein Ziel mehr zeigen.
    var lastTopLevel by remember { mutableStateOf(Routes.HOME) }
    if (Routes.isTopLevel(currentRoute)) lastTopLevel = currentRoute!!

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = Routes.isTopLevel(currentRoute),
                // Ohne expandVertically/shrinkVertically behaelt die Leiste ihre
                // gemessene Groesse waehrend der Animation, und das Scaffold-Padding
                // fuer den Inhalt springt erst im letzten Frame. Mit den beiden
                // wandert die Groesse - und damit das Padding - im selben Tempo mit.
                enter = slideInVertically(tween(150)) { it } +
                    fadeIn(tween(150)) +
                    expandVertically(tween(150), expandFrom = Alignment.Top),
                exit = slideOutVertically(tween(100)) { it } +
                    fadeOut(tween(100)) +
                    shrinkVertically(tween(100), shrinkTowards = Alignment.Top),
            ) {
                RotaskatBottomBar(currentRoute = lastTopLevel, onSelect = actions::toTopLevel)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = {
                RotaskatTransitions.enter(initialState.destination.route, targetState.destination.route)
            },
            exitTransition = { RotaskatTransitions.exit() },
            popEnterTransition = {
                RotaskatTransitions.popEnter(initialState.destination.route, targetState.destination.route)
            },
            popExitTransition = { RotaskatTransitions.exit() },
        ) {
            guarded(Routes.ONBOARDING) {
                OnboardingScreen(
                    onLocal = { actions.toLocalSetup() },
                    onJoin = { actions.toJoin() },
                )
            }
            guarded(Routes.LOCAL_SETUP) {
                LocalSetupScreen(onDone = { actions.toHome() }, onBack = { actions.back() })
            }
            guarded(Routes.JOIN) {
                JoinScreen(onDone = { actions.toHome() }, onBack = { actions.back() })
            }
            guarded(Routes.NEW_SESSION) {
                NewSessionScreen(
                    onStarted = { sessionId -> actions.toSession(sessionId, replace = true) },
                    onBack = { actions.back() },
                )
            }
            guarded(Routes.HOME) { OverviewScreen(actions = actions) }
            guarded(Routes.SESSION_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                SessionScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.ROUND_EDIT_PATTERN, listOf(sessionArg, roundArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                val roundId = entry.arguments?.getString(Routes.ARG_ROUND_ID) ?: return@guarded
                SessionScreen(sessionId = sessionId, actions = actions, editRoundId = roundId)
            }
            guarded(Routes.SETTLEMENT_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                SettlementScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.HISTORY_PATTERN, listOf(sessionArg)) { entry ->
                val sessionId = entry.sessionId() ?: return@guarded
                ProgressScreen(sessionId = sessionId, actions = actions)
            }
            guarded(Routes.LEADERBOARD) { LeaderboardScreen(actions = actions) }
            guarded(Routes.STATS) { StatsScreen(actions = actions) }
            guarded(Routes.SETTINGS) { SettingsScreen(actions = actions) }
        }
    }
}

private val sessionArg = navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }
private val roundArg = navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }

/** Ein Ziel, dessen Bildschirm waehrend des Verlassens keine Taps mehr annimmt. */
private fun NavGraphBuilder.guarded(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { entry ->
    LeavingGuard { content(entry) }
}

/**
 * Die Session-Id aus den Argumenten.
 *
 * Fehlt sie, wird nichts gezeichnet statt abgestuerzt. Das kann nur ueber einen
 * von aussen geschickten Link passieren - dann ist ein leerer Bildschirm die
 * richtige Antwort.
 */
private fun NavBackStackEntry.sessionId(): String? =
    arguments?.getString(Routes.ARG_SESSION_ID)
