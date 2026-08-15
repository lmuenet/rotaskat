package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.KeepScreenOn
import io.rotaskat.app.ui.common.LocalHaptics
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.label
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.round.RoundCommitBar
import io.rotaskat.app.ui.round.RoundEntryPanel
import io.rotaskat.app.ui.seatNames
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.SessionStatus

/**
 * Der laufende Abend.
 *
 * Ein einziger Bildschirm traegt den kompletten Ablauf: Stand oben,
 * Rundeneingabe in der Mitte, gespielte Runden unten. Der Wechsel zwischen
 * diesen dreien ist am Tisch der haeufigste Vorgang ueberhaupt - jede
 * Navigation dazwischen waere ein Tap, der nichts eintraegt.
 *
 * Derselbe Bildschirm dient dem Korrigieren einer bereits gespielten Runde,
 * dann mit [editRoundId]. Die Eingabe ist in beiden Faellen dieselbe, also gibt
 * es sie auch nur einmal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    sessionId: String,
    actions: RotaskatNavActions,
    modifier: Modifier = Modifier,
    editRoundId: String? = null,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: SessionViewModel = viewModel(
        factory = remember(graph, sessionId) { SessionViewModel.factory(graph, sessionId) },
    )
    val haptics = LocalHaptics.current

    LaunchedEffect(editRoundId) {
        if (editRoundId != null) viewModel.beginEdit(editRoundId)
    }

    val state by viewModel.state.collectAsState()
    val roster by viewModel.roster.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    // Ein ausgegangener Bildschirm kostet zwischen zwei Runden mehr Zeit als die
    // gesamte Eingabe. Deshalb bleibt er an, solange der Abend laeuft - und nur
    // solange.
    KeepScreenOn(enabled = state?.session?.status == SessionStatus.OPEN)

    LaunchedEffect(message) {
        when (val current = message) {
            null -> Unit
            is SessionMessage.Saved -> {
                haptics.commit()
                if (current.closesEdit) {
                    // Eine abgeschlossene Korrektur fuehrt SOFORT zurueck.
                    // Frueher stand der Rueckweg hinter der Snackbar: der
                    // Bildschirm blieb bis dahin auf "Runde korrigieren"
                    // stehen, es sah aus, als haette der Tap nichts getan, und
                    // der naechste Tap speicherte ein zweites Mal. Die
                    // korrigierte Runde steht sofort in der Liste des Abends -
                    // das ist die bessere Rueckmeldung als eine Leiste, auf die
                    // gewartet werden muss.
                    viewModel.consumeMessage()
                    actions.back()
                } else {
                    val result = snackbarHostState.showSnackbar(
                        message = current.text,
                        actionLabel = "Rueckgaengig",
                        // Ohne Angabe waehlt Material3 bei gesetztem
                        // actionLabel Indefinite. Die Leiste bliebe dann ueber
                        // den Ergebnisknoepfen der naechsten Runde stehen, und
                        // der naechste Blindtap traefe "Rueckgaengig".
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(current.undo)
                    viewModel.consumeMessage()
                    // Nach dem Loeschen zurueck zum laufenden Abend, aber erst
                    // jetzt: eine geloeschte Runde ist ueber die Liste nicht
                    // mehr erreichbar, das Undo ist der einzige Rueckweg.
                    if (editRoundId != null) actions.back()
                }
            }

            is SessionMessage.Failed -> {
                haptics.failure()
                snackbarHostState.showSnackbar(current.text)
                viewModel.consumeMessage()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (editRoundId != null) "Runde korrigieren" else "Abend") },
                navigationIcon = {
                    if (editRoundId != null) {
                        TextButton(onClick = { actions.back() }) { Text("Zurueck") }
                    }
                },
                actions = {
                    if (editRoundId == null) {
                        if (state?.session?.status == SessionStatus.OPEN) {
                            TextButton(onClick = { confirmEnd = true }) { Text("Abend beenden") }
                        } else if (state != null) {
                            // Der Weg, den der Abend tatsaechlich nimmt: beenden,
                            // dann sofort abrechnen. Ohne diesen Knopf fuehrte er
                            // ueber den Zurueckweg in die Uebersicht.
                            TextButton(onClick = { actions.toSettlement(sessionId) }) {
                                Text("Abrechnung")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        val currentDraft = draft
        if (current == null || currentDraft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val names = seatNames(current.session, roster)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = RotaskatDimens.screenPadding),
        ) {
            // Der Stand bleibt stehen, waehrend die Eingabe darunter scrollt.
            // Er ist die einzige Zahl, die zwischen zwei Runden staendig gesucht
            // wird - ein Stand, der weggescrollt ist, wird stattdessen gefragt.
            Scoreboard(
                state = current,
                names = names,
                modifier = Modifier.padding(top = RotaskatDimens.screenPadding),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = RotaskatDimens.sectionSpacing),
                verticalArrangement = Arrangement.spacedBy(RotaskatDimens.sectionSpacing),
            ) {
                if (current.session.status == SessionStatus.CLOSED) {
                    Text(
                        text = "Dieser Abend ist beendet. Neue Runden gibt es nicht mehr.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    RoundEntryPanel(
                        draft = currentDraft,
                        seatNames = names,
                        onDraftChange = viewModel::updateDraft,
                        onDealerChange = viewModel::setDealer,
                    )
                }

                if (editRoundId == null) {
                    RoundHistory(
                        state = current,
                        names = names,
                        onEdit = { roundId -> actions.toRoundEdit(sessionId, roundId) },
                    )
                }
            }

            // Spielwert und Ergebnisknoepfe liegen AUSSERHALB des
            // Scrollbereichs. Sie sind der vierte Tap des Vier-Tap-Pfads und
            // stehen deshalb immer an derselben Stelle, egal wie weit die
            // Eingabe darueber gewachsen ist.
            if (current.session.status == SessionStatus.OPEN) {
                RoundCommitBar(
                    draft = currentDraft,
                    onCommit = viewModel::commit,
                    onCancelEdit = if (editRoundId != null) {
                        { viewModel.cancelEdit(); actions.back() }
                    } else {
                        null
                    },
                    onDelete = if (editRoundId != null) {
                        { viewModel.deleteRound(editRoundId) }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(bottom = RotaskatDimens.screenPadding),
                )
            }
        }
    }

    if (confirmEnd) {
        // Die einzige Rueckfrage der App, und zwar bewusst: "Abend beenden"
        // passiert genau einmal pro Abend, ist nicht rueckgaengig zu machen und
        // liegt in der Kopfzeile direkt neben nichts. Alle Rueckfragen, die
        // dreissigmal am Abend kaemen, gibt es dagegen nicht.
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Abend beenden?") },
            text = { Text("Danach koennen keine Runden mehr eingetragen werden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    viewModel.endSession()
                }) { Text("Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Weiterspielen") }
            },
        )
    }
}

/**
 * Der Stand.
 *
 * Punkte immer mit Vorzeichen und in Tabellenziffern; die Farbe ist der
 * Zweitkanal, nie der einzige Traeger. Wer aussetzt, steht mit dabei - eine
 * Spalte, die verschwindet und wiederkommt, laesst die Tabelle bei jedem Blick
 * anders aussehen.
 */
