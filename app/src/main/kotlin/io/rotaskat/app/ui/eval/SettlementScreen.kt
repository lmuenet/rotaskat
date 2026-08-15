package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.rotaskat.app.ui.common.formatAmount
import io.rotaskat.app.ui.common.formatCents
import io.rotaskat.app.ui.common.formatDate
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.seatNames
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.settlement.Payment
import io.rotaskat.shared.settlement.Settlement

/**
 * Die Geldabrechnung eines Abends.
 *
 * Der Bildschirm mit dem groessten Alltagsnutzen: er beantwortet die einzige
 * Frage, die am Ende jedes Abends wirklich gestellt wird. Deshalb stehen die
 * Zahlungen ganz oben und in der groessten Schrift, und alles andere - Salden,
 * Endstand, Satz - darunter.
 *
 * Gerechnet wird nicht hier, sondern in `Settlements` aus :shared, mit
 * demselben Code, den auch der Server benutzt. Eine zweite Formel in der
 * Oberflaeche waere genau die Abweichung, die am Tisch niemand bemerkt.
 */
@Composable
fun SettlementScreen(
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
        title = "Geldabrechnung",
        subtitle = current?.let { formatDate(it.session.startedAt) },
        onBack = { actions.back() },
        modifier = modifier,
        actions = {
            if (current != null) {
                TextButton(onClick = { actions.toHistory(sessionId) }) { Text("Verlauf") }
            }
        },
    ) {
        if (current == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@EvalScaffold
        }

        val names = seatNames(current.session, roster)
        SettlementBody(state = current, names = names)
    }
}

@Composable
private fun SettlementBody(state: SessionState, names: Map<Int, String>) {
    // Die Abrechnung lehnt sich ab, wenn die Salden nicht auf null aufgehen -
    // lieber keine Zahl als eine falsche. Das kann nur passieren, wenn eine
    // Runde nicht abrechenbar ist; dann steht der Grund hier statt eines
    // Absturzes.
    val settlement = remember(state) { runCatching { state.settlement() }.getOrNull() }

    if (state.session.status == SessionStatus.OPEN) {
        Notice(
            "Dieser Abend laeuft noch. Die Abrechnung ist ein Zwischenstand und " +
                "aendert sich mit jeder weiteren Runde.",
        )
    }

    if (settlement == null) {
        Notice(
            "Die Abrechnung geht nicht auf: die Punkte dieses Abends summieren sich " +
                "nicht auf null. Solange das so ist, wird bewusst nichts ausgerechnet - " +
                "eine Runde muss korrigiert werden.",
        )
        Endstand(state, names)
        return
    }

    EvalSection(
        title = "Zahlungen",
        note = "So wenige Zahlungen wie moeglich - nicht jeder mit jedem.",
    ) {
        if (settlement.payments.isEmpty()) {
            Notice("Alles ausgeglichen. Heute zahlt niemand.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
                for (payment in settlement.payments) {
                    PaymentCard(payment = payment, names = names)
                }
            }
        }
    }

    EvalSection(
        title = "Salden",
        note = "${state.session.centsPerPoint} Cent je Punkt, am Anpfiff dieses Abends " +
            "festgehalten. Plus heisst bekommt, Minus heisst zahlt.",
    ) {
        BalanceTable(settlement = settlement, names = names, state = state)
    }

    Endstand(state, names)
}

@Composable
private fun Endstand(state: SessionState, names: Map<Int, String>) {
    EvalSection(
        title = "Endstand",
        note = "${state.liveRounds.size} Runden",
    ) {
        StandingsTable(
            rows = (0 until state.session.seatCount)
                .map { seat ->
                    StandingRow(
                        key = seat.toString(),
                        name = names[seat] ?: "Platz ${seat + 1}",
                        halfPoints = state.totals[seat] ?: 0L,
                    )
                }
                .sortedByDescending { it.halfPoints },
        )
    }
}

/**
 * Eine einzelne Zahlung.
 *
 * Die Richtung steht als Satz da und nicht als Pfeil zwischen zwei Namen: wer
 * an wen zahlt, wird am Tisch vorgelesen, und ein Pfeil laesst sich in beide
 * Richtungen lesen.
 */
@Composable
private fun PaymentCard(payment: Payment<Int>, names: Map<Int, String>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RotaskatDimens.commitButton)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = names[payment.from] ?: "Platz ${payment.from + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "zahlt an ${names[payment.to] ?: "Platz ${payment.to + 1}"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatAmount(payment.cents),
                style = RotaskatTextStyles.scoreLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Salden in Punkten und in Geld nebeneinander.
 *
 * Beide Spalten stehen da, weil beide gebraucht werden: die Punkte sind das,
 * was gespielt wurde, das Geld ist das, was auf den Tisch gelegt wird. Ohne die
 * Punktespalte laesst sich der Betrag nicht gegenlesen.
 */
@Composable
private fun BalanceTable(
    settlement: Settlement<Int>,
    names: Map<Int, String>,
    state: SessionState,
) {
    val colors = MaterialTheme.scoreColors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            settlement.balances
                .sortedByDescending { it.cents }
                .forEachIndexed { index, balance ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = RotaskatDimens.tapTarget)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = names[balance.player] ?: "Platz ${balance.player + 1}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                            Text(
                                text = formatPoints(state.totals[balance.player] ?: 0L) + " Punkte",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatCents(balance.cents),
                            style = RotaskatTextStyles.scoreMedium,
                            color = when {
                                balance.cents > 0 -> colors.gain
                                balance.cents < 0 -> colors.loss
                                else -> colors.neutral
                            },
                        )
                    }
                }
        }
    }
}
