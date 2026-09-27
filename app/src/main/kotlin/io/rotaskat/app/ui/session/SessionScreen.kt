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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.KeepScreenOn
import io.rotaskat.app.ui.common.LocalHaptics
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatShortDate
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
import kotlinx.datetime.TimeZone

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
    // Ohne Verein gibt es keinen Server - "wartet auf Sync" laese sich dort wie
    // ein Fehler, der sich nie aufloest.
    val mode by graph.settings.mode.collectAsState(initial = null)

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var dealerSheet by rememberSaveable { mutableStateOf(false) }
    val empty = state?.liveRounds?.isEmpty() == true

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

            SessionMessage.Discarded -> {
                viewModel.consumeMessage()
                actions.toHome()
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
        topBar = {
            SessionTopBar(
                title = sessionTitle(state, editRoundId),
                open = state?.session?.status == SessionStatus.OPEN,
                editing = editRoundId != null,
                empty = empty,
                loading = state == null,
                onBack = { actions.back() },
                onHistory = { actions.toHistory(sessionId) },
                onSettlement = { actions.toSettlement(sessionId) },
                onChangeDealer = { dealerSheet = true },
                onEnd = { confirmEnd = true },
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
            onDealerClick = { dealerSheet = true },
            onCommit = viewModel::commit,
            onEditRound = { roundId -> actions.toRoundEdit(sessionId, roundId) },
            onCancelEdit = { viewModel.cancelEdit(); actions.back() },
            onDelete = { editRoundId?.let(viewModel::deleteRound) },
            lastChange = lastChange,
            onUndo = viewModel::undoLastChange,
            showSync = mode == AppMode.CLUB,
            snackbar = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
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
        //
        // Ohne eine einzige Runde gibt es nichts abzurechnen. Ein solcher Abend
        // entsteht durch eine vertippte Sitzordnung oder zum Ausprobieren und
        // blieb frueher fuer immer als "0 Runden" in der Uebersicht stehen.
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(if (empty) "Abend verwerfen?" else "Abend beenden?") },
            text = {
                Text(
                    if (empty) {
                        "Es wurde noch keine Runde gespielt. Der Abend verschwindet ganz – " +
                            "bei falscher Sitzordnung danach einfach neu anlegen."
                    } else {
                        "Danach geht es direkt zur Abrechnung. Wer dort noch einen Fehler " +
                            "findet, kann den Abend wieder öffnen."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    if (empty) viewModel.discardSession() else viewModel.endSession()
                }) { Text(if (empty) "Verwerfen" else "Beenden") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Weiterspielen") }
            },
        )
    }

    val sheetDraft = draft
    val sheetState = state
    if (dealerSheet && sheetDraft != null && sheetState != null) {
        DealerSheet(
            seatCount = sheetState.session.seatCount,
            names = seatNames(sheetState.session, roster),
            dealerSeat = sheetDraft.dealerSeat,
            onPick = { seat ->
                haptics.select()
                viewModel.setDealer(seat)
                dealerSheet = false
            },
            onDismiss = { dealerSheet = false },
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
    onDealerClick: () -> Unit,
    onCommit: (won: Boolean) -> Unit,
    onEditRound: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    lastChange: LastChange? = null,
    onUndo: () -> Unit = {},
    showSync: Boolean = false,
    snackbar: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = RotaskatDimens.screenPadding),
    ) {
        // Der Stand bleibt stehen, waehrend die Eingabe darunter scrollt.
        // Er ist die einzige Zahl, die zwischen zwei Runden staendig gesucht
        // wird - ein Stand, der weggescrollt ist, wird stattdessen gefragt.
        //
        // Nach dem Abend gibt niemand mehr - die Marke waere dort nur Rauschen.
        // In der Korrektur zaehlt der Geber der Runde, nicht die Rotation.
        val dealerSeat = when {
            state.session.status != SessionStatus.OPEN -> null
            editing -> draft.dealerSeat
            else -> state.session.dealerSeat
        }
        Scoreboard(
            state = state,
            names = names,
            dealerSeat = dealerSeat,
            onDealerClick = if (editing) null else onDealerClick,
            modifier = Modifier.padding(top = RotaskatDimens.itemSpacing),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        )
                    }
                }

                if (!editing) {
                    RoundHistory(
                        state = state,
                        names = names,
                        showSync = showSync,
                        onEdit = onEditRound,
                    )
                }
            }
            // Fehlermeldungen oben ueber der Eingabe: unten lagen sie genau
            // ueber "Gewonnen" und "Verloren".
            Box(Modifier.align(Alignment.TopCenter)) { snackbar() }
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
 * Titel des Abends: Datum und die Runde, die gerade eingegeben wird. In der
 * Korrektur die Nummer der korrigierten Runde, gezaehlt wie in der Rundenliste.
 */
internal fun sessionTitle(
    state: SessionState?,
    editRoundId: String?,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (state == null) return "Abend"
    if (editRoundId != null) {
        val number = state.liveRounds.indexOfFirst { it.id == editRoundId } + 1
        return if (number > 0) "Runde $number korrigieren" else "Runde korrigieren"
    }
    val date = formatShortDate(state.session.startedAt, zone)
    return if (state.session.status == SessionStatus.OPEN) {
        "$date · Runde ${state.liveRounds.size + 1}"
    } else {
        "$date · beendet"
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
    showSync: Boolean,
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
            RoundRow(round = round, number = index + 1, names = names, editable = editable, showSync = showSync, onEdit = onEdit)
        }
    }
}

@Composable
private fun RoundRow(
    round: ScoredRound,
    number: Int,
    names: Map<Int, String>,
    editable: Boolean,
    showSync: Boolean,
    onEdit: (String) -> Unit,
) {
    val colors = MaterialTheme.scoreColors
    val subject = round.round.subjectSeat
    val half = subject?.let { round.score.halfPoints[it] } ?: 0
    Surface(
        onClick = { onEdit(round.id) },
        enabled = editable,
        shape = RoundedCornerShape(RotaskatDimens.tileCorner),
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
                        if (showSync && round.pendingSync) append(" · wartet auf Sync")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = formatPoints(half),
                style = RotaskatTextStyles.scoreMedium,
                color = colors.forValue(half.toLong()),
            )
        }
    }
}
