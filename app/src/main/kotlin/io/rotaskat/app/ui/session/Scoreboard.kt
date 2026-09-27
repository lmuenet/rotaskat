package io.rotaskat.app.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rotaskat.app.R
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors

/**
 * Der Stand.
 *
 * Punkte immer mit Vorzeichen und in Tabellenziffern; die Farbe ist der
 * Zweitkanal, nie der einzige Traeger. Wer aussetzt, steht mit dabei - eine
 * Spalte, die verschwindet und wiederkommt, laesst die Tabelle bei jedem Blick
 * anders aussehen.
 *
 * Der Geber steht in Gold mit einem Kartensymbol in der Namenszeile statt mit
 * "· gibt" dahinter: das Wort kuerzte im Geraetetest den Namen ("Joha… · gibt"),
 * und eine eigene dritte Zeile kostete genau den Platz, der fuer die Spitzen
 * fehlte. Am Vierertisch ist die Spalte zusaetzlich gedimmt - der Geber setzt
 * dort aus. Ein Tap auf den Geber oeffnet die Geberwahl; das sind zwei Taps bis
 * zur Korrektur, wie vorher mit der aufklappbaren Reihe.
 *
 * [dealerSeat] `null` heisst: keine Marke (beendeter Abend).
 */
@Composable
internal fun Scoreboard(
    state: SessionState,
    names: Map<Int, String>,
    dealerSeat: Int?,
    onDealerClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.scoreColors
    val gold = MaterialTheme.colorScheme.primary
    val seatCount = state.session.seatCount
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            for (seat in 0 until seatCount) {
                val half = state.totals[seat] ?: 0L
                val name = names[seat] ?: "Platz ${seat + 1}"
                val points = formatPoints(half)
                val isDealer = seat == dealerSeat
                val sitsOut = isDealer && seatCount == 4
                val interaction = if (isDealer && onDealerClick != null) {
                    Modifier.clickable(onClickLabel = "Geber ändern", onClick = onDealerClick)
                } else {
                    Modifier
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {
                            contentDescription = if (isDealer) "$name, $points, gibt" else "$name, $points"
                            if (isDealer && onDealerClick != null) {
                                onClick(label = "Geber ändern") { onDealerClick(); true }
                            }
                        }
                        .then(interaction)
                        .alpha(if (sitsOut) 0.6f else 1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = name,
                            style = RotaskatTextStyles.standName,
                            color = if (isDealer) gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isDealer) {
                            Icon(
                                painter = painterResource(R.drawable.ic_style),
                                contentDescription = null,
                                tint = gold,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = points,
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
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}
