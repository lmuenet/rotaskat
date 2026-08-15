package io.rotaskat.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.RotaskatRepository
import io.rotaskat.app.data.id.Uuid7
import io.rotaskat.app.data.net.ApiFailureException
import io.rotaskat.app.data.net.RotaskatApi
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.app.data.settings.DeviceIdentity
import io.rotaskat.shared.api.ClubLookupRequest
import io.rotaskat.shared.api.JoinClubRequest
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wo der Beitritt gerade steht.
 *
 * Bewusst ein Summentyp statt dreier Boolean-Flaggen: "laedt gerade UND hat
 * einen Fehler UND kennt schon einen Kader" ist ein Zustand, den es nicht geben
 * darf, und den es so auch nicht geben kann.
 */
sealed interface JoinUiState {

    /** Adresse und Code werden eingegeben. */
    data class Entry(val error: String? = null) : JoinUiState

    data object Working : JoinUiState

    /** Code stimmt, jetzt muss der Beitretende sich im Kader finden. */
    data class ChoosePlayer(
        val club: Club,
        val error: String? = null,
    ) : JoinUiState

    /**
     * Beitritt steht, aber es liegen lokale Abende herum, die noch keinem
     * Vereinsmitglied zugeordnet sind.
     */
    data class MapPlayers(
        val club: Club,
        val localPlayers: List<Player>,
        /** Lokale Spieler-Id -> Vereinsmitglied. */
        val mapping: Map<String, String>,
        val sessionCount: Int,
        val error: String? = null,
    ) : JoinUiState {
        val complete: Boolean get() = localPlayers.all { mapping[it.id] != null }
    }

    data object Done : JoinUiState
}

/**
 * Der Einstieg in die App: entweder ohne Verein loslegen oder einem beitreten.
 *
 * Beide Wege enden im selben Zustand - ein Verein mit Kader liegt lokal, und die
 * App ist benutzbar. Der Unterschied ist nur, ob es dazu einen Server gibt.
 */
