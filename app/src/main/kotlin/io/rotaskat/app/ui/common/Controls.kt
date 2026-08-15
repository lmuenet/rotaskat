package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * Die eine Auswahlflaeche, aus der fast die gesamte Eingabe besteht.
 *
 * Bewusst kein `FilterChip` und kein `Button`: beide bringen ihre eigene
 * Mindesthoehe von 32 bzw. 40dp mit, die dann per Modifier wieder
 * ueberschrieben werden muesste. Hier ist die Groesse das Wesentliche, nicht
 * eine Abweichung vom Standard.
 *
 * [enabled] `false` heisst hier nicht "gerade nicht sinnvoll", sondern
 * "strukturell unmoeglich" - etwa der Aussetzende in der Spielerauswahl. Die
 * Flaeche bleibt sichtbar, damit die Sitzordnung stimmt, ist aber nicht
 * antippbar. Das faengt zwei validate()-Fehler ab, bevor sie entstehen.
 */
@Composable
fun OptionTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryLabel: String? = null,
    height: Dp = RotaskatDimens.bigTapTarget,
    selectedColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fill = when {
        !enabled -> colors.surfaceContainerLowest
        selected -> selectedColor ?: colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> selectedColor?.let { colors.surface } ?: colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        color = fill,
        contentColor = content,
        // Der Rahmen ist der zweite Kanal neben der Fuellfarbe. Im Kneipenlicht
        // ist ein Farbunterschied allein zu wenig, um eine getroffene Auswahl
        // im Vorbeisehen zu erkennen.
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(2.dp, colors.primary)
        } else {
            null
        },
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                if (secondaryLabel != null) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Gleichmaessiges Raster mit fester Spaltenzahl.
 *
 * Bewusst von Hand aus Zeilen gebaut statt mit einem Flow-Layout: die Position
 * einer Kachel darf sich nicht aendern, wenn ihre Beschriftung laenger wird.
 * Wer "Kreuz" blind an derselben Stelle tippt, soll dort auch "Kreuz" treffen.
 */
@Composable
fun OptionGrid(
    columns: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = RotaskatDimens.itemSpacing,
    item: @Composable RowScope.(index: Int) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        val rowCount = (itemCount + columns - 1) / columns
        for (row in 0 until rowCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    if (index < itemCount) item(index) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Eine geordnete, exklusive Skala.
 *
 * Der Kern des Entwurfs fuer die Zusaetze: statt sechs unabhaengiger Schalter
 * mit 64 Kombinationen - von denen die meisten ungueltig sind - gibt es zwei
 * Skalen, auf denen jede Stellung gueltig ist. Ungueltige Zustaende sind damit
 * nicht validiert, sondern unerreichbar.
 *
 * [floorIndex] ist die Untergrenze, die eine andere Skala erzwingt: sagt jemand
 * Schwarz an, IST Schwarz erreicht, und "normal" darf nicht mehr waehlbar sein.
 * Die erzwungenen Stufen bleiben sichtbar und werden sofort mit umgelegt - sonst
 * wirkt der berechnete Spielwert falsch.
 */
@Composable
fun <T> ScaleSelector(
    options: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    floorIndex: Int = 0,
    columns: Int? = null,
    forcedHint: String = "durch die Ansage gesetzt",
) {
    val effectiveColumns = columns ?: 1
    OptionGrid(columns = effectiveColumns, itemCount = options.size, modifier = modifier) { index ->
        val forced = index < floorIndex
        OptionTile(
            label = label(options[index]),
            secondaryLabel = if (forced) forcedHint else null,
            selected = index == selectedIndex,
            enabled = !forced,
            onClick = { onSelect(index) },
            height = RotaskatDimens.tapTarget,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Ueberschrift eines Abschnitts. Klein, aber nie unter 14sp. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * Die grossen Ergebnisflaechen. Sie sind gleichzeitig die Speichern-Buttons -
 * einen zusaetzlichen Commit gibt es bewusst nicht, siehe [OptionTile].
 */
@Composable
fun CommitButton(
    label: String,
    color: Color,
    onContentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = RotaskatDimens.commitButton),
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) color else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (enabled) onContentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, style = MaterialTheme.typography.titleLarge)
                if (detail != null) {
                    // Was der Alleinspieler bei diesem Ausgang bekaeme, schon vor
                    // dem Tap. Die zweite Gelegenheit, einen falsch getippten
                    // Spielwert zu bemerken.
                    Text(text = detail, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Zaehler-Badge, etwa auf "Zusaetze". Zeigt nichts an, solange nichts anliegt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountBadge(count: Int, content: @Composable () -> Unit) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) { Text("$count") }
            }
        },
        content = { content() },
    )
}
