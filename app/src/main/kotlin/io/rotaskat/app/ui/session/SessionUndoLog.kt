package io.rotaskat.app.ui.session

import io.rotaskat.shared.model.Round
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Wie eine gerade gespeicherte Aenderung wieder zurueckgenommen wird. */
sealed interface UndoToken {

    /** Eine neu angelegte Runde: Tombstone setzen. */
    data class Remove(val roundId: String) : UndoToken

    /** Eine Korrektur oder ein Loeschen: den vorherigen Stand als neue Revision zurueckschreiben. */
    data class Restore(val round: Round) : UndoToken
}

/**
 * Die letzte Aenderung an einem Abend.
 *
 * [round] ist der Stand, den die Zeile beschreibt: die neue Runde, die
 * korrigierte Fassung oder die geloeschte Runde.
 */
data class LastChange(
    val kind: Kind,
    val round: Round,
    val undo: UndoToken,
    val undone: Boolean = false,
) {
    enum class Kind { SAVED, CORRECTED, DELETED }
}

/**
 * Merkt sich je Abend die letzte Aenderung, damit das Undo nicht an einer
 * Snackbar haengt.
 *
 * Undo ist laut SCOPE.md DAS Sicherheitsnetz, weil es keine Rueckfragen gibt.
 * Eine Leiste, die nach vier Sekunden verschwindet, traegt das am Tisch nicht:
 * nach dem Tap schaut man auf die Karten, nicht aufs Handy. Die Zeile bleibt
 * deshalb stehen, bis die naechste Aenderung sie ersetzt.
 *
 * Bewusst nur im Speicher und fuer den ganzen Prozess: Korrektur und Abend
 * haben je ein eigenes ViewModel, und das Undo einer Korrektur oder eines
 * Loeschens soll nach dem Ruecksprung im Abend angeboten werden. Nach einem
 * Neustart der App gibt es nichts mehr zurueckzunehmen - dafuer ist die
 * Rundenliste da.
 */
class SessionUndoLog {

    companion object {
        /** Das eine Exemplar fuer die laufende App, geteilt von Abend und Korrektur. */
        val process = SessionUndoLog()
    }

    private val changes = MutableStateFlow<Map<String, LastChange>>(emptyMap())

    fun observe(sessionId: String): Flow<LastChange?> =
        changes.map { it[sessionId] }.distinctUntilChanged()

    fun current(sessionId: String): LastChange? = changes.value[sessionId]

    fun record(sessionId: String, change: LastChange) {
        changes.update { it + (sessionId to change) }
    }

    /** Markiert genau diese Aenderung als zurueckgenommen, falls sie noch die letzte ist. */
    fun markUndone(sessionId: String, change: LastChange) {
        changes.update { current ->
            if (current[sessionId] == change) current + (sessionId to change.copy(undone = true)) else current
        }
    }
}