class OnboardingViewModel(
    private val repository: RotaskatRepository,
    private val api: RotaskatApi,
    private val settings: AppSettings,
) : ViewModel() {

    private val _join = MutableStateFlow<JoinUiState>(JoinUiState.Entry())
    val join: StateFlow<JoinUiState> = _join.asStateFlow()

    private val _localBusy = MutableStateFlow(false)
    val localBusy: StateFlow<Boolean> = _localBusy.asStateFlow()

    // --- Ohne Verein ------------------------------------------------------

    /**
     * Legt einen Verein an, der nur auf diesem Geraet existiert.
     *
     * Er ist technisch derselbe Verein wie ein echter, nur ohne Server dahinter.
     * Deshalb funktioniert danach alles ausser der vereinsweiten Rangliste, und
     * ein spaeterer Beitritt kann die Abende einfach mitnehmen.
     */
    fun createLocalClub(
        name: String,
        centsPerPoint: Int,
        playerNames: List<String>,
        onDone: () -> Unit,
    ) {
        if (_localBusy.value) return
        _localBusy.value = true
        viewModelScope.launch {
            try {
                val club = Club(
                    id = Uuid7.next(),
                    name = name.trim().ifBlank { "Skatrunde" },
                    centsPerPoint = centsPerPoint,
                    roster = playerNames
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { Player(id = Uuid7.next(), displayName = it) },
                )
                repository.saveClub(club)
                settings.setLocalMode(club.id)
                onDone()
            } finally {
                _localBusy.value = false
            }
        }
    }

    // --- Mit Verein -------------------------------------------------------

    /** Schritt 1: Adresse merken und den Kader zum Einladungscode holen. */
    fun lookup(serverUrl: String, inviteCode: String) {
        if (_join.value is JoinUiState.Working) return
        val url = serverUrl.trim()
        val code = inviteCode.trim()
        if (url.isBlank()) {
            _join.value = JoinUiState.Entry("Ohne Serveradresse geht es nicht.")
            return
        }
        if (code.isBlank()) {
            _join.value = JoinUiState.Entry("Der Einladungscode fehlt.")
            return
        }

        _join.value = JoinUiState.Working
        viewModelScope.launch {
            _join.value = try {
                // Die Adresse muss VOR dem Aufruf stehen, der Client liest sie
                // bei jedem Aufruf frisch aus den Einstellungen.
                settings.setServerUrl(url)
                val response = api.lookup(ClubLookupRequest(code))
                if (response.club.roster.isEmpty()) {
                    JoinUiState.Entry("Der Verein hat noch keinen Kader. Erst auf dem Server Spieler anlegen.")
                } else {
                    JoinUiState.ChoosePlayer(response.club)
                }
            } catch (error: Throwable) {
                JoinUiState.Entry(describe(error))
            }
        }
    }

    /**
     * Schritt 2: Token holen.
     *
     * Der lokale Kader wird VOR dem Beitritt gelesen. Danach steht der
     * Vereinskader in derselben Tabelle, und die Zuordnung haette keine
     * Ausgangsseite mehr.
     */
    fun join(inviteCode: String, playerId: String, deviceLabel: String?) {
        val current = _join.value
        if (current !is JoinUiState.ChoosePlayer) return

        _join.value = JoinUiState.Working
        viewModelScope.launch {
            try {
                val localPlayers = repository.observeRoster().first()
                val localSessions = repository.observeSessions().first()

                val response = api.join(
                    JoinClubRequest(
                        inviteCode = inviteCode.trim(),
                        playerId = playerId,
                        deviceLabel = deviceLabel?.trim()?.ifBlank { null },
                    )
                )
                settings.saveJoin(
                    token = response.token,
                    identity = DeviceIdentity(
                        deviceId = response.deviceId,
                        playerId = response.playerId,
                        clubId = response.club.id,
                    ),
                )

                val orphans = localPlayers.filter { it.id !in response.club.roster.map { m -> m.id } }
                if (localSessions.isEmpty() || orphans.isEmpty()) {
                    // Nichts zu uebernehmen: der Vereinskader ersetzt einfach den
                    // lokalen.
                    repository.saveClub(response.club)
                    _join.value = JoinUiState.Done
                } else {
                    _join.value = JoinUiState.MapPlayers(
                        club = response.club,
                        localPlayers = orphans,
                        mapping = suggestMapping(orphans, response.club.roster),
                        sessionCount = localSessions.size,
                    )
                }
            } catch (error: Throwable) {
                _join.value = JoinUiState.ChoosePlayer(current.club, describe(error))
            }
        }
    }

    /** Schritt 3, nur nach lokalem Betrieb: einen lokalen Spieler zuordnen. */
    fun assign(localPlayerId: String, clubPlayerId: String) {
        val current = _join.value
        if (current !is JoinUiState.MapPlayers) return
        _join.value = current.copy(mapping = current.mapping + (localPlayerId to clubPlayerId), error = null)
    }

    /** Schritt 3 abschliessen: die lokalen Abende gehoeren jetzt dem Verein. */
    fun adopt() {
        val current = _join.value
        if (current !is JoinUiState.MapPlayers || !current.complete) return

        _join.value = JoinUiState.Working
        viewModelScope.launch {
            _join.value = try {
                repository.adoptLocalData(current.club, current.mapping)
                JoinUiState.Done
            } catch (error: Throwable) {
                current.copy(error = describe(error))
            }
        }
    }

    /**
     * Die lokalen Abende NICHT uebernehmen.
     *
     * Sie werden dabei geloescht und nicht etwa stillgelegt: sie zeigen auf
     * Spieler, die es nach dem Vereinsbeitritt nicht mehr gibt, und waeren in
     * jeder Auswertung eine Reihe von Platzhaltern. Der Bildschirm sagt das
     * vorher deutlich.
     */
    fun discardLocal() {
        val current = _join.value
        if (current !is JoinUiState.MapPlayers) return
        _join.value = JoinUiState.Working
        viewModelScope.launch {
            _join.value = try {
                repository.saveClub(current.club)
                JoinUiState.Done
            } catch (error: Throwable) {
                current.copy(error = describe(error))
            }
        }
    }

    fun backToEntry() {
        _join.value = JoinUiState.Entry()
    }

    /**
     * Schlaegt fuer jeden lokalen Spieler das Vereinsmitglied mit demselben
     * Namen vor. Der haeufige Fall ist, dass dieselben vier Leute lokal und im
     * Verein gleich heissen - dann ist die Zuordnung fertig, bevor der
     * Bildschirm erscheint.
     */
    private fun suggestMapping(local: List<Player>, roster: List<Player>): Map<String, String> {
        val byName = roster.associateBy { it.displayName.trim().lowercase() }
        return local.mapNotNull { player ->
            byName[player.displayName.trim().lowercase()]?.let { player.id to it.id }
        }.toMap()
    }

    /**
     * Fehlertexte fuer den Tisch, nicht fuer das Log.
     *
     * Ein Stacktrace hilft hier niemandem; was hilft, ist die Unterscheidung
     * zwischen "Code falsch" und "Server nicht erreichbar", weil beide etwas
     * voellig anderes zu tun bedeuten.
     */
    private fun describe(error: Throwable): String = when {
        error is ApiFailureException && error.status.value == 403 ->
            "Einladungscode unbekannt. Stimmt der Code, und zeigt die Adresse auf den richtigen Server?"

        error is ApiFailureException ->
            "Der Server antwortet mit ${error.status}. Laeuft dort schon eine passende Fassung?"

        else ->
            "Server nicht erreichbar. Adresse pruefen - und ohne Verein loslegen geht immer."
    }

    companion object {
        fun factory(graph: RotaskatGraph) = viewModelFactory {
            initializer { OnboardingViewModel(graph.repository, graph.api, graph.settings) }
        }
    }
}
