package io.rotaskat.app.ui.eval

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.common.counted
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
 * Selbst gezeichnet, ohne Diagrammbibliothek. Jede Linie traegt ihren Namen
 * und den Stand am Ende - eine Legende darunter zwang dazu, zwischen Farbe und
 * Name hin und her zu sehen, und eine Farbe allein ist bei Rot-Gruen-Schwaeche
 * keine Zuordnung.
 *
 * Keine Farbe steht in dieser Datei: Raster und Achsen kommen aus dem
 * Farbschema, die Linien aus [io.rotaskat.app.ui.theme.RotaskatSeriesStyles].
 */
@Composable
fun PointsChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val values = series.flatMap { it.cumulative }
    val roundCount = (series.maxOfOrNull { it.cumulative.size } ?: 0) - 1
    if (series.isEmpty() || roundCount < 1) {
        Text(
            text = "Noch keine Runde gespielt.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val endStyle = MaterialTheme.typography.labelLarge
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val zeroColor = MaterialTheme.colorScheme.outline
    val scale = chartScale(values)
    val summary = chartSummary(series)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = summary },
    ) {
        val axisLabels = scale.lines.map { formatPoints(it) }
        val axisSizes = axisLabels.map { measurer.measure(AnnotatedString(it), axisStyle).size }
        val axisLabelHeight = axisSizes.maxOf { it.height }.toFloat()
        val axisWidth = axisSizes.maxOf { it.width }.toFloat()
        val axisTitle = measurer.measure(AnnotatedString("Runde"), axisStyle)

        // Beschriftungen am Linienende. Hoechstens 45 % der Breite, sonst
        // bliebe fuer das Diagramm selbst nichts - lange Namen werden gekuerzt.
        val maxEndWidth = (size.width * 0.45f).toInt()
        val endLayouts = series.map { line ->
            measurer.measure(
                text = AnnotatedString("${line.label} ${formatPoints(line.cumulative.last())}"),
                style = endStyle.copy(color = line.style.color),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxEndWidth),
            )
        }
        val endWidth = endLayouts.maxOf { it.size.width }.toFloat()
        val endHeight = endLayouts.maxOf { it.size.height }.toFloat()

        val leftPadding = axisWidth + 8.dp.toPx()
        val rightPadding = endWidth + 14.dp.toPx()
        val topPadding = maxOf(axisLabelHeight, endHeight) / 2f
        val bottomPadding = axisLabelHeight + 6.dp.toPx() + axisTitle.size.height + 2.dp.toPx()

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
            val measured = measurer.measure(AnnotatedString(axisLabels[index]), axisStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(leftPadding - 6.dp.toPx() - measured.size.width, yPosition - measured.size.height / 2f),
            )
        }

        // Nicht jede Runde beschriften: bei dreissig Runden stehen die Zahlen
        // sonst uebereinander.
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
                    x = (x(round) - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                    y = topPadding + plotHeight + 6.dp.toPx(),
                ),
            )
            round += labelStep
        }
        drawText(
            textLayoutResult = axisTitle,
            topLeft = Offset(
                x = leftPadding + plotWidth - axisTitle.size.width,
                y = topPadding + plotHeight + 6.dp.toPx() + axisLabelHeight + 2.dp.toPx(),
            ),
        )

        for (line in series) {
            val path = Path()
            line.cumulative.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = line.style.color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val last = line.cumulative.lastIndex
            drawCircle(color = line.style.color, radius = 4.5.dp.toPx(), center = Offset(x(last), y(line.cumulative[last])))
        }

        val desired = series.map { y(it.cumulative.last()) }
        val placed = labelPositions(desired, minGap = endHeight, top = topPadding, bottom = topPadding + plotHeight)
        val labelX = leftPadding + plotWidth + 10.dp.toPx()
        series.forEachIndexed { index, line ->
            val endX = x(line.cumulative.lastIndex)
            if (kotlin.math.abs(placed[index] - desired[index]) > 1f) {
                drawLine(
                    color = line.style.color.copy(alpha = 0.5f),
                    start = Offset(endX + 4.5.dp.toPx(), desired[index]),
                    end = Offset(labelX - 2.dp.toPx(), placed[index]),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val layout = endLayouts[index]
            drawText(textLayoutResult = layout, topLeft = Offset(labelX, placed[index] - layout.size.height / 2f))
        }
    }
}

/** Was TalkBack statt des Bildes vorliest: der Stand, nach Punkten sortiert. */
internal fun chartSummary(series: List<ChartSeries>): String {
    val rounds = (series.maxOfOrNull { it.cumulative.size } ?: 1) - 1
    val standings = series
        .sortedByDescending { it.cumulative.last() }
        .joinToString(", ") { "${it.label} ${formatPoints(it.cumulative.last())}" }
    return "Punkteverlauf über ${counted(rounds, "Runde", "Runden")}: $standings"
}

/**
 * Verteilt Beschriftungen senkrecht, sodass keine zwei naeher als [minGap]
 * beieinanderliegen und alle zwischen [top] und [bottom] bleiben.
 *
 * Die Reihenfolge der Wunschpositionen bleibt erhalten - die oberste Linie hat
 * auch die oberste Beschriftung. Erst wird von oben nach unten Platz gemacht,
 * dann vom unteren Rand her zurueckgeschoben. Liegen die Wunschpositionen
 * weit genug auseinander, bewegt sich nichts.
 */
internal fun labelPositions(desired: List<Float>, minGap: Float, top: Float, bottom: Float): List<Float> {
    if (desired.isEmpty()) return emptyList()
    val order = desired.indices.sortedBy { desired[it] }
    val placed = FloatArray(desired.size)
    var previous = Float.NEGATIVE_INFINITY
    for (index in order) {
        val y = maxOf(desired[index].coerceIn(top, bottom), previous + minGap)
        placed[index] = y
        previous = y
    }
    var next = Float.POSITIVE_INFINITY
    for (index in order.reversed()) {
        val y = minOf(placed[index], next - minGap, bottom)
        placed[index] = y
        next = y
    }
    return placed.toList()
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
