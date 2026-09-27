package io.rotaskat.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.shared.model.Suit

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
    role: Role = Role.RadioButton,
) {
    TileFrame(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        height = height,
        role = role,
        modifier = modifier,
    ) {
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

/**
 * Karo, Herz, Pik oder Kreuz: das Symbol gross, darunter klein der Name.
 *
 * Das Symbol erkennt man schneller als das Wort, gerade bei schlechtem Licht.
 * Es ist ein Vector-Drawable, kein Unicode-Zeichen: Android zeichnet Karo,
 * Herz, Pik und Kreuz sonst als farbiges Emoji und ignoriert den Kupferton.
 * Der Name bleibt fuer Neulinge und fuer TalkBack stehen; vorgelesen wird nur
 * er, nicht das Symbol.
 */
@Composable
fun SuitTile(
    suit: Suit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.accentColors
    val symbolColor = when (suit) {
        Suit.DIAMONDS, Suit.HEARTS -> accent.suitRed
        Suit.SPADES, Suit.CLUBS -> accent.suitBlack
    }
    TileFrame(selected = selected, enabled = true, onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(suit.icon),
                contentDescription = null,
                tint = symbolColor,
                modifier = Modifier.size(26.dp).clearAndSetSemantics { },
            )
            Text(
                text = suit.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else accent.labelMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Flaeche, Rand und Auswahlzustand aller Kacheln.
 *
 * Die Auswahl hat drei Kanaele: Goldschimmer, Goldrand und Haekchen. Im
 * Kneipenlicht ist ein Farbunterschied allein zu wenig, um eine getroffene
 * Auswahl im Vorbeisehen zu erkennen.
 *
 * `selectable` statt `Surface(onClick)`, damit TalkBack "ausgewaehlt" und die
 * Rolle vorliest - im Geraetetest meldeten die Kacheln beides nicht.
 */
@Composable
private fun TileFrame(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = RotaskatDimens.bigTapTarget,
    role: Role = Role.RadioButton,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(RotaskatDimens.tileCorner)
    val fill = when {
        !enabled -> colors.surfaceContainerLowest
        selected -> colors.primaryContainer
        else -> colors.surfaceContainer
    }
    val contentColor = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val border = when {
        selected -> BorderStroke(RotaskatDimens.selectedBorder, colors.primary)
        !enabled -> BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    Surface(
        modifier = modifier.height(height),
        shape = shape,
        color = fill,
        contentColor = contentColor,
        border = border,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Eine Auswahlkachel meldet "ausgewaehlt"; eine Kachel, die nur
                    // etwas aufklappt ("mehr"), ist ein Knopf und meldet nichts.
                    if (role == Role.RadioButton) {
                        Modifier.selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
                    } else {
                        Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
                    },
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp)
                        .size(14.dp),
                )
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
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing)) {
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
    forcedHint: String = "durch Ansage",
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

/** Ueberschrift eines Abschnitts. Klein, gesperrt, gedaempft - aber nie unter 14sp. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = RotaskatTextStyles.sectionLabel,
        color = MaterialTheme.accentColors.labelMuted,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/**
 * Die grossen Ergebnisflaechen. Sie sind gleichzeitig die Speichern-Buttons -
 * einen zusaetzlichen Commit gibt es bewusst nicht, siehe [OptionTile].
 *
 * Solange die Runde nicht vollstaendig ist, bleibt der Button als
 * gestrichelter Umriss mit [placeholder] stehen: gleiche Hoehe, gleiche Stelle.
 * Ein Button, der erst beim letzten Tap auftaucht, laesst das Layout springen
 * und wird dann daneben getippt.
 */
@Composable
fun CommitButton(
    label: String,
    container: Color,
    onContainer: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    placeholder: String = "+ ?",
    /** Der Ausgang, der vor der Korrektur gespeichert war. */
    previous: Boolean = false,
) {
    val accent = MaterialTheme.accentColors
    val shape = RoundedCornerShape(RotaskatDimens.commitCorner)
    val dashed = if (enabled) {
        Modifier
    } else {
        Modifier.drawBehind {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = accent.disabledOutline,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(RotaskatDimens.commitCorner.toPx()),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                ),
            )
        }
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = RotaskatDimens.commitButton)
            .then(dashed),
        shape = shape,
        border = if (previous) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        color = if (enabled) container else Color.Transparent,
        contentColor = if (enabled) onContainer else accent.labelMuted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                // Was der Alleinspieler bei diesem Ausgang bekaeme, schon vor
                // dem Tap. Die zweite Gelegenheit, einen falsch getippten
                // Spielwert zu bemerken.
                val points = when {
                    !enabled -> placeholder
                    detail == null -> null
                    previous -> "$detail · bisher"
                    else -> detail
                }
                if (points != null) {
                    Text(text = points, style = RotaskatTextStyles.commitPoints)
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
