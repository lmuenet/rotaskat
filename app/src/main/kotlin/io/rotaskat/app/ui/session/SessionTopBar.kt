package io.rotaskat.app.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import io.rotaskat.app.R
import io.rotaskat.app.ui.common.RotaskatTopBar

/**
 * Kopfzeile des Abends.
 *
 * "Abend beenden" steht im Menue, abgesetzt ganz unten und in Gold statt Rot:
 * als gleichwertiger Textknopf oben rechts war die folgenreichste Aktion die
 * am besten erreichbare. Rot bleibt den Punkten vorbehalten. Der
 * Bestaetigungsdialog dahinter bleibt.
 */
@Composable
internal fun SessionTopBar(
    title: String,
    open: Boolean,
    editing: Boolean,
    empty: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onSettlement: () -> Unit,
    onChangeDealer: () -> Unit,
    onEnd: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val gold = MaterialTheme.colorScheme.primary
    RotaskatTopBar(
        title = title,
        onBack = onBack,
        actions = {
            if (editing) return@RotaskatTopBar
            if (!open) {
                IconButton(onClick = onSettlement) {
                    Icon(painterResource(R.drawable.ic_euro), contentDescription = "Abrechnung")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Weitere Optionen")
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("Punkteverlauf") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_show_chart), contentDescription = null) },
                        onClick = { menu = false; onHistory() },
                    )
                    if (open) {
                        DropdownMenuItem(
                            text = { Text("Zwischenstand abrechnen") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_euro), contentDescription = null) },
                            onClick = { menu = false; onSettlement() },
                        )
                        DropdownMenuItem(
                            text = { Text("Geber ändern") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_swap_horiz), contentDescription = null) },
                            onClick = { menu = false; onChangeDealer() },
                        )
                        // Auf `surfaceContainerHigh` (dem Menuegrund) lag die
                        // Trennlinie mit nur rund 1,1:1 Kontrast fast unsichtbar;
                        // `outline` erreicht die geforderten 3:1 (F8).
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text(if (empty) "Abend verwerfen …" else "Abend beenden …", color = gold) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_flag), contentDescription = null, tint = gold)
                            },
                            onClick = { menu = false; onEnd() },
                        )
                    }
                }
            }
        },
    )
}
