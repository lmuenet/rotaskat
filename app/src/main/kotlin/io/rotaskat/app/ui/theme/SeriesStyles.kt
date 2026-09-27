package io.rotaskat.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Wie eine Linie im Punkteverlauf gezeichnet wird.
 *
 * Nur die Farbe. Der zweite Kanal ist nicht mehr ein Strichmuster, sondern der
 * Name am Linienende (siehe `PointsChart`): eine Beschriftung ist bei
 * Rot-Gruen-Schwaeche, im Kneipenlicht und in Graustufen eindeutig, ein
 * gepunktetes Hellviolett war es im Geraetetest nicht.
 */
@Immutable
data class SeriesStyle(
    val color: Color,
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
 * Kein Strichmuster mehr - vier kraeftige, klar unterscheidbare Farben, jede
 * mindestens 3:1 gegen den Grund (siehe `PaletteContrastTest`). Die Toene sind
 * auf die dunkle Flaeche abgestimmt, weil die App nur ein dunkles Schema hat
 * (siehe [RotaskatColorScheme]). Kaeme je ein helles dazu, ist diese Liste die
 * einzige Stelle, die nachzuziehen waere.
 */
val RotaskatSeriesStyles: List<SeriesStyle> = listOf(
    SeriesStyle(Color(0xFFF2C46B)), // Gold
    SeriesStyle(Color(0xFF7FC4F0)), // Himmelblau
    SeriesStyle(Color(0xFFC8A8FF)), // Flieder
    SeriesStyle(Color(0xFFEDE6D8)), // Elfenbein
)

/** Der Stil eines Sitzplatzes. Wiederholt sich, falls je mehr Plaetze kaemen. */
fun seriesStyleFor(seat: Int): SeriesStyle =
    RotaskatSeriesStyles[Math.floorMod(seat, RotaskatSeriesStyles.size)]
