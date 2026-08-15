package io.rotaskat.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.scoreColors

/**
 * Eine Zeile der Punktetabelle.
 *
 * [detail] traegt die Zahlen, die eine Quote erst lesbar machen - Abende,
 * Runden, Alleinspiele. Sie stehen klein unter dem Namen und nicht in eigenen
 * Spalten: vier Zahlenspalten passen auf ein Telefon nur, wenn man sie so klein
 * setzt, dass sie niemand liest.
 */
@Immutable
data class StandingRow(
    val key: String,
    val name: String,
    val halfPoints: Long,
    val detail: String? = null,
    /** Farbmarke der Zeile, etwa die Linienfarbe aus dem Punkteverlauf. */
    val marker: Color? = null,
)

/**
 * Die Punktetabelle.
 *
 * Dieselben Regeln wie ueberall: Tabellenziffern, immer ein Vorzeichen, Farbe
 * nur als Zweitkanal. Der Rang ist dicht vergeben - gleiche Punktzahl heisst
 * gleicher Rang, sonst behauptet die Tabelle eine Reihenfolge, die das Spiel
 * nicht hergibt.
 */
@Composable
fun StandingsTable(
    rows: List<StandingRow>,
    modifier: Modifier = Modifier,
    showRank: Boolean = true,
    onRowClick: ((StandingRow) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val colors = MaterialTheme.scoreColors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            var rank = 0
            var previous: Long? = null
            rows.forEachIndexed { index, row ->
                if (previous == null || row.halfPoints != previous) rank = index + 1
                previous = row.halfPoints

                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onRowClick != null) {
                                Modifier.clickable { onRowClick(row) }
                            } else {
                                Modifier
                            }
                        )
                        .defaultMinSize(minHeight = RotaskatDimens.tapTarget)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (showRank) {
                        Text(
                            text = "$rank.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(26.dp),
                        )
                    }
                    val marker = row.marker
                    if (marker != null) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = marker,
                            modifier = Modifier.width(4.dp).defaultMinSize(minHeight = 28.dp),
                            content = {},
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = row.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                        if (row.detail != null) {
                            Text(
                                text = row.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                    Text(
                        text = formatPoints(row.halfPoints),
                        style = RotaskatTextStyles.scoreMedium,
                        color = when {
                            row.halfPoints > 0 -> colors.gain
                            row.halfPoints < 0 -> colors.loss
                            else -> colors.neutral
                        },
                    )
                }
            }
        }
    }
}
