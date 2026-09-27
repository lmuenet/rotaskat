package io.rotaskat.app.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatTime
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.app.ui.theme.scoreColors

/**
 * Wer vorne liegt, in Worten. Bei Gleichstand an der Spitze kein Name - ein
 * willkuerlich herausgegriffener Spieler waere eine falsche Aussage.
 */
internal fun leaderLine(ranking: List<Pair<String, Long>>): String {
    val first = ranking.getOrNull(0) ?: return "Noch keine Runde"
    if (ranking.all { it.second == 0L }) return "Noch keine Runde"
    val second = ranking.getOrNull(1)
    return if (second != null && second.second == first.second) "Gleichstand" else "${first.first} führt"
}

/**
 * Der laufende Abend als Karte oben auf der Startseite.
 *
 * Die Fuehrungszahl steht neben dem Namen, nicht allein: frueher stand "+50"
 * ohne Namen rechts, und wer fuehrt, stand klein in der Zeile darunter.
 */
@Composable
internal fun LiveSessionCard(
    state: SessionState,
    names: Map<Int, String>,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val score = MaterialTheme.scoreColors
    val accent = MaterialTheme.accentColors
    val ranking = (0 until state.session.seatCount)
        .map { seat -> (names[seat] ?: "Platz ${seat + 1}") to (state.totals[seat] ?: 0L) }
        .sortedByDescending { it.second }
    val leader = ranking.first()

    Surface(
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "♣ Abend läuft",
                    style = RotaskatTextStyles.sectionLabel,
                    color = colors.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Runde ${state.liveRounds.size + 1} · seit ${formatTime(state.session.startedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.labelMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = leaderLine(ranking),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPoints(leader.second),
                    style = RotaskatTextStyles.scoreLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    color = when {
                        leader.second > 0 -> score.gain
                        leader.second < 0 -> score.loss
                        else -> score.neutral
                    },
                )
            }
            Text(
                text = ranking.drop(1).joinToString(" · ") { "${it.first} ${formatPoints(it.second)}" },
                style = RotaskatTextStyles.compact,
                color = colors.onSurfaceVariant,
                maxLines = 2,
            )
            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = RotaskatDimens.tapTarget),
            ) {
                Text("Weiterspielen", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
