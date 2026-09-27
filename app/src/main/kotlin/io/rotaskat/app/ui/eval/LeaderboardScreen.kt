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
        onBack = { actions.back() },
        modifier = modifier,
    ) {
        PeriodSelector(seasons = seasons, selected = period, onSelect = viewModel::setPeriod)

        if (standings.isEmpty()) {
            Notice(
                "Für diesen Zeitraum ist noch kein Abend erfasst. Die Rangliste " +
                    "entsteht aus den gespielten Runden, nicht aus dem Kader.",
            )
            return@EvalScaffold
        }

        EvalSection(
            title = "Punkte",
            note = "Jede Quote steht mit der Anzahl dahinter. Ohne sie ist sie nicht zu lesen.",
        ) {
            StandingsTable(rows = standings.map { it.toRow() })
        }

        Notice(
            "Gezählt werden alle nicht gelöschten Runden, auch die des laufenden " +
                "Abends. Jeder Abend rechnet mit den Hausregeln, die zu seinem Anpfiff " +
                "galten.",
        )
    }
}

/**
 * Die Zeile eines Spielers.
 *
 * Vier Zahlenspalten passen auf ein Telefon nur in einer Groesse, in der sie
 * niemand liest. Deshalb steht die Punktzahl gross rechts und alles, was sie
 * einordnet, klein unter dem Namen.
 */
private fun PlayerStats.toRow(): StandingRow = StandingRow(
    key = player.id,
    name = player.displayName,
    halfPoints = halfPoints,
    detail = buildString {
        append(counted(sessions, "Abend", "Abende"))
        append(" - ")
        append(counted(rounds, "Runde", "Runden"))
        append(" - allein ")
        val rate = soloWinRate
        if (rate == null) {
            append("nie")
        } else {
            // Geschuetzte Leerzeichen: die Klammer bricht sonst mitten in "2 von 2" um.
            append("${formatPercent(rate)}\u00A0($soloWins\u00A0von\u00A0$soloRounds)")
        }
    },
)
