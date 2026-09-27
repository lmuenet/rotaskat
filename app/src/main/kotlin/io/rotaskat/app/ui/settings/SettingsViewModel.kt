package io.rotaskat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.RotaskatRepository
import io.rotaskat.app.data.id.Uuid7
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.shared.model.Club
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Verein, Kader und Geld - alles, was nach dem Einrichten noch zu aendern ist.
 *
 * Ohne Verein ist der Kader lokal und hier editierbar. Mit Verein kommt er vom
 * Server und ist nur lesbar: zwei Stellen, an denen derselbe Kader gepflegt
 * wird, liefen sofort auseinander.
 */
class SettingsViewModel(
    private val repository: RotaskatRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val club: StateFlow<Club?> = repository.observeClub()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val mode: StateFlow<AppMode?> = settings.mode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val seatedPlayerIds: StateFlow<Set<String>> = repository.observeSeatedPlayerIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val pendingSync: StateFlow<Int> = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** Warum die letzte Aenderung abgelehnt wurde, bis zur naechsten. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { _serverUrl.value = settings.serverUrl().ifBlank { null } }
    }

    fun addPlayer(name: String, onDone: () -> Unit) = edit(onDone) { it.addPlayer(name, Uuid7.next()) }

    fun renamePlayer(id: String, name: String, onDone: () -> Unit) = edit(onDone) { it.renamePlayer(id, name) }

    fun removePlayer(id: String) = edit { it.removePlayer(id, seatedPlayerIds.value) }

    fun setCentsPerPoint(cents: Int) = edit { it.withCentsPerPoint(cents) }

    fun clearError() {
        _error.value = null
    }

    private fun edit(onDone: () -> Unit = {}, change: (Club) -> RosterEdit) {
        viewModelScope.launch {
            if (settings.modeOrNull() != AppMode.LOCAL) {
                _error.value = "Im Verein werden Kader und Satz auf dem Server gepflegt."
                return@launch
            }
            val current = repository.club() ?: return@launch
            when (val result = change(current)) {
                is RosterEdit.Ok -> {
                    repository.saveClub(result.club)
                    _error.value = null
                    onDone()
                }
                is RosterEdit.Rejected -> _error.value = result.reason
            }
        }
    }

    companion object {
        fun factory(graph: RotaskatGraph) = viewModelFactory {
            initializer { SettingsViewModel(graph.repository, graph.settings) }
        }
    }
}
