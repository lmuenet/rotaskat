package io.rotaskat.app.ui.session

import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.Round

/**
 * Wem die Runde gehoert: der Alleinspieler, beim Ramsch der Verlierer - oder
 * wer den Durchmarsch geschafft hat.
 */
internal val Round.subjectSeat: Int?
    get() = declarerSeat ?: ramsch?.durchmarschSeat ?: ramsch?.loserSeat

/**
 * Der Ausgang als Wort, fuer Rundenliste und Rueckmeldung.
 *
 * Bei "ueberreizt" steht das Gebot mit da. Beim spaeteren Nachvollziehen ist
 * genau diese Zahl die, nach der gefragt wird.
 */
internal fun Round.outcomeLabel(): String = when {
    declaration is RamschGame -> if (ramsch?.durchmarschSeat != null) "Durchmarsch" else "verliert"
    overbid -> bid?.let { "überreizt, gereizt bis $it" } ?: "überreizt"
    won -> "gewonnen"
    else -> "verloren"
}
