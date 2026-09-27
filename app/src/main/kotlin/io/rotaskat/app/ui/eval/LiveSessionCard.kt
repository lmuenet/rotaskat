package io.rotaskat.app.ui.eval

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rotaskat.app.R
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatShortDate
import io.rotaskat.app.ui.common.formatTime
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.app.ui.theme.scoreColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Ob die oberen beiden Plaetze der (absteigend sortierten) Rangliste
 * gleichauf stehen. Gemeinsame Grundlage von [leaderLine] und [winnerLabel],
 * damit die Gleichstandspruefung nur an einer Stelle steht.
 */
private fun topIsTied(sorted: List<Pair<String, Long>>): Boolean {
    val first = sorted.getOrNull(0) ?: return false
    val second = sorted.getOrNull(1) ?: return false
    return second.second == first.second
}

/**
 * "seit HH:MM" fuer einen Abend von heute, sonst "seit <Kurzdatum>".
 *
 * Eine Uhrzeit ohne Datum ist nur so lange eindeutig, wie der Abend heute
 * begonnen hat. Laeuft er seit einem fruehreren Tag, waere "seit 18:14" eine
 * falsche Aussage ueber die Dauer.
 */
internal fun sinceLabel(startedAt: Instant, now: Instant, zone: TimeZone): String {
    val startedDate = startedAt.toLocalDateTime(zone).date
    val nowDate = now.toLocalDateTime(zone).date
    return if (startedDate == nowDate) {
        "seit ${formatTime(startedAt, zone)}"
    } else {
        "seit ${formatShortDate(startedAt, zone)}"
    }
}

/**
 * Wer vorne liegt, in Worten. Bei Gleichstand an der Spitze kein Name - ein
 * willkuerlich herausgegriffener Spieler waere eine falsche Aussage.
 *
 * "Noch keine Runde" gilt nur, solange tatsaechlich keine Runde gespielt
 * wurde ([roundsPlayed]) - ein Alle-null-Stand nach gespielten Runden ist ein
 * echter Gleichstand, keine leere Tabelle.
 */
internal fun leaderLine(ranking: List<Pair<String, Long>>, roundsPlayed: Boolean): String {
    val first = ranking.getOrNull(0) ?: return "Noch keine Runde"
    if (ranking.all { it.second == 0L }) {
        return if (roundsPlayed) "Gleichstand" else "Noch keine Runde"
    }
    val second = ranking.getOrNull(1)
    return if (second != null && second.second == first.second) "Gleichstand" else "${first.first} führt"
}

/**
 * Wer den Abend gewonnen hat, in Worten. Bei Gleichstand an der Spitze kein
 * Name - `maxByOrNull` griffe sonst willkuerlich einen der Gleichauf-Spieler
 * heraus und benaennte ihn faelschlich als Sieger.
 */
internal fun winnerLabel(ranking: List<Pair<String, Long>>): String {
    val sorted = ranking.sortedByDescending { it.second }
    val first = sorted.getOrNull(0) ?: return "Gleichstand"
    return if (topIsTied(sorted)) "Gleichstand" else first.first
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Ohne das faende TalkBack im Kleeblatt ein eigenes,
                    // unverstaendliches Zeichen zum Vorlesen.
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = "Abend läuft" },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_suit_clubs),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Abend läuft",
                        style = RotaskatTextStyles.sectionLabel,
                        color = colors.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(
                    text = "Runde ${state.liveRounds.size + 1} · ${
                        sinceLabel(state.session.startedAt, Clock.System.now(), TimeZone.currentSystemDefault())
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.labelMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    text = leaderLine(ranking, roundsPlayed = state.liveRounds.isNotEmpty()),
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
