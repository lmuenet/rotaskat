package io.rotaskat.app.ui.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.OptionGrid
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.common.SectionLabel
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.shared.scoring.Scoring

/**
 * Die Ramsch-Eingabe.
 *
 * Sie ist bewusst kein Schnellpfad: Ramsch kommt selten vor, und die Augen
 * muessen ohnehin gezaehlt werden. Die Zahlen aller drei aktiven Spieler
 * einzugeben kostet ein paar Sekunden mehr und kauft dafuer zwei Dinge, die
 * sonst fehlen wuerden - die Summenprobe auf 120 und die Erkennung eines
 * Augengleichstands.
 *
 * Bei Gleichstand entscheidet der Tisch. Die App fragt und raet nicht; eine
 * automatische Regel dafuer gibt es in den Hausregeln bewusst nicht.
 */
@Composable
fun RamschPanel(
    draft: RoundDraft,
    seatNames: Map<Int, String>,
    onChange: ((RamschDraft) -> RamschDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ramsch = draft.ramsch
    val active = draft.activeSeats
    val total = ramsch.total(active)
    val durchmarsch = ramsch.durchmarschSeat

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {

        if (durchmarsch == null) {
            SectionLabel("Augen")
            for (seat in active) {
                OutlinedTextField(
                    value = ramsch.cardPoints[seat].orEmpty(),
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        val capped = digits.toIntOrNull()
                            ?.coerceAtMost(Scoring.MAX_CARD_POINTS)
                            ?.toString()
                            ?: ""
                        onChange { it.withPoints(seat, capped) }
                    },
                    label = { Text(seatNames[seat] ?: "Platz ${seat + 1}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Die Summenprobe ist geschenkt: 120 Augen liegen im Spiel, mehr
            // oder weniger heisst Tippfehler und nicht Hausregel. Sie steht
            // auch dann da, wenn noch Zahlen fehlen - sonst waere das
            // Sicherheitsnetz genau in dem Fall unsichtbar, in dem die Eingabe
            // noch gar nicht vollstaendig ist.
            val complete = ramsch.complete(active)
            val missing = active.filterNot { ramsch.entered(it) }
            Text(
                text = when {
                    !complete -> "Es fehlen noch die Augen von " +
                        missing.joinToString(" und ") { seatNames[it] ?: "Platz ${it + 1}" }

                    total == Scoring.MAX_CARD_POINTS -> "Summe $total - stimmt"
                    else -> "Summe $total statt ${Scoring.MAX_CARD_POINTS} - bitte nachzaehlen"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (complete && total == Scoring.MAX_CARD_POINTS) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            if (complete && ramsch.tied(active)) {
                SectionLabel("Augengleichstand - wer gilt als Verlierer?")
                val tiedSeats = ramsch.leaders(active)
                OptionGrid(columns = tiedSeats.size.coerceAtLeast(1), itemCount = tiedSeats.size) { index ->
                    val seat = tiedSeats[index]
                    OptionTile(
                        label = seatNames[seat] ?: "Platz ${seat + 1}",
                        selected = ramsch.chosenLoser == seat,
                        onClick = { onChange { it.copy(chosenLoser = seat) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionLabel("Schuebe")
            OptionGrid(columns = Scoring.MAX_PUSHES + 1, itemCount = Scoring.MAX_PUSHES + 1) { index ->
                OptionTile(
                    label = "$index",
                    selected = ramsch.pushes == index,
                    onClick = { onChange { it.copy(pushes = index) } },
                    height = RotaskatDimens.tapTarget,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = "Jungfrau (ein Spieler ohne Stich)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = ramsch.jungfrau,
                    onCheckedChange = { checked -> onChange { it.copy(jungfrau = checked) } },
                )
            }
        }

        SectionLabel("Durchmarsch")
        OptionGrid(columns = active.size + 1, itemCount = active.size + 1) { index ->
            if (index == 0) {
                OptionTile(
                    label = "keiner",
                    selected = durchmarsch == null,
                    onClick = { onChange { it.copy(durchmarschSeat = null) } },
                    height = RotaskatDimens.tapTarget,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val seat = active[index - 1]
                OptionTile(
                    label = seatNames[seat] ?: "Platz ${seat + 1}",
                    selected = durchmarsch == seat,
                    onClick = { onChange { it.copy(durchmarschSeat = seat) } },
                    height = RotaskatDimens.tapTarget,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
