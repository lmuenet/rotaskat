package io.rotaskat.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.RotaskatRepository
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.ui.round.RoundDraft
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.scoring.Scoring
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Was der Rundeneingabe nach einem Tap zurueckgemeldet wird.
 *
 * Die Oberflaeche macht daraus Haptik und - nur bei Fehlern - eine Leiste. Ein
 * Dialog kommt bewusst nicht vor: bei dreissig Runden pro Abend wird jeder
 * Bestaetigungsdialog blind weggetippt und erhoeht die Fehlerzahl, statt sie zu
 * senken. Was gespeichert wurde und wie es sich zuruecknehmen laesst, steht
 * dauerhaft in [SessionViewModel.lastChange].
 */
sealed interface SessionMessage {

    data class Saved(
        /**
         * Die Korrektur (oder das Loeschen) ist fertig und der Bildschirm hat
         * seinen Zweck erfuellt. Der Rueckweg folgt SOFORT - das Undo wird im
         * Abend angeboten, nicht hier.
         */
        val closesEdit: Boolean = false,
    ) : SessionMessage

    data object Undone : SessionMessage

    /** Der Abend ist beendet, weiter geht es in die Abrechnung. */
    data object Ended : SessionMessage

    /** Der Abend ohne Runden ist verworfen und existiert nicht mehr. */
    data object Discarded : SessionMessage

    data class Failed(val text: String) : SessionMessage
}

/**
 * Der Zustand eines Spielabends samt der Runde, die gerade eingegeben wird.
 *
 * Der Entwurf liegt hier und nicht im Bildschirm, damit eine halb eingegebene
 * Runde eine Drehung des Geraets oder einen kurzen Wechsel in eine andere App
 * ueberlebt. Am Tisch wird das Handy weitergereicht; ein Entwurf, der dabei
 * verschwindet, kostet die Eingabe zweimal.
 */
