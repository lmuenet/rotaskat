package io.rotaskat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.counted
import io.rotaskat.app.ui.common.formatAmount
import io.rotaskat.app.ui.eval.EvalScaffold
import io.rotaskat.app.ui.eval.EvalSection
import io.rotaskat.app.ui.eval.Notice
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player

/**
 * Einstellungen: Verein, Kader, Geld.
 *
 * Nach dem Einstieg war nichts davon mehr erreichbar - kein fuenfter Spieler,
 * kein anderer Cent-Satz und vor allem kein Weg von "ohne Verein" zu einem
 * Verein, obwohl SCOPE.md genau das als den erwarteten Ablauf beschreibt.
 */
@Composable
fun SettingsScreen(actions: RotaskatNavActions, modifier: Modifier = Modifier) {
    val graph = LocalRotaskatGraph.current
    val viewModel: SettingsViewModel = viewModel(factory = remember(graph) { SettingsViewModel.factory(graph) })
    val club by viewModel.club.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val seated by viewModel.seatedPlayerIds.collectAsState()
    val pending by viewModel.pendingSync.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val error by viewModel.error.collectAsState()
    val local = mode == AppMode.LOCAL

    var renaming by remember { mutableStateOf<Player?>(null) }

    EvalScaffold(title = "Einstellungen", onBack = { actions.back() }, modifier = modifier) {
        val current = club ?: return@EvalScaffold

        ClubSection(
            club = current,
            local = local,
            serverUrl = serverUrl,
            pending = pending,
            onJoin = { actions.toJoin() },
        )

        if (error != null) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        RosterSection(
            roster = current.roster,
            local = local,
            seated = seated,
            onAdd = viewModel::addPlayer,
            onRename = { renaming = it },
            onRemove = viewModel::removePlayer,
        )

        CentsSection(
            cents = current.centsPerPoint,
            local = local,
            onChange = viewModel::setCentsPerPoint,
        )
    }

    renaming?.let { player ->
        RenameDialog(
            player = player,
            onDismiss = {
                renaming = null
                viewModel.clearError()
            },
            onRename = { name -> viewModel.renamePlayer(player.id, name) { renaming = null } },
            error = error,
        )
    }
}

@Composable
private fun ClubSection(
    club: Club,
    local: Boolean,
    serverUrl: String?,
    pending: Int,
    onJoin: () -> Unit,
) {
    EvalSection(title = if (local) "Ohne Verein" else "Verein ${club.name}") {
        if (local) {
            Notice(
                "Alles bleibt auf diesem Gerät. Beim Beitritt zu einem Verein werden die " +
                    "bisherigen Abende mitgenommen.",
            )
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth().heightIn(min = RotaskatDimens.tapTarget),
            ) { Text("Einem Verein beitreten") }
        } else {
            Notice(
                buildString {
                    if (serverUrl != null) append("Server: $serverUrl\n")
                    append(
                        if (pending == 0) {
                            "Alles ist mit dem Server abgeglichen."
                        } else {
                            counted(pending, "Runde wartet", "Runden warten") + " auf den Server."
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun RosterSection(
    roster: List<Player>,
    local: Boolean,
    seated: Set<String>,
    onAdd: (String, () -> Unit) -> Unit,
    onRename: (Player) -> Unit,
    onRemove: (String) -> Unit,
) {
    EvalSection(
        title = "Spieler (${roster.size})",
        note = if (local) null else "Der Kader wird auf dem Server gepflegt.",
    ) {
        for (player in roster) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = RotaskatDimens.tapTarget).padding(start = 14.dp, end = 4.dp),
                ) {
                    Text(
                        text = player.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (local) {
                        TextButton(onClick = { onRename(player) }) { Text("umbenennen") }
                        // Wer schon an einem Abend sass, bleibt: sonst zeigte die
                        // Sitzordnung dieses Abends ins Leere.
                        if (player.id !in seated && roster.size > 1) {
                            TextButton(onClick = { onRemove(player.id) }) {
                                Text("entfernen", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (local) {
            var name by rememberSaveable { mutableStateOf("") }
            val submit = { onAdd(name) { name = "" } }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text("Neuer Spieler") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { submit() }, enabled = name.isNotBlank()) { Text("Hinzufügen") }
            }
        }
    }
}

@Composable
private fun CentsSection(cents: Int, local: Boolean, onChange: (Int) -> Unit) {
    EvalSection(
        title = "Geld",
        note = if (local) {
            "Gilt ab dem nächsten Abend. Laufende und beendete Abende behalten ihren Satz."
        } else {
            "Der Satz wird auf dem Server gepflegt."
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        ) {
            if (local) {
                OutlinedButton(
                    onClick = { onChange(cents - 1) },
                    enabled = cents > 0,
                    modifier = Modifier.heightIn(min = RotaskatDimens.tapTarget),
                ) { Text("−") }
            }
            Text(
                text = "$cents Cent je Punkt",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (local) {
                OutlinedButton(
                    onClick = { onChange(cents + 1) },
                    modifier = Modifier.heightIn(min = RotaskatDimens.tapTarget),
                ) { Text("+") }
            }
        }
        Text(
            text = "Ein Spiel mit 48 Punkten sind ${formatAmount(48L * cents)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenameDialog(
    player: Player,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    error: String?,
) {
    var name by rememberSaveable(player.id) { mutableStateOf(player.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${player.displayName} umbenennen") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onRename(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
