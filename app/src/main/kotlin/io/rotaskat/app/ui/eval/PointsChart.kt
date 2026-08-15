package io.rotaskat.app.ui.eval

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.formatPoints
import io.rotaskat.app.ui.theme.SeriesStyle
import kotlin.math.ceil

/**
 * Eine Linie im Punkteverlauf.
 *
 * [cumulative] ist der laufende Stand in halben Punkten, Eintrag 0 ist der Stand
 * VOR der ersten Runde. Die Linie beginnt damit bei jedem Spieler auf der
 * Nulllinie, und die erste Runde ist als Steigung sichtbar statt als Punkt aus
 * dem Nichts.
 */
@Immutable
data class ChartSeries(
    val label: String,
    val style: SeriesStyle,
    val cumulative: List<Long>,
)

/**
 * Der Punkteverlauf eines Abends.
 *
 * Selbst gezeichnet, ohne Diagrammbibliothek. Vier Linien ueber dreissig Punkte
 * sind kein Grund fuer eine Abhaengigkeit, die eigene Themes, eigene
 * Animationen und eine eigene Vorstellung von Barrierefreiheit mitbringt - und
 * die man dann doch wieder ueberschreibt.
 *
 * Keine Farbe steht in dieser Datei: Raster, Nulllinie und Beschriftung kommen
 * aus dem Farbschema, die Linien aus [io.rotaskat.app.ui.theme.RotaskatSeriesStyles].
 * Ein anderes Schema - auch ein helles - aendert das Diagramm mit, ohne dass
 * hier eine Zeile angefasst werden muss.
 */
@Composable
fun PointsChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    val values = series.flatMap { it.cumulative }
    val roundCount = (series.maxOfOrNull { it.cumulative.size } ?: 0) - 1
    if (series.isEmpty() || roundCount < 1) {
        Text(
            text = "Noch keine Runde gespielt - es gibt noch nichts zu zeichnen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val scale = chartScale(values)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val labels = scale.lines.map { formatPoints(it) }
        val labelWidths = labels.map { measurer.measure(AnnotatedString(it), axisStyle).size }
        val labelHeight = labelWidths.maxOf { it.height }.toFloat()
        val axisWidth = labelWidths.maxOf { it.width }.toFloat()

        val leftPadding = axisWidth + 8.dp.toPx()
        val bottomPadding = labelHeight + 8.dp.toPx()
        val topPadding = labelHeight / 2f
        // Rechts bleibt Platz, damit der letzte Messpunkt nicht halb an der
        // Kante klebt - er ist der interessanteste des ganzen Diagramms.
        val rightPadding = 6.dp.toPx()

        val plotWidth = size.width - leftPadding - rightPadding
        val plotHeight = size.height - topPadding - bottomPadding
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun x(index: Int): Float = leftPadding + plotWidth * index / roundCount.toFloat()
        fun y(halfPoints: Long): Float {
            val span = (scale.max - scale.min).toFloat()
            return topPadding + plotHeight * (scale.max - halfPoints) / span
        }

        for ((index, line) in scale.lines.withIndex()) {
            val yPosition = y(line)
            drawLine(
                color = if (line == 0L) zeroColor else gridColor,
                start = Offset(leftPadding, yPosition),
                end = Offset(leftPadding + plotWidth, yPosition),
                strokeWidth = if (line == 0L) 1.5.dp.toPx() else 1.dp.toPx(),
            )
            val text = labels[index]
            val measured = measurer.measure(AnnotatedString(text), axisStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = leftPadding - 6.dp.toPx() - measured.size.width,
                    y = yPosition - measured.size.height / 2f,
                ),
            )
        }

        // Nicht jede Runde beschriften: bei dreissig Runden stehen die Zahlen
        // sonst uebereinander. Der Schritt richtet sich nach dem Platz, den eine
        // Beschriftung tatsaechlich braucht.
        val stepWidth = measurer.measure(AnnotatedString("00"), axisStyle).size.width * 2.2f
        val labelStep = maxOf(1, ceil(roundCount * stepWidth / plotWidth).toInt())
        var round = 0
        while (round <= roundCount) {
            val measured = measurer.measure(AnnotatedString(round.toString()), axisStyle)
            drawLine(
                color = gridColor,
                start = Offset(x(round), topPadding),
                end = Offset(x(round), topPadding + plotHeight),
                strokeWidth = 1.dp.toPx(),
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (x(round) - measured.size.width / 2f)
                        .coerceIn(0f, size.width - measured.size.width),
                    y = topPadding + plotHeight + 6.dp.toPx(),
                ),
            )
            round += labelStep
        }

        for (line in series) {
            val path = Path()
            line.cumulative.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = line.style.color,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = line.style.dash?.let { dash ->
                        PathEffect.dashPathEffect(dash.map { it.dp.toPx() }.toFloatArray())
                    },
                ),
            )
            val last = line.cumulative.lastIndex
            drawCircle(
                color = line.style.color,
                radius = 3.5.dp.toPx(),
                center = Offset(x(last), y(line.cumulative[last])),
            )
        }
    }
}

/**
 * Die Legende.
 *
 * Sie zeigt das Strichmuster mit, nicht nur die Farbe - dieselbe Ueberlegung wie
 * beim Vorzeichen an jeder Punktzahl: eine Zuordnung, die allein an der Farbe
 * haengt, ist bei Rot-Gruen-Schwaeche keine.
 */
@Composable
fun ChartLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in series) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 34.dp, height = 12.dp)) {
                    drawLine(
                        color = line.style.color,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = line.style.dash?.let { dash ->
                            PathEffect.dashPathEffect(dash.map { it.dp.toPx() }.toFloatArray())
                        },
                    )
                }
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Text(
                    text = formatPoints(line.cumulative.lastOrNull() ?: 0L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Die Achsenteilung in halben Punkten.
 *
 * Die Null ist immer dabei, auch wenn alle Linien darueber oder darunter liegen:
 * ohne sie sagt das Diagramm nichts darueber, wer im Plus steht.
 */
internal data class ChartScale(val min: Long, val max: Long, val step: Long) {

    val lines: List<Long> get() = generateSequence(min) { it + step }.takeWhile { it <= max }.toList()
}

/** Teilungen, die sich im Kopf ablesen lassen - in GANZEN Punkten. */
private val NICE_STEPS = listOf(1L, 2L, 5L, 10L, 20L, 25L, 50L, 100L, 250L, 500L, 1000L)

internal fun chartScale(halfPoints: List<Long>, targetLines: Int = 4): ChartScale {
    val lowest = minOf(halfPoints.minOrNull() ?: 0L, 0L)
    val highest = maxOf(halfPoints.maxOrNull() ?: 0L, 0L)
    // Ein voellig flacher Verlauf braucht trotzdem eine Hoehe, sonst waere die
    // Spanne 0 und die Umrechnung eine Division durch null.
    val span = maxOf(highest - lowest, 2L)

    val rawWholePoints = ceil(span / 2.0 / targetLines).toLong().coerceAtLeast(1L)
    val stepWholePoints = NICE_STEPS.firstOrNull { it >= rawWholePoints }
        ?: (ceil(rawWholePoints / 1000.0).toLong() * 1000L)
    val step = stepWholePoints * 2

    val min = Math.floorDiv(lowest, step) * step
    val max = min + ceil((highest - min) / step.toDouble()).toLong().coerceAtLeast(1L) * step
    return ChartScale(min, max, step)
}
