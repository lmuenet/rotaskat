package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
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
import io.rotaskat.app.ui.round.RoundDraft
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
    val lastChange by viewModel.lastChange.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
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
                viewModel.consumeMessage()
                if (current.closesEdit) {
                    // Eine abgeschlossene Korrektur - oder ein Loeschen - fuehrt
                    // SOFORT zurueck. Das Undo steht dann im Abend in der Zeile
                    // ueber der Eingabe, nicht in einer Leiste, auf die hier
                    // gewartet werden muesste.
                    actions.back()
                } else {
                    // Die naechste Runde beginnt oben, dort steht auch die Zeile
                    // mit dem Undo. Wer fuer Spitzen oder Zusaetze gescrollt
                    // hatte, sah sonst die Alleinspieler-Auswahl nicht mehr.
                    scrollState.animateScrollTo(0)
                }
            }

            SessionMessage.Undone -> {
                haptics.select()
                viewModel.consumeMessage()
            }

            SessionMessage.Ended -> {
                viewModel.consumeMessage()
                actions.toSettlementAfterEnd(sessionId)
            }

            is SessionMessage.Failed -> {
                haptics.failure()
                viewModel.consumeMessage()
                snackbarHostState.showSnackbar(current.text)
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
                        TextButton(onClick = { actions.back() }) { Text("Zurück") }
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

        SessionBody(
            state = current,
            draft = currentDraft,
            names = seatNames(current.session, roster),
            editing = editRoundId != null,
            scrollState = scrollState,
            onDraftChange = viewModel::updateDraft,
            onDealerChange = viewModel::setDealer,
            onCommit = viewModel::commit,
            onEditRound = { roundId -> actions.toRoundEdit(sessionId, roundId) },
            onCancelEdit = { viewModel.cancelEdit(); actions.back() },
            onDelete = { editRoundId?.let(viewModel::deleteRound) },
            lastChange = lastChange,
            onUndo = viewModel::undoLastChange,
            // Die Tastatur der Ramsch-Augen schiebt die Ergebnisleiste mit nach
            // oben, statt das zweite und dritte Feld zu verdecken. Das Fenster
            // selbst wird bei targetSdk 35 nicht mehr verkleinert.
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        )
    }

    if (confirmEnd) {
        // Die einzige Rueckfrage der App, und zwar bewusst: "Abend beenden"
        // passiert genau einmal pro Abend, ist nur ueber die Abrechnung umkehrbar und
        // liegt in der Kopfzeile direkt neben nichts. Alle Rueckfragen, die
        // dreissigmal am Abend kaemen, gibt es dagegen nicht.
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("Abend beenden?") },
            text = {
                Text(
                    "Danach geht es direkt zur Abrechnung. Wer dort noch einen Fehler " +
                        "findet, kann den Abend wieder öffnen.",
                )
            },
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
 * Der Inhalt des Abends ohne ViewModel und Navigation.
 *
 * Getrennt, damit sich das Platzbudget des Vier-Tap-Pfads ohne Datenbank auf
 * einer festen Bildschirmgroesse pruefen laesst - siehe `SessionLayoutTest`.
 */
