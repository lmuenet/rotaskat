package io.rotaskat.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * Eine Reihe waehlbarer Kacheln, die bei Bedarf zur Seite scrollt.
 *
 * Fuer Zeitraeume und Spieler: wenige Optionen, eine davon aktiv. Im Raster
 * brauchten vier Spieler zwei Reihen und schoben die Zahlen nach unten.
 */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        for (option in options) {
            OptionTile(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                height = RotaskatDimens.tapTarget,
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }
    }
}
