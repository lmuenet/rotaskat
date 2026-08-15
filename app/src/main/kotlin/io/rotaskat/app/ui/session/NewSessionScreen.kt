package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.ScaleSelector
import io.rotaskat.app.ui.common.SeatRing
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.common.initials
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.shared.model.Player
import kotlinx.coroutines.launch

/**
 * Der Anpfiff eines Abends: wer sitzt wo, und wer gibt.
 *
 * Die Sitzordnung wird als Tisch gezeigt und nicht als Liste, aus demselben
 * Grund wie in der Rundeneingabe: eine Nummer im Kopf auf einen Stuhl
 * abzubilden ist genau der Zwischenschritt, an dem am Tisch der falsche Spieler
 * getippt wird.
 *
 * Der Geber wird hier einmal festgelegt und danach nie wieder gefragt - die App
 * schreibt ihn nach jeder Runde selbst fort.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(
    onStarted: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val scope = rememberCoroutineScope()
    val roster by graph.repository.observeRoster().collectAsState(initial = emptyList())

    var seatCount by remember { mutableIntStateOf(4) }
    var dealerSeat by remember { mutableIntStateOf(0) }
    // Sitzplatz -> Spieler. Bewusst nicht vorbelegt: wer am Tisch sitzt, wechselt
    // von Abend zu Abend, und eine falsche Vorbelegung wird uebersehen.
    var seats by remember { mutableStateOf(mapOf<Int, String>()) }
    var activeSeat by remember { mutableStateOf<Int?>(0) }
    var busy by remember { mutableStateOf(false) }

    // Wird der Tisch kleiner, fallen die hinteren Plaetze weg - sonst bliebe ein
    // Spieler auf einem Stuhl sitzen, den es nicht mehr gibt.
    val cleanedSeats = seats.filterKeys { it < seatCount }
    val complete = (0 until seatCount).all { cleanedSeats[it] != null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Neuer Abend") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Zurueck") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(RotaskatDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        ) {
            if (roster.size < 3) {
                Text(
                    text = "Im Kader stehen erst ${roster.size} Spieler. Skat braucht mindestens drei.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            val seatCountOptions = listOf(3, 4)
            SectionLabel("Wie viele am Tisch")
            ScaleSelector(
                options = seatCountOptions,
                selectedIndex = seatCountOptions.indexOf(seatCount),
                onSelect = { index ->
                    val count = seatCountOptions[index]
                    seatCount = count
                    seats = seats.filterKeys { it < count }
                    if (dealerSeat >= count) dealerSeat = 0
                    activeSeat = (0 until count).firstOrNull { seats[it] == null }
                },
                label = { "$it Spieler" },
                columns = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = if (seatCount == 4) {
                    "Bei vier Spielern setzt der Geber aus."
                } else {
                    "Bei drei Spielern spielen alle jede Runde."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                SeatRing(
                    seatCount = seatCount,
                    center = {
                        Text(
                            text = if (activeSeat != null) "Platz ${activeSeat!! + 1}\nwaehlen" else "Tisch",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    },
                ) { seat ->
                    SeatSlot(
                        label = cleanedSeats[seat]?.let { id ->
                            roster.firstOrNull { it.id == id }?.displayName
                        },
                        seatNumber = seat + 1,
                        isDealer = seat == dealerSeat,
                        isActive = seat == activeSeat,
                        onClick = { activeSeat = seat },
                    )
                }
            }

            SectionLabel("Wer sitzt auf Platz ${(activeSeat ?: 0) + 1}")
            roster.forEach { player ->
                val takenBy = cleanedSeats.entries.firstOrNull { it.value == player.id }?.key
                PlayerRow(
                    player = player,
                    takenSeat = takenBy,
                    onClick = {
                        val target = activeSeat ?: return@PlayerRow
                        // Ein Spieler sitzt auf hoechstens einem Stuhl. Wird er
                        // umgesetzt, wird sein alter Platz frei statt ihn zu
                        // verdoppeln.
                        seats = seats.filterValues { it != player.id } + (target to player.id)
                        activeSeat = (0 until seatCount).firstOrNull { seats[it] == null && it != target }
                    },
                )
            }

            SectionLabel("Wer gibt zuerst", Modifier.padding(top = RotaskatDimens.sectionSpacing))
            ScaleSelector(
                // Die Optionen SIND die Sitzplaetze, Index und Platz fallen also
                // zusammen.
                options = (0 until seatCount).toList(),
                selectedIndex = dealerSeat,
                onSelect = { dealerSeat = it },
                label = { seat ->
                    cleanedSeats[seat]?.let { id -> roster.firstOrNull { it.id == id }?.displayName }
                        ?: "Platz ${seat + 1}"
                },
                columns = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        try {
                            val id = graph.repository.startSession(
                                seatCount = seatCount,
                                seats = cleanedSeats,
                                dealerSeat = dealerSeat,
                            )
                            onStarted(id)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = complete && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RotaskatDimens.sectionSpacing, bottom = 24.dp),
            ) {
                Text(if (complete) "Abend starten" else "Noch nicht alle Plaetze besetzt")
            }
        }
    }
}

@Composable
private fun SeatSlot(
    label: String?,
    seatNumber: Int,
    isDealer: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer
            label != null -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.size(RotaskatDimens.seatSlotSize),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = label?.let { initials(it) } ?: "$seatNumber",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = label ?: "frei",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDealer) {
                Text(
                    text = "gibt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: Player,
    takenSeat: Int?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (takenSeat != null) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(player.displayName, Modifier.weight(1f))
            if (takenSeat != null) {
                Text(
                    text = "Platz ${takenSeat + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
