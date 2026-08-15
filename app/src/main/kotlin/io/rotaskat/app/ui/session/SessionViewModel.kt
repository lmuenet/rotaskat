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
 * Was der Rundeneingabe nach einem Tap auf Gewonnen oder Verloren zurueckgemeldet
 * wird.
 *
 * Zwei Ergebnisse, zwei Kanaele: die Oberflaeche macht daraus eine Snackbar, die
 * Haptik macht daraus einen Doppelpuls oder einen langen Puls. Ein Dialog kommt
 * bewusst in keinem der beiden Faelle vor - bei dreissig Runden pro Abend wird
 * jeder Bestaetigungsdialog blind weggetippt und erhoeht die Fehlerzahl, statt
 * sie zu senken.
 */
sealed interface SessionMessage {

    val text: String

    data class Saved(
        override val text: String,
        val undo: UndoToken,
        /**
         * Die Korrektur ist damit fertig und der Bildschirm hat seinen Zweck
         * erfuellt. Der Rueckweg haengt dann NICHT am Ende der Snackbar: sonst
         * steht die Oberflaeche nach dem Speichern weiter auf "Runde
         * korrigieren", und es sieht aus, als haette der Tap nichts getan.
         */
        val closesEdit: Boolean = false,
    ) : SessionMessage

    data class Failed(override val text: String) : SessionMessage
}

/** Wie eine gerade gespeicherte Aenderung wieder zurueckgenommen wird. */
sealed interface UndoToken {

    /** Eine neu angelegte Runde: Tombstone setzen. */
    data class Remove(val roundId: String) : UndoToken

    /** Eine Korrektur: den vorherigen Stand als neue Revision zurueckschreiben. */
    data class Restore(val round: Round) : UndoToken
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
) : ViewModel() {

    val state: StateFlow<SessionState?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roster: StateFlow<List<Player>> = repository.observeRoster()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<RoundDraft?>(null)
    val draft: StateFlow<RoundDraft?> = _draft.asStateFlow()

    private val _message = MutableStateFlow<SessionMessage?>(null)
    val message: StateFlow<SessionMessage?> = _message.asStateFlow()

    /** Der Stand vor einer Korrektur, damit das Undo etwas hat, worauf es zeigt. */
    private var beforeEdit: Round? = null

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
        if (current.editing) return
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
        _draft.value = _draft.value?.let(transform)
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
     * der Snackbar.
     */
    fun commit(won: Boolean) {
        val draft = _draft.value ?: return
        val round = draft.toRound(won)
        if (round == null) {
            _message.value = SessionMessage.Failed("Die Runde ist noch nicht vollstaendig.")
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
        viewModelScope.launch {
            val result = runCatching {
                if (editing) repository.correctRound(round) else repository.recordRound(sessionId, round)
            }
            result.onSuccess {
                _message.value = if (editing && previous != null) {
                    SessionMessage.Saved("Runde geaendert", UndoToken.Restore(previous), closesEdit = true)
                } else {
                    SessionMessage.Saved(
                        if (won) "Gewonnen gespeichert" else "Verloren gespeichert",
                        UndoToken.Remove(round.id),
                    )
                }
                beforeEdit = null
                val current = repository.session(sessionId)
                if (current != null) _draft.value = newDraft(current)
            }.onFailure {
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
                    _message.value =
                        SessionMessage.Saved("Runde geloescht", UndoToken.Restore(existing))
                    if (_draft.value?.roundId == roundId) cancelEdit()
                }
                .onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    fun undo(token: UndoToken) {
        viewModelScope.launch {
            runCatching {
                when (token) {
                    is UndoToken.Remove -> repository.deleteRound(token.roundId)
                    is UndoToken.Restore -> repository.correctRound(token.round)
                }
            }.onFailure { _message.value = SessionMessage.Failed(it.readableMessage()) }
        }
    }

    fun endSession() {
        viewModelScope.launch {
            runCatching { repository.endSession(sessionId) }
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
            initializer { SessionViewModel(graph.repository, sessionId) }
        }
    }
}
