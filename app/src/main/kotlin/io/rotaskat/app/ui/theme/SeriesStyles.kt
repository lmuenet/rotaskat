package io.rotaskat.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Wie eine Linie im Punkteverlauf gezeichnet wird.
 *
 * Zwei Kanaele, nicht einer: Farbe UND Strichmuster. Dieselbe Ueberlegung wie
 * beim immer mitgeschriebenen Vorzeichen in `formatPoints` - vier Linien, die
 * sich nur in der Farbe unterscheiden, sind bei Rot-Gruen-Schwaeche, im
 * Kneipenlicht und auf einem Graustufen-Screenshot vier gleiche Linien.
 *
 * [dash] ist die Strichfolge in dp (Strich, Luecke, ...), `null` heisst
 * durchgezogen.
 */
@Immutable
data class SeriesStyle(
    val color: Color,
    val dash: List<Float>? = null,
)

/**
 * Die Farben der Spielerlinien.
 *
 * Gruen und Rot kommen bewusst NICHT vor: beide Toene sind fuer Gewinn und
 * Verlust reserviert (siehe [RotaskatScoreColors]). Eine Spielerlinie in
 * Verlustrot wuerde das Signal entwerten, das die ganze Oberflaeche sonst
 * traegt.
 *
 * Vier Eintraege reichen fuer einen Tisch. Mehr Sitzplaetze gibt es nicht, und
 * die Reihenfolge ist die der Sitzplaetze - Platz 0 bekommt immer dieselbe
 * Farbe, damit der Blick zwischen Diagramm und Tabelle nicht neu suchen muss.
 *
 * Die Toene sind auf die dunkle Flaeche abgestimmt, weil die App nur ein
 * dunkles Schema hat (siehe [RotaskatColorScheme]). Kaeme je ein helles dazu,
 * ist diese Liste die einzige Stelle, die nachzuziehen waere - das Strichmuster
 * traegt die Unterscheidung ohnehin auch ohne Farbe.
 */
val RotaskatSeriesStyles: List<SeriesStyle> = listOf(
    SeriesStyle(Color(0xFFF2C46B)),
    SeriesStyle(Color(0xFF9FCBE8), dash = listOf(10f, 6f)),
    SeriesStyle(Color(0xFFC9A7E8), dash = listOf(2f, 6f)),
    SeriesStyle(Color(0xFFD9D2C4), dash = listOf(14f, 5f, 2f, 5f)),
)

/** Der Stil eines Sitzplatzes. Wiederholt sich, falls je mehr Plaetze kaemen. */
fun seriesStyleFor(seat: Int): SeriesStyle =
    RotaskatSeriesStyles[Math.floorMod(seat, RotaskatSeriesStyles.size)]
