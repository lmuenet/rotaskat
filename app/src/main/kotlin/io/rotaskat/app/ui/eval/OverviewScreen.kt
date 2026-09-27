package io.rotaskat.app.ui.eval

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.RotaskatTopBar
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatShortDate
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.seatNames
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
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
            RotaskatTopBar(
                title = "Rotaskat",
                actions = {
                    IconButton(onClick = { actions.toSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        floatingActionButton = {
            NewSessionFab(
                running = states.any { it.session.status == SessionStatus.OPEN },
                onClick = { actions.toNewSession() },
            )
        },
    ) { padding ->
        val open = states.firstOrNull { it.session.status == SessionStatus.OPEN }
        val closed = states.filter { it.session.status == SessionStatus.CLOSED }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Unten Platz fuer den FAB, damit der letzte Abend nicht darunter liegt.
            contentPadding = PaddingValues(
                start = RotaskatDimens.screenPadding,
                end = RotaskatDimens.screenPadding,
                top = RotaskatDimens.itemSpacing,
                bottom = 96.dp,
            ),
        ) {
            if (open != null) {
                item(key = open.session.id) {
                    LiveSessionCard(
                        state = open,
                        names = seatNames(open.session, roster),
                        onContinue = { actions.toSession(open.session.id) },
                    )
                }
            }

            item(key = "closed-heading") {
                SectionLabel(
                    text = if (closed.isEmpty()) "Noch keine abgeschlossenen Abende" else "Frühere Abende",
                    modifier = Modifier.padding(top = RotaskatDimens.sectionSpacing),
                )
            }

            items(closed, key = { it.session.id }) { state ->
                PastSessionRow(
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

/**
 * Der "Neuer Abend"-Knopf, nur sichtbar, wenn keiner laeuft.
 *
 * Es gibt hoechstens einen offenen Abend: weder `NewSessionScreen` noch
 * `RoomRotaskatRepository.startSession` verhindern einen zweiten offenen
 * Abend - der versteckte Knopf ist die einzige Schranke dagegen. Laeuft
 * einer, ist "Weiterspielen" auf der Karte die Hauptaktion.
 */
@Composable
internal fun NewSessionFab(running: Boolean, onClick: () -> Unit) {
    if (running) return
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text("Neuer Abend") },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        // Tag statt Textsuche: Robolectric haengt das Layout des erweiterten
        // FAB fest, sodass sein Textknoten selbst als nicht angezeigt gilt -
        // der aeussere, korrekt vermessene Knoten bleibt zuverlaessig testbar.
        modifier = Modifier.testTag(NewSessionFabTags.FAB),
    )
}

/** Test-Tag fuer den "Neuer Abend"-Knopf. */
internal object NewSessionFabTags {
    const val FAB = "new-session-fab"
}

/**
 * Ein frueherer Abend: Datum, Umfang, Sieger.
 *
 * Eine ruhige Zeile statt einer Karte - der Endstand steht schon hier, nicht
 * erst im Detail: wer die Historie durchblaettert, sucht meistens genau ihn.
 */
@Composable
private fun PastSessionRow(
    state: SessionState,
    roster: List<Player>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.scoreColors
    val names = seatNames(state.session, roster)
    val winner = (0 until state.session.seatCount)
        .map { seat -> (names[seat] ?: "Platz ${seat + 1}") to (state.totals[seat] ?: 0L) }
        .maxByOrNull { it.second }
    val rounds = state.liveRounds.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = RotaskatDimens.tapTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatShortDate(state.session.startedAt),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$rounds ${if (rounds == 1) "Runde" else "Runden"} · " +
                        if (state.session.seatCount == 4) "zu viert" else "zu dritt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.accentColors.labelMuted,
                )
            }
            if (winner != null) {
                Text(
                    text = winner.first + " ",
                    style = RotaskatTextStyles.compact,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatPoints(winner.second),
                    style = RotaskatTextStyles.scoreMedium,
                    color = when {
                        winner.second > 0 -> colors.gain
                        winner.second < 0 -> colors.loss
                        else -> colors.neutral
                    },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
    }
}
