package io.rotaskat.app.ui.eval

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.ChipRow
import io.rotaskat.app.ui.common.counted
import io.rotaskat.app.ui.common.formatAverage
import io.rotaskat.app.ui.common.formatPercent
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.common.formatShortDate
import io.rotaskat.app.ui.common.icon
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.theme.RotaskatDimens
import io.rotaskat.app.ui.theme.accentColors
import io.rotaskat.app.ui.theme.scoreColors
import io.rotaskat.shared.model.Suit

/**
 * Die Zahlen eines einzelnen Spielers.
 *
 * Die Leitregel dieses Bildschirms ist Ehrlichkeit vor Aussagekraft: neben jeder
 * Quote steht, aus wie vielen Spielen sie stammt, und wo die Grundgesamtheit zu
 * duenn ist, sagt die App das selbst. Eine Gewinnquote von 100 Prozent aus zwei
 * Alleinspielen ist kein Lob, sondern ein Rundungsfehler mit Prozentzeichen.
 */
@Composable
fun StatsScreen(
    actions: RotaskatNavActions,
    modifier: Modifier = Modifier,
) {
    val graph = LocalRotaskatGraph.current
    val viewModel: EvaluationViewModel = viewModel(
        factory = remember(graph) { EvaluationViewModel.factory(graph) },
    )
    val standings by viewModel.standings.collectAsState()
    val seasons by viewModel.seasons.collectAsState()
    val period by viewModel.period.collectAsState()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Nach einem Wechsel des Zeitraums kann der gewaehlte Spieler in den Daten
    // fehlen - dann rueckt der Erste nach, statt dass der Bildschirm leer bleibt.
    val selected = standings.firstOrNull { it.player.id == selectedId } ?: standings.firstOrNull()

    EvalScaffold(
        title = "Statistik",
        subtitle = period.label,
        onBack = null,
        modifier = modifier,
    ) {
        PeriodSelector(seasons = seasons, selected = period, onSelect = viewModel::setPeriod)

        if (standings.isEmpty() || selected == null) {
            Notice("Noch kein Abend in diesem Zeitraum.")
            return@EvalScaffold
        }

        ChipRow(
            options = standings,
            selected = selected,
            onSelect = { selectedId = it.player.id },
            label = { it.player.displayName },
        )

        EvalSection(
            title = "Kennzahlen",
            info = "Überreizt zählt als verloren. Beim Lieblingsspiel zählen nur angesagte Spiele – der Ramsch gehört niemandem.",
        ) {
            StatTileGrid(statTiles(selected))
        }
    }
}

internal data class StatTileModel(
    val label: String,
    val value: String,
    val detail: String?,
    val warning: String? = null,
    val valueColor: Color = Color.Unspecified,
    @DrawableRes val icon: Int? = null,
    val iconTint: Color = Color.Unspecified,
    val labelDescription: String? = null,
)

/** Die sechs Kennzahlen eines Spielers, in fester Reihenfolge. */
@Composable
internal fun statTiles(stats: PlayerStats): List<StatTileModel> {
    val score = MaterialTheme.scoreColors
    val accent = MaterialTheme.accentColors
    val rate = stats.soloWinRate
    val favourites = stats.favouriteGames
    val average = stats.averageHalfPointsPerRound
    val best = stats.bestSession
    val worst = stats.worstSession
    val single = favourites.singleOrNull()
    val suit = when (single) {
        GameKind.KARO -> Suit.DIAMONDS
        GameKind.HERZ -> Suit.HEARTS
        GameKind.PIK -> Suit.SPADES
        GameKind.KREUZ -> Suit.CLUBS
        else -> null
    }
    return listOf(
        StatTileModel(
            label = "Punkte",
            value = formatPoints(stats.halfPoints),
            detail = "${counted(stats.sessions, "Abend", "Abende")} · ${counted(stats.rounds, "Runde", "Runden")}",
            valueColor = score.forValue(stats.halfPoints),
        ),
        StatTileModel(
            label = "Gewinnquote allein",
            value = if (rate == null) "–" else formatPercent(rate),
            detail = if (rate == null) "nie allein" else "${stats.soloWins} von ${stats.soloRounds}",
            warning = if (rate != null && stats.soloSampleIsThin) "Unter $THIN_SOLO_SAMPLE Alleinspielen wenig aussagekräftig" else null,
        ),
        StatTileModel(
            label = "Ø je Runde",
            value = if (average == null) "–" else formatAverage(average),
            detail = if (average == null) null else "aus ${counted(stats.rounds, "Runde", "Runden")}",
            valueColor = average?.let { score.forValue(kotlin.math.sign(it).toLong()) } ?: Color.Unspecified,
            labelDescription = "Durchschnitt je Runde",
        ),
        StatTileModel(
            label = "Lieblingsspiel",
            value = if (favourites.isEmpty()) "–" else favourites.joinToString(" / ") { it.label },
            detail = if (favourites.isEmpty()) "nie allein" else "${stats.favouriteGameCount} von ${counted(stats.soloRounds, "Alleinspiel", "Alleinspielen")}",
            icon = suit?.icon,
            iconTint = when (suit) {
                Suit.DIAMONDS, Suit.HEARTS -> accent.suitRed
                Suit.SPADES, Suit.CLUBS -> accent.suitBlack
                null -> Color.Unspecified
            },
        ),
        StatTileModel(
            label = "Bester Abend",
            value = best?.let { formatPoints(it.halfPoints) } ?: "–",
            detail = best?.let { "${formatShortDate(it.startedAt)} · ${counted(it.rounds, "Runde", "Runden")}" },
            valueColor = best?.let { score.forValue(it.halfPoints) } ?: Color.Unspecified,
        ),
        StatTileModel(
            label = "Schlechtester Abend",
            value = worst?.let { formatPoints(it.halfPoints) } ?: "–",
            detail = worst?.let { "${formatShortDate(it.startedAt)} · ${counted(it.rounds, "Runde", "Runden")}" },
            warning = if (best != null && worst != null && stats.sessions == 1) "nur ein Abend" else null,
            valueColor = worst?.let { score.forValue(it.halfPoints) } ?: Color.Unspecified,
        ),
    )
}

/** Zwei Kacheln je Reihe, jede Reihe so hoch wie ihre hoechste Kachel. */
@Composable
internal fun StatTileGrid(tiles: List<StatTileModel>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        for (pair in tiles.chunked(2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                for (tile in pair) {
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        detail = tile.detail,
                        warning = tile.warning,
                        valueColor = tile.valueColor,
                        icon = tile.icon,
                        iconTint = tile.iconTint,
                        labelDescription = tile.labelDescription,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
