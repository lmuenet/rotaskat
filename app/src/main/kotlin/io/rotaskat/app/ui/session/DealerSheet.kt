package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.common.SeatRing
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * "Wer gibt?" als Sheet mit dem Sitzring.
 *
 * Frueher klappte die Geberwahl als zweite, fast identische Namensreihe direkt
 * ueber der Alleinspieler-Reihe auf und war mit ihr verwechselbar. Der
 * Sitzring zeigt die Plaetze so, wie sie am Tisch liegen - dieselbe Form wie
 * beim Anlegen des Abends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DealerSheet(
    seatCount: Int,
    names: Map<Int, String>,
    dealerSeat: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Auf 360x800-Geraeten ist der Inhalt hoeher als die halbe
        // Bildschirmhoehe; ohne das oeffnet das Sheet halb ausgeklappt und
        // schneidet "Wer gibt?" ab.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        ) {
            Text("Wer gibt?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            DealerPicker(seatCount = seatCount, names = names, dealerSeat = dealerSeat, onPick = onPick)
        }
    }
}

/** Der Inhalt des Sheets, ohne Sheet - damit er sich ohne Popup testen laesst. */
@Composable
internal fun DealerPicker(
    seatCount: Int,
    names: Map<Int, String>,
    dealerSeat: Int,
    onPick: (Int) -> Unit,
) {
    SeatRing(seatCount = seatCount) { seat ->
        OptionTile(
            label = names[seat] ?: "Platz ${seat + 1}",
            selected = seat == dealerSeat,
            onClick = { onPick(seat) },
            height = RotaskatDimens.seatSlotSize,
            modifier = Modifier.size(RotaskatDimens.seatSlotSize),
        )
    }
}
