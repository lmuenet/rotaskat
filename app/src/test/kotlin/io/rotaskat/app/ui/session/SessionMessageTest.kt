package io.rotaskat.app.ui.session

import io.rotaskat.app.data.RotaskatRepository
import io.rotaskat.app.data.ScoredRound
import io.rotaskat.app.data.SessionState
import io.rotaskat.app.data.SessionSummary
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.suitRound
import io.rotaskat.app.ui.round.GamePick
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.scoring.Scoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wovon der Rueckweg aus der Korrektur abhaengt.
 *
 * Er hing frueher am Ende der Snackbar: die Oberflaeche stand nach dem
 * Speichern weiter auf "Runde korrigieren", bis jemand die Leiste wegwischte -
 * fuer den Nutzer sah es aus, als haette der Tap nichts getan, und er tippte
 * erneut. Die Snackbar hatte ausserdem keine Dauer mitbekommen; Material3
 * waehlt bei gesetztem actionLabel dann Indefinite, und die Leiste blieb genau
 * ueber den Ergebnisknoepfen der naechsten Runde stehen.
 *
 * Gegen ein Fake-Repository statt gegen Room: hier geht es um die Antwort des
 * ViewModels, und ein Test, der auf die Threads einer Datenbank wartet, sagt
 * darueber nichts aus, was er nicht auch ohne sie saegen koennte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionMessageTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `eine gespeicherte Korrektur meldet sich als abgeschlossen`() = runTest(dispatcher) {
        val roundId = repository.add(suitRound("r0", dealerSeat = 0, declarerSeat = 1))

        val viewModel = SessionViewModel(repository, SESSION_ID)
        viewModel.beginEdit(roundId)
        advanceUntilIdle()
        assertTrue(viewModel.draft.value!!.editing)

        viewModel.commit(won = false)
        advanceUntilIdle()

        val message = assertIs<SessionMessage.Saved>(viewModel.message.value)
        assertTrue(message.closesEdit, "Die Korrektur ist fertig, der Bildschirm hat seinen Zweck erfuellt")
        assertIs<UndoToken.Restore>(message.undo)
        assertEquals(false, repository.rounds.single().won)
    }

    /**
     * Eine geloeschte Runde ist ueber die Rundenliste nicht mehr erreichbar -
     * das Undo in der Snackbar ist der einzige Rueckweg. Der Bildschirm bleibt
     * deshalb stehen, bis die Leiste durch ist.
     */
    @Test
    fun `eine geloeschte Runde haelt den Bildschirm fuer das Undo`() = runTest(dispatcher) {
        val roundId = repository.add(suitRound("r0", dealerSeat = 0, declarerSeat = 1))

        val viewModel = SessionViewModel(repository, SESSION_ID)
        viewModel.beginEdit(roundId)
        advanceUntilIdle()

        viewModel.deleteRound(roundId)
        advanceUntilIdle()

        val message = assertIs<SessionMessage.Saved>(viewModel.message.value)
        assertFalse(message.closesEdit)
        assertIs<UndoToken.Restore>(message.undo)
    }

    /** Eine neue Runde kommt gar nicht aus der Korrektur - es gibt nichts zu verlassen. */
    @Test
    fun `eine neu eingetragene Runde schliesst keine Korrektur`() = runTest(dispatcher) {
        val viewModel = SessionViewModel(repository, SESSION_ID)
        advanceUntilIdle()

        viewModel.updateDraft { it.copy(declarerSeat = 1).withGame(GamePick.Grand) }
        viewModel.commit(won = true)
        advanceUntilIdle()

        val message = assertIs<SessionMessage.Saved>(viewModel.message.value)
        assertFalse(message.closesEdit)
        assertIs<UndoToken.Remove>(message.undo)
    }

    /**
     * Gerade so viel Repository, wie das ViewModel anfasst. Der Rest wirft:
     * ein stiller Vorgabewert wuerde einen Test gruen halten, der etwas ganz
     * anderes prueft, als er behauptet.
     */
    private class FakeRepository : RotaskatRepository {

        val rounds = mutableListOf<Round>()
        private val deleted = mutableSetOf<String>()

        private val session = Session(
            id = SESSION_ID,
            clubId = TEST_CLUB.id,
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            startedAt = T0,
            scoring = TEST_CLUB.scoring,
            dealerSeat = 0,
        )

        private val state = MutableStateFlow(stateOf())

        fun add(round: Round): String {
            rounds += round
            state.value = stateOf()
            return round.id
        }

        private fun stateOf(): SessionState {
            val scored = rounds.mapIndexed { index, round ->
                ScoredRound(
                    id = round.id,
                    sequence = index,
                    revision = 1,
                    round = round,
                    score = Scoring.score(round, session.scoring),
                    deletedAt = if (round.id in deleted) T0 else null,
                    pendingSync = false,
                )
            }
            val totals = (0 until session.seatCount).associateWith { seat ->
                scored.filterNot { it.deleted }.sumOf { (it.score.halfPoints[seat] ?: 0).toLong() }
            }
            return SessionState(session, scored, totals)
        }

        override fun observeSession(sessionId: String): Flow<SessionState?> = state.map { it }

        override suspend fun session(sessionId: String): SessionState? = state.value

        override fun observeRoster(): Flow<List<Player>> = flowOf(TEST_CLUB.roster)

        override fun newRoundId(): String = "r${rounds.size}"

        override suspend fun recordRound(sessionId: String, round: Round): String {
            add(round)
            return round.id
        }

        override suspend fun correctRound(round: Round) {
            val index = rounds.indexOfFirst { it.id == round.id }
            if (index >= 0) rounds[index] = round else rounds += round
            deleted -= round.id
            state.value = stateOf()
        }

        override suspend fun deleteRound(roundId: String, at: Instant) {
            deleted += roundId
            state.value = stateOf()
        }

        override fun observeClub(): Flow<Club?> = flowOf(TEST_CLUB)
        override fun observeOpenSession(): Flow<SessionState?> = state.map { it }
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeSessionStates(): Flow<List<SessionState>> = flowOf(listOf(state.value))
        override fun observePendingSyncCount(): Flow<Int> = flowOf(0)
        override suspend fun club(): Club = TEST_CLUB
        override suspend fun saveClub(club: Club) = error("Im Test nicht benutzt")
        override suspend fun startSession(
            seatCount: Int,
            seats: Map<Int, String>,
            dealerSeat: Int,
            startedAt: Instant,
        ): String = error("Im Test nicht benutzt")

        override suspend fun endSession(sessionId: String, endedAt: Instant) = error("Im Test nicht benutzt")
        override suspend fun setDealer(sessionId: String, dealerSeat: Int) = error("Im Test nicht benutzt")
    }

    private companion object {
        const val SESSION_ID = "session-1"
    }
}
