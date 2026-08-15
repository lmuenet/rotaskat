package io.rotaskat.app.data

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Datenschicht gegen eine In-Memory-Room-Datenbank. Kein Emulator, keine
 * Instrumentierung - Robolectric liefert die SQLite-Implementierung.
 */
@RunWith(RobolectricTestRunner::class)
class RoomRotaskatRepositoryTest {

    private lateinit var database: RotaskatDatabase
    private lateinit var repository: RoomRotaskatRepository
    private var syncRequests = 0

    @Before
    fun setUp() {
        database = RotaskatDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = RoomRotaskatRepository(database) { syncRequests++ }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun startEvening(
        club: Club = TEST_CLUB,
        dealerSeat: Int = 0,
    ): String {
        repository.saveClub(club)
        return repository.startSession(
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            dealerSeat = dealerSeat,
            startedAt = T0,
        )
    }

    @Test
    fun `Eine zweimal gespeicherte Runde existiert genau einmal`() = runTest {
        val sessionId = startEvening()
        val round = suitRound(id = repository.newRoundId(), dealerSeat = 0, declarerSeat = 1)

        repository.recordRound(sessionId, round)
        repository.recordRound(sessionId, round)

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(1, state.rounds.size, "Der Doppeltap hat eine zweite Runde erzeugt")
        assertEquals(1L, state.rounds.single().revision, "Der Doppeltap hat eine Revision gezogen")
    }

    @Test
    fun `Eine Korrektur ist eine neue Revision und kein neuer Datensatz`() = runTest {
        val sessionId = startEvening()
        val id = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(id, dealerSeat = 0, declarerSeat = 1, won = true))

        repository.correctRound(suitRound(id, dealerSeat = 0, declarerSeat = 1, won = false))

        val rounds = assertNotNull(repository.session(sessionId)).rounds
        assertEquals(1, rounds.size)
        assertEquals(2L, rounds.single().revision)
        assertTrue(rounds.single().pendingSync)
        // Kreuz mit 1 ist 24; verloren macht das -4 * 24 halbe Punkte.
        assertEquals(-96, rounds.single().score.halfPoints.getValue(1))
    }

    @Test
    fun `Loeschen setzt einen Tombstone und entfernt nichts`() = runTest {
        val sessionId = startEvening()
        val id = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(id, dealerSeat = 0, declarerSeat = 1))

