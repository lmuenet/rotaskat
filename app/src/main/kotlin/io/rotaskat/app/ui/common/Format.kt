package io.rotaskat.app.ui.common

import kotlin.math.absoluteValue

/**
 * Punkteanzeige.
 *
 * Zwei Regeln, die ueberall gelten:
 *
 *  1. Das Vorzeichen wird IMMER geschrieben, auch das Plus. Farbe ist der
 *     Zweitkanal, nie der einzige - bei Rot-Gruen-Schwaeche, im Kneipenlicht
 *     und auf einem Screenshot in Graustufen bleibt das Vorzeichen lesbar.
 *  2. Gerechnet wird in halben Punkten, angezeigt in ganzen. Die Umrechnung
 *     passiert genau hier und nirgends sonst, damit sie nicht an zwei Stellen
 *     auseinanderlaufen kann.
 */
fun formatPoints(halfPoints: Long): String {
    val sign = if (halfPoints < 0) "-" else "+"
    val abs = halfPoints.absoluteValue
    val whole = abs / 2
    // Der halbe Punkt ist kein Rundungsfehler, sondern der Regelfall bei
    // ungeradem Spielwert. Er wird ausgeschrieben statt weggerundet.
    return if (abs % 2 == 0L) "$sign$whole" else "$sign$whole,5"
}

fun formatPoints(halfPoints: Int): String = formatPoints(halfPoints.toLong())

/**
 * Geldbetrag in ganzen Cent. Positiv heisst bekommt, negativ heisst zahlt -
 * auch hier steht das Vorzeichen immer.
 */
fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else "+"
    val abs = cents.absoluteValue
    return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')} €"
}

/** Betrag ohne Vorzeichen, fuer Zahlungen ("A zahlt B 3,40 Euro"). */
fun formatAmount(cents: Long): String {
    val abs = cents.absoluteValue
    return "${abs / 100},${(abs % 100).toString().padStart(2, '0')} €"
}

/** Kuerzel fuer die Sitzplatzanzeige, wenn der Name nicht passt. */
fun initials(displayName: String): String {
    val parts = displayName.trim().split(" ", "-").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
