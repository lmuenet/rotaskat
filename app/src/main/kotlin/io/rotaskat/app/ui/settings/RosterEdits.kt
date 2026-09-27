package io.rotaskat.app.ui.settings

import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player

/**
 * Das Ergebnis einer Aenderung am lokalen Verein: der neue Stand oder der
 * Grund, warum es ihn nicht gibt.
 *
 * Bewusst frei von Datenbank und Oberflaeche - die Regeln fuer den Kader sind
 * damit ohne Bildschirm nachpruefbar.
 */
sealed interface RosterEdit {
    data class Ok(val club: Club) : RosterEdit
    data class Rejected(val reason: String) : RosterEdit
}

/** Laenger wird ein Name in keiner Kachel lesbar. */
const val MAX_NAME_LENGTH = 24

private fun Club.nameProblem(name: String, exceptId: String? = null): String? = when {
    name.isEmpty() -> "Ohne Namen geht es nicht."
    name.length > MAX_NAME_LENGTH -> "Höchstens $MAX_NAME_LENGTH Zeichen – länger passt in keine Kachel."
    roster.any { it.id != exceptId && it.displayName.equals(name, ignoreCase = true) } ->
        "$name steht schon im Kader."
    else -> null
}

fun Club.addPlayer(name: String, newId: String): RosterEdit {
    val trimmed = name.trim()
    nameProblem(trimmed)?.let { return RosterEdit.Rejected(it) }
    return RosterEdit.Ok(copy(roster = roster + Player(id = newId, displayName = trimmed)))
}

/**
 * Umbenennen behaelt die Id. Abende und Runden zeigen auf die Id, nicht auf den
 * Namen - sie heissen danach alle mit dem neuen Namen.
 */
fun Club.renamePlayer(id: String, name: String): RosterEdit {
    val trimmed = name.trim()
    nameProblem(trimmed, exceptId = id)?.let { return RosterEdit.Rejected(it) }
    return RosterEdit.Ok(
        copy(roster = roster.map { if (it.id == id) it.copy(displayName = trimmed) else it }),
    )
}

/**
 * Entfernen nur, solange der Spieler an keinem Abend sass. Sonst zeigte die
 * Sitzordnung eines Abends auf einen Spieler, den es nicht mehr gibt, und die
 * Rangliste verloere seine Punkte.
 */
fun Club.removePlayer(id: String, seated: Set<String>): RosterEdit = when {
    id in seated -> RosterEdit.Rejected("Wer schon mitgespielt hat, bleibt im Kader – umbenennen geht.")
    roster.size <= 1 -> RosterEdit.Rejected("Mindestens ein Spieler bleibt im Kader.")
    else -> RosterEdit.Ok(copy(roster = roster.filterNot { it.id == id }))
}

/** Gilt ab dem naechsten Abend - jeder Abend friert seinen Satz beim Anpfiff ein. */
fun Club.withCentsPerPoint(cents: Int): RosterEdit =
    if (cents < 0) RosterEdit.Rejected("Der Satz kann nicht negativ sein.") else RosterEdit.Ok(copy(centsPerPoint = cents))
