package io.rotaskat.app.ui.eval

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.ChipRow
import io.rotaskat.app.ui.common.RotaskatTopBar
import io.rotaskat.app.ui.common.SectionHeader
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.RotaskatTextStyles
import io.rotaskat.app.ui.theme.accentColors

/**
 * Der gemeinsame Rahmen der Auswertungsbildschirme.
 *
 * Sie sehen alle gleich aus, weil sie alle dasselbe tun: eine Zahlenmenge
 * zeigen, die niemand am Tisch eintippt. Anders als die Rundeneingabe haben sie
 * kein Tap-Budget - hier wird gelesen, nicht bedient.
 */
@Composable
fun EvalScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            RotaskatTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(RotaskatDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RotaskatDimens.sectionSpacing),
            content = content,
        )
    }
}

/**
 * Ueberschrift plus Inhalt. [subtitle] ist eine kurze, immer sichtbare
 * Zeile (etwa "12 Runden"), [info] die Erklaerung hinter dem ⓘ.
 */
@Composable
fun EvalSection(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Column {
            SectionHeader(title = title, info = info)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

/**
 * Ein Hinweis in Worten.
 *
 * Er steht bewusst in derselben Flaechenform wie die Tabellen daneben: was die
 * App einschraenkt - ein laufender Abend, eine duenne Grundlage, eine Abrechnung,
 * die nicht aufgeht -, gehoert neben die Zahl und nicht in eine Fussnote.
 */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/**
 * Eine Kennzahl als Kachel: Label, grosser Wert, Grundgesamtheit, optional
 * eine Warnung.
 *
 * [detail] ist kein Beiwerk, sondern die Bedingung dafuer, dass [value]
 * etwas aussagt: eine Quote ohne die Anzahl dahinter ist eine Behauptung.
 * Die Warnung traegt ein Icon statt des Zeichens ⚠, das Android als Emoji
 * zeichnet.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    warning: String? = null,
    valueColor: Color = Color.Unspecified,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Color.Unspecified,
    /** Vorlesetext des Labels, wenn die sichtbare Kurzform (etwa "Ø je Runde") allein nicht reicht. */
    labelDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        // Eine Kachel ist eine Aussage, keine Liste - TalkBack soll sie als
        // einen Fokusstopp vorlesen statt Label, Wert und Detail getrennt.
        modifier = modifier
            .defaultMinSize(minHeight = 112.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = label,
                style = RotaskatTextStyles.sectionLabel,
                color = MaterialTheme.accentColors.labelMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (labelDescription != null) {
                    Modifier.semantics { contentDescription = labelDescription }
                } else {
                    Modifier
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) {
                    Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = value,
                    style = RotaskatTextStyles.scoreLarge,
                    color = if (valueColor == Color.Unspecified) colors.onSurface else valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Ein Gedankenstrich ist fuer sehende Augen "keine Quote" -
                    // vorgelesen waere er sonst ein Minuszeichen ohne Zahl.
                    modifier = if (value == "–") {
                        Modifier.semantics { contentDescription = "keine Angabe" }
                    } else {
                        Modifier
                    },
                )
            }
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 2)
            }
            if (warning != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(top = 2.dp).size(14.dp),
                    )
                    Text(warning, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
        }
    }
}

/**
 * Die Umschaltung zwischen All-Time und einer Saison.
 *
 * Als Reihe statt als Raster oder Dropdown: die Zeitraeume sind wenige, sie
 * kommen einmal im Jahr dazu, und jeder von ihnen soll sichtbar sein, ohne
 * dass jemand erst ein Menue oeffnet. Reicht die Breite nicht, scrollt die
 * Reihe zur Seite statt eine zweite Zeile zu erzwingen.
 */
@Composable
fun PeriodSelector(
    seasons: List<Int>,
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = buildList {
        add(Period.AllTime)
        seasons.forEach { add(Period.Season(it)) }
    }
    ChipRow(options = options, selected = selected, onSelect = onSelect, label = { it.label }, modifier = modifier)
}
