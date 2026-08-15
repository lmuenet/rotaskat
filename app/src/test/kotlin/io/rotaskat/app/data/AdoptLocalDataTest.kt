package io.rotaskat.app.data

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
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
 * Der Weg vom lokalen Betrieb in einen Verein.
 *
 * Das ist der erwartete Ablauf und nicht der Sonderfall: man spielt erst ein
 * paar Abende ohne Server und richtet den Server spaeter ein. Geht dabei etwas
 * schief, sind genau die ersten Abende weg - also die, an denen die App sich
 * bewiesen hat.
 */
@RunWith(RobolectricTestRunner::class)
class AdoptLocalDataTest {

    private lateinit var database: RotaskatDatabase
    private lateinit var repository: RoomRotaskatRepository

    private val localClub = Club(
        id = "lokal-1",
        name = "Kuechentisch",
        centsPerPoint = 5,
        roster = listOf(
            Player("lokal-anna", "Anna"),
            Player("lokal-bert", "Bert"),
            Player("lokal-carl", "Carl"),
        ),
    )

    @Before
    fun setUp() {
        database = RotaskatDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = RoomRotaskatRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Ein lokaler Abend mit zwei Runden, so wie er vor dem Beitritt daliegt. */
    private suspend fun playLocalEvening(): String {
        repository.saveClub(localClub)
        val sessionId = repository.startSession(
            seatCount = 3,
            seats = mapOf(0 to "lokal-anna", 1 to "lokal-bert", 2 to "lokal-carl"),
            dealerSeat = 0,
            startedAt = T0,
        )
        repository.recordRound(sessionId, threeHandedRound(dealerSeat = 0, declarerSeat = 1))
        repository.recordRound(sessionId, threeHandedRound(dealerSeat = 1, declarerSeat = 2))
        return sessionId
    }

    /** Ein gewonnenes Kreuzspiel am Dreiertisch: hier setzt niemand aus. */
    private fun threeHandedRound(dealerSeat: Int, declarerSeat: Int) = Round(
        id = repository.newRoundId(),
        seatCount = 3,
        declarerSeat = declarerSeat,
        sittingOutSeat = null,
        declaration = SuitGame(Suit.CLUBS, matadors = 1),
        won = true,
        dealerSeat = dealerSeat,
    )

    @Test
    fun `lokale Abende landen mit umgehaengter Sitzordnung beim Verein`() = runTest {
        val sessionId = playLocalEvening()

        repository.adoptLocalData(
            club = TEST_CLUB,
            playerMapping = mapOf(
                "lokal-anna" to "p0",
                "lokal-bert" to "p1",
                "lokal-carl" to "p2",
            ),
        )

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(CLUB_ID, state.session.clubId, "Der Abend gehoert jetzt dem Verein")
        assertEquals(
            mapOf(0 to "p0", 1 to "p1", 2 to "p2"),
            state.session.seats,
            "Die Sitzordnung zeigt auf Vereinsmitglieder",
        )
        assertEquals(2, state.liveRounds.size, "Die Runden sind unveraendert da")
    }

    @Test
    fun `die Runden selbst bleiben unangetastet`() = runTest {
        val sessionId = playLocalEvening()
        val before = assertNotNull(repository.session(sessionId)).liveRounds.map { it.round }

        repository.adoptLocalData(
            club = TEST_CLUB,
            playerMapping = mapOf("lokal-anna" to "p0", "lokal-bert" to "p1", "lokal-carl" to "p2"),
        )

        val after = assertNotNull(repository.session(sessionId)).liveRounds.map { it.round }
        // Eine Runde kennt nur Sitzplatznummern, keine Spieler. Deshalb genuegt
        // es, die Sitzordnung umzuhaengen - wuerde hier etwas abweichen, waere
        // die Abrechnung des ganzen Abends verschoben.
        assertEquals(before, after)
    }

    @Test
    fun `nach der Uebernahme warten alle Abende und Runden auf den Server`() = runTest {
        val sessionId = playLocalEvening()

        repository.adoptLocalData(
            club = TEST_CLUB,
            playerMapping = mapOf("lokal-anna" to "p0", "lokal-bert" to "p1", "lokal-carl" to "p2"),
        )

        val state = assertNotNull(repository.session(sessionId))
        assertTrue(state.rounds.all { it.pendingSync }, "Bis hierhin hat keine Runde je einen Server gesehen")
        assertEquals(2, repository.observePendingSyncCount().first())
    }

    @Test
    fun `der lokale Verein verschwindet samt Kader`() = runTest {
        playLocalEvening()

        repository.adoptLocalData(
            club = TEST_CLUB,
            playerMapping = mapOf("lokal-anna" to "p0", "lokal-bert" to "p1", "lokal-carl" to "p2"),
        )

        val club = assertNotNull(repository.club())
        assertEquals(CLUB_ID, club.id)
        val roster = repository.observeRoster().first()
        assertEquals(
            TEST_CLUB.roster.map { it.id }.toSet(),
            roster.map { it.id }.toSet(),
            "Es bleibt genau ein Kader uebrig, der des Vereins",
        )
    }

    @Test
    fun `eine unvollstaendige Zuordnung bricht ab, bevor irgendetwas umgehaengt wird`() = runTest {
        val sessionId = playLocalEvening()

        assertFailsWith<IllegalArgumentException> {
            repository.adoptLocalData(
                club = TEST_CLUB,
                // Carl fehlt.
                playerMapping = mapOf("lokal-anna" to "p0", "lokal-bert" to "p1"),
            )
        }

        // Der entscheidende Teil: nichts ist halb passiert. Ein Abend mit
        // gemischter Sitzordnung - halb lokale, halb echte Ids - waere in jeder
        // Auswertung Unsinn und liesse sich nicht mehr reparieren.
        val state = assertNotNull(repository.session(sessionId))
        assertEquals("lokal-1", state.session.clubId)
        assertEquals(
            mapOf(0 to "lokal-anna", 1 to "lokal-bert", 2 to "lokal-carl"),
            state.session.seats,
        )
        assertEquals("lokal-1", assertNotNull(repository.club()).id)
    }

    @Test
    fun `eine Zuordnung auf Spieler ausserhalb des Kaders wird abgelehnt`() = runTest {
        playLocalEvening()

        assertFailsWith<IllegalArgumentException> {
            repository.adoptLocalData(
                club = TEST_CLUB,
                playerMapping = mapOf(
                    "lokal-anna" to "p0",
                    "lokal-bert" to "p1",
                    "lokal-carl" to "gibt-es-nicht",
                ),
            )
        }
    }

    @Test
    fun `zwei lokale Spieler duerfen auf dasselbe Mitglied zeigen`() = runTest {
        // Kommt vor, wenn jemand lokal doppelt angelegt wurde ("Bert" und
        // "Bert S."). Das ist kein Fehler, sondern eine Zusammenlegung.
        val sessionId = playLocalEvening()

        repository.adoptLocalData(
            club = TEST_CLUB,
            playerMapping = mapOf(
                "lokal-anna" to "p0",
                "lokal-bert" to "p1",
                "lokal-carl" to "p1",
            ),
        )

        val state = assertNotNull(repository.session(sessionId))
        assertEquals(mapOf(0 to "p0", 1 to "p1", 2 to "p1"), state.session.seats)
    }

    @Test
    fun `ohne lokale Abende ist die Uebernahme ein reiner Vereinswechsel`() = runTest {
        repository.saveClub(localClub)

        repository.adoptLocalData(club = TEST_CLUB, playerMapping = emptyMap())

        assertEquals(CLUB_ID, assertNotNull(repository.club()).id)
        assertNull(repository.observeOpenSession().first())
    }
}
