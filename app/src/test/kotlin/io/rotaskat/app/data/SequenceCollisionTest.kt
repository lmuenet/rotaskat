package io.rotaskat.app.data

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.model.Session
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Was passiert, wenn ein uebernommenes Geraet die Historie nur teilweise
 * gezogen hat und der Schreiber trotzdem weiterspielt.
 *
 * `RoundDao.nextSequence` leitet die naechste Position ausschliesslich aus den
 * LOKAL bekannten Runden ab - anders geht es nicht, die App muss ohne Netz
 * vollstaendig funktionieren. Eine Kollision ist damit unvermeidlich und darf
 * deshalb kein Fehlerfall sein: das UNIQUE (session_id, sequence) aus V1 hat
 * genau diese Runde in einen 500er laufen lassen und den Sync des Geraets
 * dauerhaft blockiert. Seit V3 gibt es die Zusicherung nicht mehr, sortiert
 * wird ueber (sequence, id).
 */
@RunWith(RobolectricTestRunner::class)
class SequenceCollisionTest {

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

    @Test
    fun `nach einem abgebrochenen Pull vergibt der Client eine bereits belegte Position`() = runTest {
        repository.saveClub(TEST_CLUB)

        val session = Session(
            id = "session-1",
            clubId = CLUB_ID,
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            startedAt = T0,
            dealerSeat = 0,
        )

        // Seite 1 des Delta-Pull: die Session und ihre erste Runde. Die Runden
        // mit sequence 1 und 2 liegen auf dem Server und kommen erst mit der
        // naechsten Seite - die nach einem Netzabbruch nie eintrifft.
        repository.applyPull(
            sessions = listOf(SessionEnvelope(session = session, revision = 1)),
            rounds = listOf(
                RoundEnvelope(
                    round = suitRound("server-round-0", dealerSeat = 0, declarerSeat = 1),
                    sessionId = session.id,
                    sequence = 0,
                    revision = 1,
                ),
            ),
        )

        // Der Schreiber spielt weiter. Die Position kommt aus dem lokalen MAX.
        val id = repository.newRoundId()
        repository.recordRound(session.id, suitRound(id, dealerSeat = 1, declarerSeat = 2))

        val recorded = assertNotNull(repository.session(session.id)).rounds.single { it.id == id }
        assertEquals(
            1,
            recorded.sequence,
            "Die neue Runde bekommt Position 1 - dieselbe, die auf dem Server bereits " +
                "von einer anderen Runden-Id belegt sein kann. Das ist erlaubt: die " +
                "Position ordnet die Liste, sie identifiziert nichts.",
        )
    }
}
