package io.rotaskat.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Die Masse, an denen die Bedienbarkeit haengt.
 *
 * Die 48dp aus den Material-Richtlinien sind das Minimum fuer eine ruhige Hand
 * bei gutem Licht. Hier wird einhaendig getippt, mit dem Daumen, waehrend die
 * andere Hand die Karten haelt, und der Tisch wackelt. Deshalb liegt die
 * Untergrenze bei 56dp und der Standard bei 64dp: ein Fehltap kostet eine
 * Korrektur mit Undo, also ein Vielfaches der Zeit, die die groessere Flaeche
 * an Platz kostet.
 */
object RotaskatDimens {

    /** Untergrenze fuer alles Antippbare. */
    val tapTarget = 56.dp

    /** Der Normalfall: Spielart, Spitzen, Sitzplaetze. */
    val bigTapTarget = 64.dp

    /**
     * Gewonnen und Verloren. Sie sind gleichzeitig die Speichern-Buttons und
     * werden ohne Hinsehen getroffen - dafuer muessen sie die groessten
     * Flaechen des Bildschirms sein.
     */
    val commitButton = 76.dp

    val screenPadding = 16.dp
    val sectionSpacing = 20.dp
    val itemSpacing = 8.dp

    /** Kantenlaenge des Sitzordnungs-Rings. */
    val tableRingSize = 320.dp

    /** Ein Sitzplatz im Ring. */
    val seatSlotSize = 104.dp

    /** Ecken der Auswahlkacheln. */
    val tileCorner = 12.dp

    /** Ecken von "Gewonnen" und "Verloren". */
    val commitCorner = 16.dp

    /** Ecken von Karten, etwa des laufenden Abends auf der Startseite. */
    val cardCorner = 16.dp

    /** Rand einer gewaehlten Kachel. */
    val selectedBorder = 2.dp
}
