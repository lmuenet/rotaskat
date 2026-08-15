package io.rotaskat.app.ui.nav

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Alle Routen der App an einer Stelle.
 *
 * Die Routen unter "reserviert" sind noch von keinem Bildschirm belegt. Sie
 * stehen trotzdem schon hier, damit die naechste Phase Rangliste, Statistik,
 * Verlauf und Geldabrechnung einhaengen kann, ohne den Navigationsbaum oder die
 * Aufrufer umzuschreiben: die Adresse steht fest, nur der Inhalt fehlt.
 *
 * Die Muster mit den geschweiften Klammern sind das, was die NavHost-Eintraege
 * brauchen, die Funktionen daneben das, was Aufrufer brauchen. Beide von Hand
 * zusammenzusetzen ist die klassische Stelle fuer einen Tippfehler, den erst
 * der Nutzer findet.
 */
object Routes {

    const val ARG_SESSION_ID = "sessionId"
    const val ARG_ROUND_ID = "roundId"

    const val JOIN = "beitritt"
    const val HOME = "start"
    const val NEW_SESSION = "abend/neu"

    const val SESSION_PATTERN = "abend/{$ARG_SESSION_ID}"
    fun session(sessionId: String): String = "abend/$sessionId"

    const val ROUND_EDIT_PATTERN = "abend/{$ARG_SESSION_ID}/runde/{$ARG_ROUND_ID}"
    fun roundEdit(sessionId: String, roundId: String): String = "abend/$sessionId/runde/$roundId"

    // --- reserviert fuer die naechste Phase ---

    const val SETTLEMENT_PATTERN = "abend/{$ARG_SESSION_ID}/abrechnung"
    fun settlement(sessionId: String): String = "abend/$sessionId/abrechnung"

    const val HISTORY_PATTERN = "abend/{$ARG_SESSION_ID}/verlauf"
    fun history(sessionId: String): String = "abend/$sessionId/verlauf"

    const val LEADERBOARD = "rangliste"
    const val STATS = "statistik"
}

/**
 * Die Navigationsbefehle, die ein Bildschirm kennen darf.
 *
 * Kein Bildschirm baut selbst eine Route zusammen. Damit bleibt der
 * Bildschirm ohne Navigationsgraph testbar, und eine geaenderte Adresse
 * beruehrt genau eine Datei.
 */
@Stable
class RotaskatNavActions(private val navController: NavHostController) {

    fun back() {
        navController.popBackStack()
    }

    fun toJoin() = navController.navigate(Routes.JOIN) {
        // Ohne Vereinsbeitritt gibt es nichts, wohin zurueckgegangen werden
        // koennte.
        popUpTo(0) { inclusive = true }
    }

    fun toHome() = navController.navigate(Routes.HOME) {
        popUpTo(0) { inclusive = true }
    }

    fun toNewSession() = navController.navigate(Routes.NEW_SESSION)

    /**
     * Der laufende Abend ersetzt den Startbildschirm im Stapel, wenn er gerade
     * angepfiffen wurde: der Weg zurueck darf nicht in eine halb ausgefuellte
     * Sitzordnung fuehren, die es schon gibt.
     */
    fun toSession(sessionId: String, replace: Boolean = false) =
        navController.navigate(Routes.session(sessionId)) {
            if (replace) popUpTo(Routes.HOME)
        }

    fun toRoundEdit(sessionId: String, roundId: String) =
        navController.navigate(Routes.roundEdit(sessionId, roundId))

    fun toSettlement(sessionId: String) = navController.navigate(Routes.settlement(sessionId))

    fun toHistory(sessionId: String) = navController.navigate(Routes.history(sessionId))

    fun toLeaderboard() = navController.navigate(Routes.LEADERBOARD)

    fun toStats() = navController.navigate(Routes.STATS)

    /** Fluchtweg fuer Ziele, die diese Klasse noch nicht kennt. */
    fun to(route: String, builder: NavOptionsBuilder.() -> Unit = {}) =
        navController.navigate(route, builder)
}
