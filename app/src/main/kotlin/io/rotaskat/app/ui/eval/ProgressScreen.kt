package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.StandingRow
import io.rotaskat.app.ui.common.StandingsTable
import io.rotaskat.app.ui.common.formatDate
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.seatNames
import io.rotaskat.app.ui.theme.seriesStyleFor
import io.rotaskat.shared.model.Player

/**
 * Der Punkteverlauf eines Abends.
 *
 * Die Frage, die dieser Bildschirm beantwortet, ist nicht "wer hat gewonnen" -
 * das steht im Endstand -, sondern "wann hat sich der Abend gedreht". Deshalb
 * eine Linie je Spieler ueber die Runden und nicht etwa Balken je Runde.
 */
@Composable
fun ProgressScreen(
    sessionId: String,
    actions: RotaskatNavActions,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: SessionDetailViewModel = viewModel(
        factory = remember(graph, sessionId) { SessionDetailViewModel.factory(graph, sessionId) },
    )
    val state by viewModel.state.collectAsState()
    val roster by viewModel.roster.collectAsState()

    val current = state
    EvalScaffold(
        title = "Punkteverlauf",
        subtitle = current?.let { formatDate(it.session.startedAt) },
        onBack = { actions.back() },
        modifier = modifier,
        actions = {
            if (current != null) {
                TextButton(onClick = { actions.toSettlement(sessionId) }) { Text("Abrechnung") }
            }
        },
    ) {
        if (current == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@EvalScaffold
        }

        val series = remember(current, roster) { seriesOf(current, roster) }

        EvalSection(
            title = "Verlauf",
            note = "Waagerecht die Runden, senkrecht der laufende Stand in Punkten. " +
                "Die Nulllinie ist staerker gezeichnet.",
        ) {
            PointsChart(series = series)
            ChartLegend(series = series, modifier = Modifier.padding(top = 8.dp))
        }

        EvalSection(title = "Stand", note = "${current.liveRounds.size} Runden") {
            StandingsTable(
                rows = series
                    .mapIndexed { seat, line ->
                        StandingRow(
                            key = seat.toString(),
                            name = line.label,
                            halfPoints = line.cumulative.lastOrNull() ?: 0L,
                            marker = line.style.color,
                        )
                    }
                    .sortedByDescending { it.halfPoints },
            )
        }
    }
}

/**
 * Eine Linie je Sitzplatz, in Sitzplatzreihenfolge.
 *
 * Die Reihenfolge ist wichtig: Platz 0 bekommt immer denselben Stil, in jedem
 * Diagramm und in jeder Legende. Nach Punkten sortiert waeren die Farben nach
 * jeder Runde andere.
 */
private fun seriesOf(state: SessionState, roster: List<Player>): List<ChartSeries> {
    val names = seatNames(state.session, roster)
    val cumulative = Statistics.cumulativeBySeat(state)
    return (0 until state.session.seatCount).map { seat ->
        ChartSeries(
            label = names[seat] ?: "Platz ${seat + 1}",
            style = seriesStyleFor(seat),
            cumulative = cumulative[seat] ?: listOf(0L),
        )
    }
}
