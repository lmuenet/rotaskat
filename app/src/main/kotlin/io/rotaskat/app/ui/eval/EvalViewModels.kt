package io.rotaskat.app.ui.eval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.RotaskatRepository
import io.rotaskat.app.data.SessionState
import io.rotaskat.shared.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

/**
 * Ein einzelner Abend, nur lesend.
 *
 * Bewusst nicht das SessionViewModel der Eingabe: die Auswertung braucht keinen
 * Entwurf, kein Undo und keine Schreibwege. Ein Bildschirm, der einen
 * abgeschlossenen Abend zeigt, soll gar nicht erst in der Lage sein, ihn zu
 * aendern.
 */
class SessionDetailViewModel(
    repository: RotaskatRepository,
    sessionId: String,
) : ViewModel() {

    val state: StateFlow<SessionState?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roster: StateFlow<List<Player>> = repository.observeRoster()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(graph: RotaskatGraph, sessionId: String) = viewModelFactory {
            initializer { SessionDetailViewModel(graph.repository, sessionId) }
        }
    }
}

/**
 * Alle Abende des Vereins, fuer Historie, Rangliste und Statistik.
 *
 * Der Zeitraum liegt hier und nicht im Bildschirm, damit die Wahl "Saison 2026"
 * eine Drehung des Geraets ueberlebt. Rangliste und Statistik bekommen dabei je
 * ein eigenes Exemplar - jedes Navigationsziel hat seinen eigenen Zeitraum, und
 * das ist gewollt: wer die Statistik einer Saison liest, will die Rangliste
 * daneben nicht mit umgestellt bekommen.
 *
 * Die Zeitzone kommt aus dem Geraet und wird einmal festgehalten. Sie
 * entscheidet, in welche Saison ein Abend faellt, der kurz vor Mitternacht des
 * 31. Dezember beginnt; sie waehrend der Anzeige zu wechseln, wuerde die
 * Rangliste unter der Hand umsortieren.
 */
class EvaluationViewModel(
    repository: RotaskatRepository,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val states: StateFlow<List<SessionState>> = repository.observeSessionStates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val roster: StateFlow<List<Player>> = repository.observeRoster()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Wie viele Runden noch auf den Server warten. Reine Anzeige und kein Tor:
     * die App funktioniert ohne Netz vollstaendig, die Zahl sagt nur, dass noch
     * etwas unterwegs ist.
     */
    val pendingSync: StateFlow<Int> = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _period = MutableStateFlow<Period>(Period.AllTime)
    val period: StateFlow<Period> = _period.asStateFlow()

    val seasons: StateFlow<List<Int>> = states
        .map { Statistics.seasons(it, zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Die Rangliste des gewaehlten Zeitraums, der beste Spieler zuerst. */
    val standings: StateFlow<List<PlayerStats>> =
        combine(states, roster, _period) { states, roster, period ->
            Statistics.standings(Statistics.filter(states, period, zone), roster)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setPeriod(period: Period) {
        _period.value = period
    }

    companion object {
        fun factory(graph: RotaskatGraph) = viewModelFactory {
            initializer { EvaluationViewModel(graph.repository) }
        }
    }
}