        repository.deleteRound(id, at = T0)

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(1, state.rounds.size, "Die Runde wurde physisch geloescht")
        assertTrue(state.rounds.single().deleted)
        assertTrue(state.liveRounds.isEmpty())
        // Eine geloeschte Runde zaehlt nicht mehr mit.
        assertEquals(0L, state.totals.values.sumOf { kotlin.math.abs(it) })
    }

    @Test
    fun `Eine neue Runde bekommt die naechste Position, auch nach einem Undo`() = runTest {
        val sessionId = startEvening()
        val first = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(first, dealerSeat = 0, declarerSeat = 1))
        repository.deleteRound(first)

        val second = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(second, dealerSeat = 1, declarerSeat = 2))

        val rounds = assertNotNull(repository.session(sessionId)).rounds
        // Die Position wandert ueber den Tombstone hinweg weiter, sonst waeren
        // geloeschte und neue Runde im Verlauf nicht auseinanderzuhalten.
        assertEquals(listOf(0, 1), rounds.map { it.sequence })
    }

    @Test
    fun `Die Rotation wandert nach jeder Runde einen Platz weiter`() = runTest {
        val sessionId = startEvening(dealerSeat = 0)
        assertEquals(0, assertNotNull(repository.session(sessionId)).session.dealerSeat)

        repository.recordRound(sessionId, suitRound(repository.newRoundId(), dealerSeat = 0, declarerSeat = 1))
        assertEquals(1, assertNotNull(repository.session(sessionId)).session.dealerSeat)

        repository.recordRound(sessionId, suitRound(repository.newRoundId(), dealerSeat = 1, declarerSeat = 2))
        assertEquals(2, assertNotNull(repository.session(sessionId)).session.dealerSeat)
    }

    @Test
    fun `Ein Tap korrigiert die Rotation`() = runTest {
        val sessionId = startEvening(dealerSeat = 0)
        repository.setDealer(sessionId, 3)
        assertEquals(3, assertNotNull(repository.session(sessionId)).session.dealerSeat)
    }

    @Test
    fun `Die Session rechnet mit der eingefrorenen ScoringConfig weiter`() = runTest {
        val club = TEST_CLUB.copy(scoring = ScoringConfig(jungfrauDoubles = true))
        val sessionId = startEvening(club)

        // Der Verein aendert die Hausregel MITTEN im Abend.
        repository.saveClub(club.copy(scoring = ScoringConfig(jungfrauDoubles = false)))

        val frozen = assertNotNull(repository.session(sessionId)).session
        assertEquals(ScoringConfig(jungfrauDoubles = true), frozen.scoring)
        assertEquals(ScoringConfig(jungfrauDoubles = false), assertNotNull(repository.club()).scoring)
    }

    @Test
    fun `Der Abend ist nullsummig und die Abrechnung geht auf`() = runTest {
        val sessionId = startEvening()
        repository.recordRound(sessionId, suitRound(repository.newRoundId(), 0, 1, won = true))
        repository.recordRound(sessionId, suitRound(repository.newRoundId(), 1, 2, won = false))
        repository.recordRound(sessionId, suitRound(repository.newRoundId(), 2, 3, matadors = 2))

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(0L, state.totals.values.sum(), "Der Abend ist nicht nullsummig")
        assertEquals(4, state.totals.size, "Es fehlen Sitzplaetze in der Summe")

        val settlement = state.settlement()
        assertEquals(0L, settlement.balances.sumOf { it.cents })
        // Greedy liefert hoechstens n-1 Zahlungen.
        assertTrue(settlement.payments.size <= 3, "Zu viele Zahlungen: ${settlement.payments}")
    }

    @Test
    fun `Sitzordnung und Zeitstempel ueberleben den Weg durch die Datenbank`() = runTest {
        val sessionId = startEvening()
        val session = assertNotNull(repository.session(sessionId)).session

        assertEquals(mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"), session.seats)
        // Nicht auf Millisekunden gekuerzt: der serverseitige Inhalts-Hash
        // haengt an genau diesem Wert.
        assertEquals(T0, session.startedAt)
        assertEquals(CLUB_ID, session.clubId)
    }

    @Test
    fun `Abend beenden schliesst genau einen Abend`() = runTest {
        val sessionId = startEvening()
        assertNotNull(repository.observeOpenSession().first())

        repository.endSession(sessionId, endedAt = T0)

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(SessionStatus.CLOSED, state.session.status)
        assertEquals(T0, state.session.endedAt)
        assertNull(repository.observeOpenSession().first())
    }

    @Test
    fun `Eine strukturell ungueltige Runde wird gar nicht erst gespeichert`() = runTest {
        val sessionId = startEvening()
        // Am Vierertisch muss der Geber aussetzen; hier tut er es nicht.
        val kaputt = suitRound(repository.newRoundId(), dealerSeat = 0, declarerSeat = 1)
            .copy(sittingOutSeat = 2)

        assertFailsWith<IllegalArgumentException> { repository.recordRound(sessionId, kaputt) }
        assertTrue(assertNotNull(repository.session(sessionId)).rounds.isEmpty())
    }

    @Test
    fun `Jede Schreiboperation stoesst den Sync an, ohne auf ihn zu warten`() = runTest {
        val sessionId = startEvening()
        val vorher = syncRequests
        repository.recordRound(sessionId, suitRound(repository.newRoundId(), 0, 1))
        assertTrue(syncRequests > vorher, "Nach der Runde wurde kein Sync angestossen")
    }
}
