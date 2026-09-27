package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.rotaskat.app.R
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.label
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.scoring.Scoring

/**
 * Die letzte Aenderung am Abend, mit Undo.
 *
 * Ersetzt die Snackbar, die nur vier Sekunden stand, genau ueber "Verloren"
 * lag und nur "Gewonnen gespeichert" sagte. Die Zeile steht oben ueber der
 * Eingabe - weit weg von den Ergebnisknoepfen - und bleibt, bis die naechste
 * Aenderung sie ersetzt. Sie nennt Spieler, Spiel und Punkte, damit sich am
 * Tisch pruefen laesst, ob die richtige Runde gespeichert wurde.
 */
@Composable
internal fun LastChangeRow(
    change: LastChange,
    state: SessionState,
    names: Map<Int, String>,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val round = change.round
    val subject = round.subjectSeat
    val headline = when {
        change.undone -> "Zurückgenommen"
        change.kind == LastChange.Kind.SAVED -> "Gespeichert"
        change.kind == LastChange.Kind.CORRECTED -> "Geändert"
        else -> "Gelöscht"
    }
    val description = buildString {
        append(names[subject] ?: "Platz ?")
        append(" · ")
        append(round.declaration.label())
        append(" · ")
        append(round.outcomeLabel())
    }
    val half = subject?.let { seat ->
        runCatching { Scoring.score(round, state.session.scoring).halfPoints[seat] }.getOrNull()
    }
    val showPoints = half != null && !change.undone && change.kind != LastChange.Kind.DELETED
    val colors = MaterialTheme.scoreColors

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Feste Hoehe in beiden Zustaenden: nach "Zurueckgenommen" fehlt der
        // Knopf, und die Zeile wurde flacher - das Layout darunter sprang.
        modifier = modifier.fillMaxWidth().height(52.dp).testTag(LastChangeTags.ROW),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
        ) {
            // Eine Zeile: die Zeile steht ueber der Eingabe und kostet damit
            // genau den Platz, den der Vier-Tap-Pfad braucht.
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(headline)
                        append(": ")
                    }
                    append(description)
                },
                style = RotaskatTextStyles.compact,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showPoints && half != null) {
                Text(
                    text = formatPoints(half),
                    style = RotaskatTextStyles.scoreMedium,
                    color = when {
                        half > 0 -> colors.gain
                        half < 0 -> colors.loss
                        else -> colors.neutral
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (!change.undone) {
                TextButton(onClick = onUndo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Rückgängig")
                }
            }
        }
    }
}

/** Test-Tag der Zeile. */
internal object LastChangeTags {
    const val ROW = "last-change"
}
