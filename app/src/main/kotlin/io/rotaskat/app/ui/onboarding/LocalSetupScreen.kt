package io.rotaskat.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.common.formatAmount
import io.rotaskat.app.ui.theme.RotaskatDimens

/** Ein Skattisch hat drei oder vier Spieler, mehr Kader braucht es nicht zum Start. */
private const val MIN_PLAYERS = 3
private const val MAX_PLAYERS = 12

/**
 * Der Verein, den es nur auf diesem Geraet gibt.
 *
 * Gefragt wird genau das, was zum Spielen noetig ist: wer mitspielt und um wie
 * viel. Alles andere - Hausregeln, Saison, Rangliste - hat brauchbare
 * Vorgaben und wuerde hier nur zwischen dem Nutzer und der ersten Runde stehen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSetupScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = remember(graph) { OnboardingViewModel.factory(graph) },
    )
    val busy by viewModel.localBusy.collectAsState()

    var clubName by rememberSaveable { mutableStateOf("") }
    var cents by rememberSaveable { mutableStateOf("10") }
    // Vier Zeilen als Vorgabe: das ist die haeufigere Tischgroesse, und eine
    // leere Zeile zu ignorieren ist weniger Arbeit als eine anzulegen.
    val names = remember { mutableStateListOf("", "", "", "") }

    val filled = names.count { it.isBlank().not() }
    val centsValue = cents.toIntOrNull()
    val canSave = filled >= MIN_PLAYERS && centsValue != null && centsValue >= 0 && !busy

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ohne Verein") },
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
            OutlinedTextField(
                value = clubName,
                onValueChange = { clubName = it },
                label = { Text("Name der Runde") },
                placeholder = { Text("Skatrunde") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = cents,
                onValueChange = { input -> cents = input.filter { it.isDigit() }.take(4) },
                label = { Text("Cent je Punkt") },
                supportingText = {
                    Text(
                        if (centsValue == null || centsValue == 0) {
                            "0 bedeutet: nur Punkte, kein Geld."
                        } else {
                            "Ein Spiel mit 48 Punkten sind dann ${formatAmount(48L * centsValue)}."
                        }
                    )
                },
                isError = centsValue == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Wer spielt mit", Modifier.padding(top = RotaskatDimens.sectionSpacing))
            Text(
                text = "Mindestens $MIN_PLAYERS. Wer nicht jeden Abend dabei ist, kann trotzdem " +
                    "hier stehen - pro Abend waehlt ihr aus, wer am Tisch sitzt.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            names.forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { names[index] = it },
                        label = { Text("Spieler ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (names.size > MIN_PLAYERS) {
                        TextButton(onClick = { names.removeAt(index) }) { Text("Weg") }
                    }
                }
            }

            if (names.size < MAX_PLAYERS) {
                TextButton(onClick = { names.add("") }) { Text("+ Spieler") }
            }

            Button(
                onClick = {
                    viewModel.createLocalClub(
                        name = clubName,
                        centsPerPoint = centsValue ?: 0,
                        playerNames = names.toList(),
                        onDone = onDone,
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RotaskatDimens.sectionSpacing),
            ) {
                Text(if (busy) "Moment..." else "Los geht's")
            }

            if (filled < MIN_PLAYERS) {
                Text(
                    text = "Noch ${MIN_PLAYERS - filled} Spieler eintragen.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}
