package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.OptionGrid
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * Der gemeinsame Rahmen der Auswertungsbildschirme.
 *
 * Sie sehen alle gleich aus, weil sie alle dasselbe tun: eine Zahlenmenge
 * zeigen, die niemand am Tisch eintippt. Anders als die Rundeneingabe haben sie
 * kein Tap-Budget - hier wird gelesen, nicht bedient.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("Zurueck") }
                    }
                },
                actions = actions,
            )
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

/** Ueberschrift plus Inhalt. Haelt den Abstand zwischen beiden ueberall gleich. */
@Composable
fun EvalSection(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        shape = RoundedCornerShape(14.dp),
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
 * Eine Kennzahl mit ihrer Grundgesamtheit.
 *
 * [detail] ist kein Beiwerk, sondern die Bedingung dafuer, dass [value]
 * ueberhaupt etwas aussagt: eine Quote ohne die Anzahl dahinter ist eine
 * Behauptung. Deshalb nimmt diese Kachel das Detail auch nicht als optional an,
 * wenn oben eine Prozentzahl steht.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    warning: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (warning != null) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/**
 * Die Umschaltung zwischen All-Time und einer Saison.
 *
 * Als Raster grosser Kacheln statt als Dropdown oder als waagerecht scrollender
 * Streifen: die Zeitraeume sind wenige, sie kommen einmal im Jahr dazu, und
 * jeder von ihnen soll sichtbar sein, ohne dass jemand erst ein Menue oeffnet
 * oder zur Seite wischt.
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
    OptionGrid(columns = 3, itemCount = options.size, modifier = modifier) { index ->
        val option = options[index]
        OptionTile(
            label = option.label,
            selected = option == selected,
            onClick = { onSelect(option) },
            height = RotaskatDimens.tapTarget,
            modifier = Modifier.weight(1f),
        )
    }
}
