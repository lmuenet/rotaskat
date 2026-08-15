package io.rotaskat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Session

/**
 * Der Zugang zur Datenschicht.
 *
 * Bewusst ein CompositionLocal und kein Parameter durch alle Bildschirme: die
 * Alternative waere, den Graphen durch jede Signatur zu reichen, nur damit die
 * Bildschirme ihn an ihre ViewModels weitergeben.
 */
val LocalRotaskatGraph = staticCompositionLocalOf<RotaskatGraph> {
    error("RotaskatGraph fehlt - RotaskatApp() nicht aufgerufen?")
}

/**
 * Sitzplatz -> angezeigter Name.
 *
 * Ein Spieler, der lokal noch nicht bekannt ist - etwa nach einem
 * Geraetewechsel, bevor der Kader gezogen wurde - bekommt einen Platzhalter
 * statt einer leeren Zeile. Die Sitzordnung darf nicht abhaengig davon
 * verrutschen, ob der Kader schon da ist.
 */
fun seatNames(session: Session, roster: List<Player>): Map<Int, String> {
    val byId = roster.associateBy { it.id }
    return (0 until session.seatCount).associateWith { seat ->
        val playerId = session.seats[seat]
        byId[playerId]?.displayName ?: "Platz ${seat + 1}"
    }
}