@Composable
private fun Scoreboard(state: SessionState, names: Map<Int, String>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.scoreColors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp)) {
            for (seat in 0 until state.session.seatCount) {
                val half = state.totals[seat] ?: 0L
                val sittingOut = seat == state.rotation.sittingOutSeat
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = names[seat] ?: "Platz ${seat + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sittingOut) colors.sittingOut else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatPoints(half),
                        style = RotaskatTextStyles.scoreLarge,
                        color = when {
                            half > 0 -> colors.gain
                            half < 0 -> colors.loss
                            else -> colors.neutral
                        },
                    )
                    if (sittingOut) {
                        Text(
                            text = "gibt",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.sittingOut,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Die gespielten Runden.
 *
 * Jede Zeile ist antippbar und fuehrt in dieselbe Eingabe zurueck. Eine
 * Korrektur ist damit genauso schnell wie eine Neueingabe, und das ist die
 * Voraussetzung dafuer, dass die Eingabe ohne Bestaetigungsdialoge auskommt.
 */
@Composable
private fun RoundHistory(
    state: SessionState,
    names: Map<Int, String>,
    onEdit: (String) -> Unit,
) {
    if (state.rounds.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Text(
            text = "Runden (${state.liveRounds.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        for (round in state.rounds.sortedByDescending { it.sequence }) {
            RoundRow(round = round, names = names, onEdit = onEdit)
        }
    }
}

@Composable
private fun RoundRow(round: ScoredRound, names: Map<Int, String>, onEdit: (String) -> Unit) {
    val colors = MaterialTheme.scoreColors
    val subject = round.round.declarerSeat ?: round.round.ramsch?.loserSeat
    val half = subject?.let { round.score.halfPoints[it] } ?: 0
    val outcome = when {
        round.round.declaration is RamschGame -> "Ramsch"
        round.round.overbid -> "ueberreizt"
        round.round.won -> "gewonnen"
        else -> "verloren"
    }
    Surface(
        onClick = { onEdit(round.id) },
        enabled = !round.deleted,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "${round.sequence + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = round.round.declaration.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    // Das Wort steht neben der Farbe, nicht statt ihr: ein
                    // Ausgang, der sich nur an der Faerbung ablesen laesst, ist
                    // im Kneipenlicht kein Ausgang.
                    text = buildString {
                        append(names[subject] ?: "Platz ?")
                        append(" - ")
                        append(outcome)
                        if (round.deleted) append(" - geloescht")
                        if (round.pendingSync) append(" - wartet auf Sync")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = if (round.deleted) "-" else formatPoints(half),
                style = RotaskatTextStyles.scoreMedium,
                color = when {
                    round.deleted -> colors.sittingOut
                    half > 0 -> colors.gain
                    half < 0 -> colors.loss
                    else -> colors.neutral
                },
            )
        }
    }
}