class SessionViewModel(
    private val repository: RotaskatRepository,
    private val sessionId: String,
    private val undoLog: SessionUndoLog = SessionUndoLog(),
) : ViewModel() {

    val state: StateFlow<SessionState?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roster: StateFlow<List<Player>> = repository.observeRoster()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<RoundDraft?>(null)
    val draft: StateFlow<RoundDraft?> = _draft.asStateFlow()

    private val _message = MutableStateFlow<SessionMessage?>(null)
    val message: StateFlow<SessionMessage?> = _message.asStateFlow()

    /** Die letzte Aenderung am Abend, auch wenn sie im Korrekturbildschirm passiert ist. */
    val lastChange: StateFlow<LastChange?> = undoLog.observe(sessionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, undoLog.current(sessionId))

    /** Der Stand vor einer Korrektur, damit das Undo etwas hat, worauf es zeigt. */
    private var beforeEdit: Round? = null

    /**
     * Zwischen dem Tap und dem neuen Entwurf. Solange die Runde gespeichert
     * wird, bleibt der alte Entwurf unangetastet stehen: sonst haengte die neue
     * Rotation ihn schon um, der Alleinspieler fiele heraus, und fuer einen
     * Moment stand "-" neben "Karo mit 1 = 18".
     */
    private var committing = false

    init {
        viewModelScope.launch {
            state.collect { session -> syncDraft(session) }
        }
    }

    /**
     * Haelt den Entwurf an der automatisch fortgeschriebenen Sitzordnung.
     *
     * Der Geber wandert nach jeder Runde von selbst weiter - danach gefragt wird
     * nicht. Der Entwurf wird dabei NICHT weggeworfen, sondern nur umgehaengt:
     * wer den Geber von Hand korrigiert, hat den Alleinspieler unter Umstaenden
     * schon getippt und soll ihn nicht noch einmal tippen muessen.
     */
    private fun syncDraft(session: SessionState?) {
        if (session == null) return
        val current = _draft.value
        if (current == null) {
            _draft.value = newDraft(session)
            return
        }
        if (current.editing || committing) return
        if (current.dealerSeat != session.session.dealerSeat) {
            _draft.value = current.withDealer(session.session.dealerSeat)
        }
    }

    private fun newDraft(session: SessionState) = RoundDraft.forNextRound(
        roundId = repository.newRoundId(),
        seatCount = session.session.seatCount,
        dealerSeat = session.session.dealerSeat,
        config = session.session.scoring,
    )

    fun updateDraft(transform: (RoundDraft) -> RoundDraft) {
        if (committing) return
        // Das Gebot folgt dem Spielwert: wer nach "ueberreizt" noch die Spitzen
        // aendert, soll kein Gebot unter dem Spielwert stehen haben.
        _draft.value = _draft.value?.let(transform)?.withBidAboveValue()
    }

    /** Ein Tap korrigiert die Rotation. Sie wird nicht jede Runde abgefragt. */
    fun setDealer(dealerSeat: Int) {
        viewModelScope.launch {
            runCatching { repository.setDealer(sessionId, dealerSeat) }
                .onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    /** Laedt eine bestehende Runde zum Korrigieren in den Entwurf. */
    fun beginEdit(roundId: String) {
        if (_draft.value?.editing == true && _draft.value?.roundId == roundId) return
        viewModelScope.launch {
            val session = repository.session(sessionId) ?: return@launch
            val existing = session.rounds.firstOrNull { it.id == roundId } ?: return@launch
            beforeEdit = existing.round
            _draft.value = RoundDraft.fromRound(existing.round, session.session.scoring)
        }
    }

    /**
     * "Gewonnen" und "Verloren" SIND der Speichern-Knopf. Es gibt keinen
     * zweiten Schritt und keine Rueckfrage; abgesichert wird ueber das Undo in
     * der Zeile "Gespeichert: ..." oberhalb der Eingabe.
     */
    fun commit(won: Boolean) {
        if (committing) return
        val draft = _draft.value ?: return
        val round = draft.toRound(won)
        if (round == null) {
            _message.value = SessionMessage.Failed("Die Runde ist noch nicht vollständig.")
            return
        }
        val errors = Scoring.validate(round)
        if (errors.isNotEmpty()) {
            _message.value = SessionMessage.Failed(errors.first())
            return
        }
        // Der kostenlose Selbsttest nach jedem Commit: eine Runde verteilt
        // Punkte um, sie erzeugt keine. Die Probe kostet nichts und faengt jeden
        // Verteilungsfehler, bevor er in der All-Time-Rangliste steht. Sie ist
        // allerdings overflow-blind, deshalb greift zusaetzlich validate().
        val score = Scoring.score(round, draft.config)
        if (score.sum() != 0) {
            _message.value = SessionMessage.Failed(
                "Runde nicht gespeichert: die Punkte summieren sich auf ${score.sum()} statt auf 0.",
            )
            return
        }

        val editing = draft.editing
        val previous = beforeEdit
        committing = true
        viewModelScope.launch {
            val result = runCatching {
                if (editing) repository.correctRound(round) else repository.recordRound(sessionId, round)
            }
            result.onSuccess {
                when {
                    editing && previous != null ->
                        undoLog.record(sessionId, LastChange(LastChange.Kind.CORRECTED, round, UndoToken.Restore(previous)))
                    !editing ->
                        undoLog.record(sessionId, LastChange(LastChange.Kind.SAVED, round, UndoToken.Remove(round.id)))
                }
                beforeEdit = null
                // Entwurf und Anzeige in EINEM Schritt zuruecksetzen.
                val current = repository.session(sessionId)
                if (current != null) _draft.value = newDraft(current)
                committing = false
                _message.value = SessionMessage.Saved(closesEdit = editing)
            }.onFailure {
                committing = false
                _message.value = SessionMessage.Failed(it.readableMessage())
            }
        }
    }

    /** Verwirft die Korrektur und geht zurueck zur Eingabe der naechsten Runde. */
    fun cancelEdit() {
        beforeEdit = null
        viewModelScope.launch {
            val current = repository.session(sessionId) ?: return@launch
            _draft.value = newDraft(current)
        }
    }

    /**
     * Loescht eine Runde aus der Korrektur heraus und beendet die Korrektur
     * sofort. Frueher blieb der Bildschirm bis zum Ende der Snackbar stehen,
     * mit aktivem Speichern-Knopf fuer die gerade geloeschte Runde. Das Undo
     * steht jetzt im Abend.
     */
    fun deleteRound(roundId: String) {
        viewModelScope.launch {
            val existing = repository.session(sessionId)
                ?.rounds
                ?.firstOrNull { it.id == roundId }
                ?.round
            if (existing == null) {
                _message.value = SessionMessage.Failed("Diese Runde gibt es nicht mehr.")
                return@launch
            }
            runCatching { repository.deleteRound(roundId) }
                .onSuccess {
                    // Ein Tombstone laesst sich durch eine neue Revision mit
                    // demselben Inhalt wieder aufheben. Physisch geloescht wurde
                    // nichts, deshalb geht das ueberhaupt.
                    undoLog.record(sessionId, LastChange(LastChange.Kind.DELETED, existing, UndoToken.Restore(existing)))
                    beforeEdit = null
                    _message.value = SessionMessage.Saved(closesEdit = true)
                }
                .onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    /** Nimmt die letzte Aenderung des Abends zurueck. Ein zweiter Tap tut nichts. */
    fun undoLastChange() {
        val change = lastChange.value ?: return
        if (change.undone) return
        viewModelScope.launch {
            runCatching {
                when (val token = change.undo) {
                    is UndoToken.Remove -> repository.deleteRound(token.roundId)
                    is UndoToken.Restore -> repository.correctRound(token.round)
                }
            }.onSuccess {
                undoLog.markUndone(sessionId, change)
                _message.value = SessionMessage.Undone
            }.onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    fun endSession() {
        viewModelScope.launch {
            runCatching { repository.endSession(sessionId) }
                .onSuccess { _message.value = SessionMessage.Ended }
                .onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    fun discardSession() {
        viewModelScope.launch {
            runCatching { repository.discardSession(sessionId) }
                .onSuccess { _message.value = SessionMessage.Discarded }
                .onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Unbekannter Fehler"

    companion object {
        fun factory(graph: RotaskatGraph, sessionId: String) = viewModelFactory {
            initializer { SessionViewModel(graph.repository, sessionId, SessionUndoLog.process) }
        }
    }
}
