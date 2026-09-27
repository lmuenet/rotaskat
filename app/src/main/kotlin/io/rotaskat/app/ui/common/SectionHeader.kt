package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors

/**
 * Ueberschrift eines Abschnitts, optional mit Erklaerung hinter einem ⓘ.
 *
 * Die Erklaerungen standen frueher als Absatz ueber jeder Tabelle und lasen
 * sich wie Entwickler-Kommentare. Wer die Zahl kennt, braucht sie nicht; wer
 * sie nicht kennt, findet sie mit einem Tap.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, info: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.accentColors.labelMuted
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = RotaskatTextStyles.sectionLabel,
            color = muted,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (info != null) {
            IconButton(onClick = { open = true }, modifier = Modifier.size(RotaskatDimens.tapTarget)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Erklärung zu $title",
                    tint = muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    if (open && info != null) {
        InfoSheet(title = title, text = info, onDismiss = { open = false })
    }
}

/** Die Erklaerung zu einem Abschnitt, voll aufgeklappt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, text: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        InfoSheetContent(title = title, text = text)
    }
}

/**
 * Der Inhalt von [InfoSheet], eigenstaendig, damit Robolectric ihn ohne das
 * eigene Fenster von `ModalBottomSheet` pruefen kann.
 */
@Composable
internal fun InfoSheetContent(title: String, text: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
