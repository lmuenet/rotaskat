package io.rotaskat.app.ui.eval

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.StandingRow
import io.rotaskat.app.ui.common.StandingsTable
import io.rotaskat.app.ui.common.counted
import io.rotaskat.app.ui.common.formatPercent
import io.rotaskat.app.ui.nav.RotaskatNavActions

/**
 * Die Rangliste.
 *
 * All-time und Saison sind derselbe Bildschirm mit einem anderen Filter, nicht
 * zwei Bildschirme: die Frage ist dieselbe, nur der Zeitraum ist ein anderer.
 * Eine Saison ist ein Kalenderjahr - am 1. Januar faengt die Saisontabelle neu
 * an, die All-Time-Tabelle laeuft unberuehrt weiter.
 */
@Composable
fun LeaderboardScreen(
    actions: RotaskatNavActions,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: EvaluationViewModel = viewModel(
        factory = remember(graph) { EvaluationViewModel.factory(graph) },
    )
    val standings by viewModel.standings.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val period by viewModel.period.collectAsState()

    EvalScaffold(
        title = "Rangliste",
        subtitle = period.label,
        onBack = null,
        modifier = modifier,
    ) {
        PeriodSelector(seasons = seasons, selected = period, onSelect = viewModel::setPeriod)

        if (standings.isEmpty()) {
            Notice("Noch kein Abend in diesem Zeitraum.")
            return@EvalScaffold
        }

        EvalSection(
            title = "Punkte",
            info = "Die Quote zählt nur Spiele als Alleinspieler. Die Zahl in Klammern ist " +
                "die Grundlage – aus wenigen Spielen sagt die Quote wenig. Gezählt werden " +
                "alle Runden, auch die des laufenden Abends, jede mit den Hausregeln ihres " +
                "Abends.",
        ) {
            StandingsTable(rows = standings.map { it.toRow() })
        }
    }
}

/**
 * Die Zeile eines Spielers.
 *
 * Vier Zahlenspalten passen auf ein Telefon nur in einer Groesse, in der sie
 * niemand liest. Deshalb steht die Punktzahl gross rechts und alles, was sie
 * einordnet, klein unter dem Namen.
 */
internal fun PlayerStats.toRow(): StandingRow = StandingRow(
    key = player.id,
    name = player.displayName,
    halfPoints = halfPoints,
    detail = buildString {
        append(counted(sessions, "Abend", "Abende"))
        append(" \u00B7 ")
        append(counted(rounds, "Runde", "Runden"))
        append(" \u00B7 allein ")
        val rate = soloWinRate
        if (rate == null) {
            append("nie")
        } else {
            append("${formatPercent(rate)} ($soloWins/$soloRounds)")
        }
    },
)
