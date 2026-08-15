package io.rotaskat.app.ui.eval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rotaskat.app.ui.LocalRotaskatGraph
import io.rotaskat.app.ui.common.OptionGrid
import io.rotaskat.app.ui.common.OptionTile
import io.rotaskat.app.ui.common.formatAverage
import io.rotaskat.app.ui.common.formatDate
import io.rotaskat.app.ui.common.formatPercent
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.nav.RotaskatNavActions
import io.rotaskat.app.ui.theme.RotaskatDimens

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
        onBack = { actions.back() },
        modifier = modifier,
    ) {
        PeriodSelector(seasons = seasons, selected = period, onSelect = viewModel::setPeriod)

        if (standings.isEmpty() || selected == null) {
            Notice("Fuer diesen Zeitraum ist noch kein Abend erfasst.")
            return@EvalScaffold
        }

        OptionGrid(columns = 2, itemCount = standings.size) { index ->
            val stats = standings[index]
            OptionTile(
                label = stats.player.displayName,
                selected = stats.player.id == selected.player.id,
                onClick = { selectedId = stats.player.id },
                height = RotaskatDimens.tapTarget,
                modifier = Modifier.weight(1f),
            )
        }

        PlayerStatsCards(selected)
    }
}

@Composable
private fun PlayerStatsCards(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing)) {
        StatCard(
            label = "Punkte",
            value = formatPoints(stats.halfPoints),
            detail = "${stats.sessions} Abende, ${stats.rounds} Runden mitgespielt",
        )

        // Ueberreizt zaehlt als verloren, genau wie in der Abrechnung: wer sich
        // verreizt hat, hat das Spiel verloren, auch wenn die Stiche gereicht
        // haetten.
        val rate = stats.soloWinRate
        StatCard(
            label = "Gewinnquote als Alleinspieler",
            value = if (rate == null) "kein Alleinspiel" else formatPercent(rate),
            detail = if (rate == null) {
                "In diesem Zeitraum war ${stats.player.displayName} nie Alleinspieler."
            } else {
                "${stats.soloWins} von ${stats.soloRounds} Alleinspielen gewonnen"
            },
            warning = if (rate != null && stats.soloSampleIsThin) {
                "Unter $THIN_SOLO_SAMPLE Alleinspielen sagt die Quote wenig aus."
            } else {
                null
            },
        )

        val favourite = stats.favouriteGame
        StatCard(
            label = "Haeufigstes Alleinspiel",
            value = favourite?.key?.label ?: "keines",
            detail = if (favourite == null) {
                "Gezaehlt werden nur angesagte Spiele - der Ramsch gehoert niemandem."
            } else {
                "${favourite.value} von ${stats.soloRounds} Alleinspielen"
            },
        )

        val average = stats.averageHalfPointsPerRound
        StatCard(
            label = "Durchschnitt je Runde",
            value = if (average == null) "keine Runde" else "${formatAverage(average)} Punkte",
            detail = if (average == null) null else "aus ${stats.rounds} Runden",
        )

        val best = stats.bestSession
        StatCard(
            label = "Bester Abend",
            value = if (best == null) "keiner" else formatPoints(best.halfPoints),
            detail = best?.let { "${formatDate(it.startedAt)}, ${it.rounds} Runden" },
        )

        val worst = stats.worstSession
        StatCard(
            label = "Schlechtester Abend",
            value = if (worst == null) "keiner" else formatPoints(worst.halfPoints),
            detail = worst?.let { "${formatDate(it.startedAt)}, ${it.rounds} Runden" },
            warning = if (best != null && worst != null && stats.sessions == 1) {
                "Es gibt bisher genau einen Abend - bester und schlechtester sind derselbe."
            } else {
                null
            },
        )
    }
}
