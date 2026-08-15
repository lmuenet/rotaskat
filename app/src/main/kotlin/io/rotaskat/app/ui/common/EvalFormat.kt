package io.rotaskat.app.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Die Formate der Auswertung.
 *
 * Sie stehen neben `formatPoints` und `formatCents` und folgen denselben zwei
 * Regeln: das Vorzeichen wird immer geschrieben, und die Umrechnung von halben
 * in ganze Punkte passiert genau einmal, naemlich hier.
 */

/** Datum eines Abends, deutsch und ohne Uhrzeit - der Abend ist die Einheit. */
fun formatDate(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = instant.toLocalDateTime(zone).date
    return String.format(Locale.GERMANY, "%02d.%02d.%04d", date.dayOfMonth, date.monthNumber, date.year)
}

/**
 * Ein Durchschnitt in halben Punkten je Runde, angezeigt in ganzen Punkten.
 *
 * Eine Nachkommastelle, nicht mehr: der Durchschnitt eines Abends ist eine
 * Groessenordnung, keine Messung. Drei Stellen wuerden eine Genauigkeit
 * vortaeuschen, die aus dreissig Runden nicht herauszuholen ist.
 */
fun formatAverage(halfPointsPerRound: Double): String {
    val points = halfPointsPerRound / 2.0
    // Vorzeichen von Hand statt ueber %+.1f: sonst steht bei -0,04 ein "-0,0"
    // da, also ein Minus vor einer Null.
    val rounded = (points * 10).roundToInt() / 10.0
    val sign = if (rounded < 0) "-" else "+"
    return sign + String.format(Locale.GERMANY, "%.1f", rounded.absoluteValue)
}

/**
 * Eine Quote in Prozent, ohne Nachkommastelle.
 *
 * Die zugrunde liegende Anzahl steht in der Oberflaeche immer daneben. Eine
 * Quote aus fuenf Spielen ist keine Quote, sondern ein Zufall mit Prozentzeichen
 * - siehe [io.rotaskat.app.ui.eval.PlayerStats.soloSampleIsThin].
 */
fun formatPercent(share: Double): String = "${(share * 100).roundToInt()} %"
