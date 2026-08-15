package io.rotaskat.app.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Die Sitzordnung als Bild des echten Tisches.
 *
 * Drei Spieler ergeben ein Dreieck, vier ein Viereck - nicht aus Dekoration,
 * sondern damit der Abgleich zwischen Bildschirm und Tisch ohne Nachdenken
 * funktioniert. Eine Liste "Platz 1 bis 4" zwingt dazu, im Kopf eine Nummer auf
 * einen Stuhl abzubilden, und genau dieser Zwischenschritt ist die Stelle, an
 * der am Tisch der falsche Alleinspieler getippt wird.
 *
 * Platz 0 sitzt unten, also dort, wo das Geraet liegt. Die weiteren Plaetze
 * laufen im Uhrzeigersinn - in derselben Richtung, in der auch der Geber
 * weiterwandert.
 */
@Composable
fun SeatRing(
    seatCount: Int,
    modifier: Modifier = Modifier,
    ringSize: Dp = RotaskatDimens.tableRingSize,
    slotSize: Dp = RotaskatDimens.seatSlotSize,
    center: @Composable () -> Unit = {},
    seat: @Composable (seat: Int) -> Unit,
) {
    Layout(
        content = {
            center()
            for (index in 0 until seatCount) seat(index)
        },
        modifier = modifier.size(ringSize),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val slot = slotSize.roundToPx()
        val childConstraints = Constraints(maxWidth = slot, maxHeight = slot)
        val placeables = measurables.map { it.measure(childConstraints) }

        // Der Radius laesst den halben Kachelrand innen, damit keine Kachel ueber
        // die Kante des Rings hinausragt.
        val radius = min(width, height) / 2.0 - slot / 2.0

        layout(width, height) {
            val centerX = width / 2.0
            val centerY = height / 2.0

            placeables.firstOrNull()?.let { middle ->
                middle.place(
                    (centerX - middle.width / 2.0).roundToInt(),
                    (centerY - middle.height / 2.0).roundToInt(),
                )
            }

            val seats = placeables.drop(1)
            seats.forEachIndexed { index, placeable ->
                // Bildschirmkoordinaten: y waechst nach unten, PI/2 ist also
                // unten. Wachsende Winkel laufen damit im Uhrzeigersinn.
                val angle = PI / 2 + index * 2 * PI / seats.size
                val x = centerX + radius * cos(angle) - placeable.width / 2.0
                val y = centerY + radius * sin(angle) - placeable.height / 2.0
                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

/** Abstand zwischen Ring und den Abschnitten darunter. */
val SeatRingBottomSpacing: Dp = 12.dp
