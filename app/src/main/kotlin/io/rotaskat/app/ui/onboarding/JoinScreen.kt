package io.rotaskat.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.shared.model.Player

/**
 * Der Vereinsbeitritt, in bis zu drei Schritten.
 *
 * Der mittlere Schritt - "wer von diesen bist du" - existiert, weil der Beitritt
 * eine Spieler-Id aus dem Kader braucht und niemand seine eigene Id auswendig
 * kennt. Der dritte Schritt erscheint nur, wenn vorher ohne Verein gespielt
 * wurde, und entscheidet ueber die bereits gespielten Abende.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = remember(graph) { OnboardingViewModel.factory(graph) },
    )
    val state by viewModel.join.collectAsState()

    var serverUrl by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var deviceLabel by rememberSaveable { mutableStateOf("") }

    // Die Serveradresse aus dem Build ist nur eine Vorbelegung. Steht dort
    // nichts, bleibt das Feld leer statt "null" anzuzeigen.
    LaunchedEffect(Unit) {
        if (serverUrl.isBlank()) serverUrl = graph.settings.serverUrl()
    }

    LaunchedEffect(state) {
        if (state is JoinUiState.Done) onDone()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Verein beitreten") },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            if (state is JoinUiState.ChoosePlayer) viewModel.backToEntry() else onBack()
                        }
                    ) { Text("Zurueck") }
                },
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
            when (val current = state) {
                is JoinUiState.Entry -> EntryStep(
                    serverUrl = serverUrl,
                    onServerUrl = { serverUrl = it },
                    inviteCode = inviteCode,
                    onInviteCode = { inviteCode = it },
                    error = current.error,
                    onContinue = { viewModel.lookup(serverUrl, inviteCode) },
                )

                JoinUiState.Working -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 24.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Moment...", Modifier.padding(start = 12.dp))
                }

                is JoinUiState.ChoosePlayer -> ChoosePlayerStep(
                    clubName = current.club.name,
                    roster = current.club.roster,
                    deviceLabel = deviceLabel,
                    onDeviceLabel = { deviceLabel = it },
                    error = current.error,
                    onPick = { playerId ->
                        viewModel.join(inviteCode, playerId, deviceLabel)
                    },
                )

                is JoinUiState.MapPlayers -> MapPlayersStep(
                    state = current,
                    onAssign = viewModel::assign,
                    onAdopt = viewModel::adopt,
                    onDiscard = viewModel::discardLocal,
                )

                JoinUiState.Done -> Unit
            }
        }
    }
}

@Composable
private fun EntryStep(
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    inviteCode: String,
    onInviteCode: (String) -> Unit,
    error: String?,
    onContinue: () -> Unit,
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrl,
        label = { Text("Serveradresse") },
        placeholder = { Text("https://skat.example.de") },
        supportingText = { Text("Die Adresse, unter der euer Rotaskat-Server laeuft.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = inviteCode,
        onValueChange = onInviteCode,
        label = { Text("Einladungscode") },
        // Kein Autocorrect und keine Autokapitalisierung: der Code ist kein
        // Wort, und eine hilfreiche Tastatur macht daraus sonst ein falsches.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (error != null) ErrorNote(error)

    Button(
        onClick = onContinue,
        enabled = serverUrl.isNotBlank() && inviteCode.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RotaskatDimens.sectionSpacing),
    ) { Text("Weiter") }
}

@Composable
private fun ChoosePlayerStep(
    clubName: String,
    roster: List<Player>,
    deviceLabel: String,
    onDeviceLabel: (String) -> Unit,
    error: String?,
    onPick: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Text(clubName, style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Wer bist du?",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    roster.forEach { player ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (player.id == selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = player.id == selected, onClick = { selected = player.id }),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                RadioButton(selected = player.id == selected, onClick = { selected = player.id })
                Text(player.displayName, Modifier.padding(start = 8.dp))
            }
        }
    }

    OutlinedTextField(
        value = deviceLabel,
        onValueChange = onDeviceLabel,
        label = { Text("Geraetename (optional)") },
        supportingText = { Text("Taucht nur in der Geraeteliste des Vereins auf.") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RotaskatDimens.itemSpacing),
    )

    if (error != null) ErrorNote(error)

    Button(
        onClick = { selected?.let(onPick) },
        enabled = selected != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RotaskatDimens.sectionSpacing, bottom = 24.dp),
    ) { Text("Beitreten") }
}

/**
 * Was mit den lokal gespielten Abenden passiert.
 *
 * Der Bildschirm erscheint nur, wenn es welche gibt. Die Zuordnung ist meist
 * schon ausgefuellt, weil dieselben Leute lokal und im Verein gleich heissen -
 * dann ist es ein Bestaetigen und kein Ausfuellen.
 */
@Composable
private fun MapPlayersStep(
    state: JoinUiState.MapPlayers,
    onAssign: (String, String) -> Unit,
    onAdopt: () -> Unit,
    onDiscard: () -> Unit,
) {
    Text("Beitritt steht", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Auf diesem Geraet ${if (state.sessionCount == 1) "liegt noch ein Abend" else
            "liegen noch ${state.sessionCount} Abende"} aus der Zeit ohne Verein. " +
            "Wer von euren lokalen Spielern ist welches Vereinsmitglied?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    state.localPlayers.forEach { local ->
        SectionLabel(local.displayName, Modifier.padding(top = RotaskatDimens.itemSpacing))
        state.club.roster.forEach { member ->
            val chosen = state.mapping[local.id] == member.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (chosen) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = chosen, onClick = { onAssign(local.id, member.id) }),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    RadioButton(selected = chosen, onClick = { onAssign(local.id, member.id) })
                    Text(member.displayName, Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (state.error != null) ErrorNote(state.error)

    Button(
        onClick = onAdopt,
        enabled = state.complete,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RotaskatDimens.sectionSpacing),
    ) { Text("Abende uebernehmen") }

    OutlinedButton(
        onClick = onDiscard,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) { Text("Verwerfen und neu anfangen") }

    Text(
        text = "Verwerfen loescht die lokalen Abende endgueltig. Sie zeigen auf Spieler, " +
            "die es im Verein nicht gibt, und waeren in jeder Auswertung nur Platzhalter.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
    )
}

@Composable
private fun ErrorNote(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
