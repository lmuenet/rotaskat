package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import io.rotaskat.app.ui.common.formatDate
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.seatNames
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.SessionStatus

/**
 * Die Abende des Vereins, der juengste zuerst.
 *
 * Zugleich der Einstieg in alles Ausgewertete: ein laufender Abend fuehrt in die
 * Eingabe, ein abgeschlossener in seine Abrechnung. Bewusst eine Liste und kein
 * Blaetterwerk mit Wischgesten - wer einen bestimmten Abend sucht, sucht ihn
 * ueber sein Datum, und ein Datum liest man in einer Liste schneller als in
 * einer Kartenfolge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    actions: RotaskatNavActions,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: EvaluationViewModel = viewModel(
        factory = remember(graph) { EvaluationViewModel.factory(graph) },
    )
    val states by viewModel.states.collectAsState()
    val roster by viewModel.roster.collectAsState()
    val pending by viewModel.pendingSync.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Rotaskat") },
                actions = {
                    TextButton(onClick = { actions.toLeaderboard() }) { Text("Rangliste") }
                    TextButton(onClick = { actions.toStats() }) { Text("Statistik") }
                },
            )
        },
    ) { padding ->
        val open = states.firstOrNull { it.session.status == SessionStatus.OPEN }
        val closed = states.filter { it.session.status == SessionStatus.CLOSED }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(RotaskatDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        ) {
            if (open != null) {
                item(key = "open") {
                    SectionHeading("Laufender Abend")
                }
                item(key = open.session.id) {
                    SessionCard(
                        state = open,
                        roster = roster,
                        onClick = { actions.toSession(open.session.id) },
                    )
                }
            }

            if (open == null) {
                // Nur wenn nichts laeuft: es gibt hoechstens einen offenen
                // Abend, und ein zweiter Knopf daneben wuerde das Gegenteil
                // suggerieren.
                item(key = "new-session") {
                    Button(
                        onClick = { actions.toNewSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RotaskatDimens.bigTapTarget),
                    ) { Text("Neuen Abend starten") }
                }
            }

            item(key = "closed-heading") {
                SectionHeading(
                    if (closed.isEmpty()) "Noch keine abgeschlossenen Abende" else "Abgeschlossene Abende"
                )
            }

            items(closed, key = { it.session.id }) { state ->
                SessionCard(
                    state = state,
                    roster = roster,
                    onClick = { actions.toSettlement(state.session.id) },
                )
            }

            if (pending > 0) {
                item(key = "pending") {
                    Notice(
                        "$pending ${if (pending == 1) "Runde wartet" else "Runden warten"} auf den " +
                            "Server. Gespielt und gerechnet wird trotzdem - der Sync holt das nach.",
                        modifier = Modifier.padding(top = RotaskatDimens.itemSpacing),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

/**
 * Ein Abend in der Liste.
 *
 * Der Endstand steht schon hier, nicht erst im Detail: wer die Historie
 * durchblaettert, sucht meistens genau ihn, und ein Tap, der nur eine Zahl
 * nachliefert, ist ein Tap zu viel.
 */
@Composable
private fun SessionCard(
    state: SessionState,
    roster: List<Player>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.scoreColors
    val names = seatNames(state.session, roster)
    val ranking = (0 until state.session.seatCount)
        .map { seat -> (names[seat] ?: "Platz ${seat + 1}") to (state.totals[seat] ?: 0L) }
        .sortedByDescending { it.second }
    val leader = ranking.firstOrNull()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatDate(state.session.startedAt),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append("${state.liveRounds.size} ")
                            append(if (state.liveRounds.size == 1) "Runde" else "Runden")
                            append(" - ${state.session.seatCount} Spieler")
                            if (state.session.status == SessionStatus.OPEN) append(" - laeuft")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (leader != null) {
                    Text(
                        text = formatPoints(leader.second),
                        style = RotaskatTextStyles.scoreMedium,
                        color = when {
                            leader.second > 0 -> colors.gain
                            leader.second < 0 -> colors.loss
                            else -> colors.neutral
                        },
                    )
                }
            }
            if (ranking.isNotEmpty()) {
                Text(
                    text = ranking.joinToString("  ") { "${it.first} ${formatPoints(it.second)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
