package io.rotaskat.app.data

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.db.RotaskatDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Was das Undo mit der Sitzordnung macht.
 */
@RunWith(RobolectricTestRunner::class)
class RotationUndoAuditTest {

    private lateinit var database: RotaskatDatabase
    private lateinit var repository: RoomRotaskatRepository

    @Before
    fun setUp() {
        database = RotaskatDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = RoomRotaskatRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun startEvening(dealerSeat: Int = 0): String {
        repository.saveClub(TEST_CLUB)
        return repository.startSession(
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            dealerSeat = dealerSeat,
            startedAt = T0,
        )
    }

    private suspend fun dealer(sessionId: String): Int =
        assertNotNull(repository.session(sessionId)).session.dealerSeat

    @Test
    fun `Undo der spaeteren Runde setzt die Rotation korrekt zurueck`() = runTest {
        val sessionId = startEvening(dealerSeat = 0)
        repository.recordRound(sessionId, suitRound(repository.newRoundId(), dealerSeat = 0, declarerSeat = 1))
        val zweite = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(zweite, dealerSeat = 1, declarerSeat = 2))
        assertEquals(2, dealer(sessionId))

        repository.deleteRound(zweite)
        println("Geber nach Undo der zweiten Runde: ${dealer(sessionId)}")
        assertEquals(1, dealer(sessionId))
    }

    /**
     * Nach dem Undo der ERSTEN Runde bleibt keine lebende Runde uebrig. Der
     * Geber der geloeschten steht aber weiterhin im Rohdatensatz - der
     * Tombstone loescht ihn nicht -, also ist die Stellung rekonstruierbar.
     */
    @Test
    fun `Undo der ersten Runde stellt den Geber des Anpfiffs wieder her`() = runTest {
        val sessionId = startEvening(dealerSeat = 0)
        val erste = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(erste, dealerSeat = 0, declarerSeat = 1))
        assertEquals(1, dealer(sessionId))

        repository.deleteRound(erste)
        assertEquals(0, dealer(sessionId), "Der Abend startete mit Geber 0")
    }
}