@Composable
internal fun SessionBody(
    state: SessionState,
    draft: RoundDraft,
    names: Map<Int, String>,
    editing: Boolean,
    scrollState: ScrollState,
    onDraftChange: ((RoundDraft) -> RoundDraft) -> Unit,
    onDealerChange: (Int) -> Unit,
    onCommit: (won: Boolean) -> Unit,
    onEditRound: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    lastChange: LastChange? = null,
    onUndo: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = RotaskatDimens.screenPadding),
    ) {
        // Der Stand bleibt stehen, waehrend die Eingabe darunter scrollt.
        // Er ist die einzige Zahl, die zwischen zwei Runden staendig gesucht
        // wird - ein Stand, der weggescrollt ist, wird stattdessen gefragt.
        Scoreboard(
            state = state,
            names = names,
            modifier = Modifier.padding(top = RotaskatDimens.itemSpacing),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(SessionTags.SCROLL_AREA)
                .verticalScroll(scrollState)
                .padding(top = RotaskatDimens.itemSpacing, bottom = RotaskatDimens.sectionSpacing),
            verticalArrangement = Arrangement.spacedBy(RotaskatDimens.sectionSpacing),
        ) {
            if (state.session.status == SessionStatus.CLOSED) {
                Text(
                    text = "Dieser Abend ist beendet. Wer noch eine Runde korrigieren muss: " +
                        "in der Abrechnung „Abend wieder öffnen“.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Die Undo-Zeile sitzt mit dem kleinen Abstand direkt ueber der
                // Eingabe: sie gehoert dazu und soll die Spitzen nicht unter die
                // Falz schieben.
                Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
                    if (!editing && lastChange != null) {
                        LastChangeRow(change = lastChange, state = state, names = names, onUndo = onUndo)
                    }
                    RoundEntryPanel(
                        draft = draft,
                        seatNames = names,
                        onDraftChange = onDraftChange,
                        onDealerChange = onDealerChange,
                    )
                }
            }

            if (!editing) {
                RoundHistory(
                    state = state,
                    names = names,
                    onEdit = onEditRound,
                )
            }
        }

        // Spielwert und Ergebnisknoepfe liegen AUSSERHALB des
        // Scrollbereichs. Sie sind der vierte Tap des Vier-Tap-Pfads und
        // stehen deshalb immer an derselben Stelle, egal wie weit die
        // Eingabe darueber gewachsen ist.
        if (state.session.status == SessionStatus.OPEN) {
            RoundCommitBar(
                draft = draft,
                onCommit = onCommit,
                onCancelEdit = if (editing) onCancelEdit else null,
                onDelete = if (editing) onDelete else null,
                modifier = Modifier.padding(bottom = RotaskatDimens.screenPadding),
            )
        }
    }
}

/** Test-Tags fuer den Layout-Test. */
internal object SessionTags {
    const val SCROLL_AREA = "session-scroll"
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
    // Nach dem Abend gibt niemand mehr - das "gibt" waere dort nur Rauschen.
    val open = state.session.status == SessionStatus.OPEN
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp)) {
            for (seat in 0 until state.session.seatCount) {
                val half = state.totals[seat] ?: 0L
                val sittingOut = open && seat == state.rotation.sittingOutSeat
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    // "gibt" steht in der Namenszeile statt in einer eigenen
                    // dritten Zeile. Die Zeile kostete auf dem Geraet genau den
                    // Platz, der fuer die Spitzen fehlte. Der Name kuerzt sich
                    // notfalls, das Wort bleibt stehen.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = names[seat] ?: "Platz ${seat + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sittingOut) colors.sittingOut else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (sittingOut) {
                            Text(
                                text = " · gibt",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.sittingOut,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = formatPoints(half),
                        style = RotaskatTextStyles.scoreLarge,
                        color = when {
                            half > 0 -> colors.gain
                            half < 0 -> colors.loss
                            else -> colors.neutral
                        },
                    )
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
 *
 * Geloeschte Runden stehen NICHT in der Liste. Der Tombstone ist fuer den Sync
 * da, nicht fuer den Tisch - eine zurueckgenommene Runde wurde aus Sicht der
 * Spieler nie gespielt. Die Nummer kommt deshalb aus der Position unter den
 * lebenden Runden und nicht aus `sequence`, sonst haette die Liste Luecken.
 */
@Composable
private fun RoundHistory(
    state: SessionState,
    names: Map<Int, String>,
    onEdit: (String) -> Unit,
) {
    val live = state.liveRounds
    if (live.isEmpty()) return
    // Im beendeten Abend fuehrte ein Tap frueher in einen leeren
    // Korrekturbildschirm ohne Eingabe - eine Sackgasse. Korrigiert wird nach
    // dem Wiederoeffnen aus der Abrechnung.
    val editable = state.session.status == SessionStatus.OPEN
    Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Text(
            text = "Runden (${live.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        for ((index, round) in live.withIndex().reversed()) {
            RoundRow(round = round, number = index + 1, names = names, editable = editable, onEdit = onEdit)
        }
    }
}

@Composable
private fun RoundRow(
    round: ScoredRound,
    number: Int,
    names: Map<Int, String>,
    editable: Boolean,
    onEdit: (String) -> Unit,
) {
    val colors = MaterialTheme.scoreColors
    val subject = round.round.subjectSeat
    val half = subject?.let { round.score.halfPoints[it] } ?: 0
    Surface(
        onClick = { onEdit(round.id) },
        enabled = editable,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "$number",
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
                        append(" · ")
                        append(round.round.outcomeLabel())
                        if (round.pendingSync) append(" · wartet auf Sync")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = formatPoints(half),
                style = RotaskatTextStyles.scoreMedium,
                color = when {
                    half > 0 -> colors.gain
                    half < 0 -> colors.loss
                    else -> colors.neutral
                },
            )
        }
    }
}
