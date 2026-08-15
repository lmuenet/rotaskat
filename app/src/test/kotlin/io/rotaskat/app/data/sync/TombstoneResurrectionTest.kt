package io.rotaskat.app.data.sync

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.FakeApi
import io.rotaskat.app.data.FakeAppSettings
import io.rotaskat.app.data.RoomRotaskatRepository
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.app.data.net.PushResult
import io.rotaskat.app.data.suitRound
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncPushResponse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Was mit einem Tombstone passiert, wenn der Server ihn hat und das Geraet
 * nicht - der Fall "wiederhergestelltes Backup", den
 * `RoomRotaskatRepository.resolveConflicts` selbst benennt.
 *
 * `AndroidManifest.xml` hat `allowBackup="true"` ohne Ausschlussregeln, die
 * Room-Datei faehrt also mit dem Android-Backup mit.
 */
@RunWith(RobolectricTestRunner::class)
class TombstoneResurrectionTest {

    private lateinit var database: RotaskatDatabase
    private lateinit var repository: RoomRotaskatRepository
    private lateinit var api: FakeApi
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        database = RotaskatDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = RoomRotaskatRepository(database)
        api = FakeApi()
        engine = SyncEngine(repository, api, FakeAppSettings())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `der Konfliktpfad uebernimmt den Tombstone des Servers`() = runTest {
        repository.saveClub(TEST_CLUB)
        val sessionId = repository.startSession(
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            startedAt = T0,
        )
        val roundId = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(roundId, dealerSeat = 0, declarerSeat = 1))
        // Der lokale Stand ist Revision 2 und NICHT geloescht.
        repository.correctRound(suitRound(roundId, dealerSeat = 0, declarerSeat = 1, won = false))

        // Der Server fuehrt dieselbe Revision 2 - dort ist die Runde aber
        // geloescht. Gleiche Revision, anderer Inhalt: das ist der 409.
        var abgelehnt = false
        api.onPush = { request ->
            val runde = request.rounds.firstOrNull { it.round.id == roundId }
            if (runde != null && !abgelehnt && runde.revision == 2L) {
                abgelehnt = true
                PushResult.Conflicted(
                    SyncPushResponse(
                        cursor = 0,
                        conflicts = listOf(
                            SyncConflict(
                                id = roundId,
                                kind = "round",
                                revision = 2,
                                serverContentHash = "tombstone",
                                clientContentHash = "lebendig",
                                deleted = true,
                            )
                        ),
                    )
                )
            } else {
                PushResult.Applied(SyncPushResponse(cursor = 1))
            }
        }

        engine.syncOnce()

        val danach = repository.session(sessionId)!!.rounds.single { it.id == roundId }
        assertNotNull(
            danach.deletedAt,
            "Gegen einen Tombstone gewinnt der lokale Stand nicht: eine Loeschung kann ein " +
                "wiederhergestelltes Backup gar nicht kennen",
        )
        assertEquals(2, danach.revision, "Derselbe Stand wie der Server, nicht eine Revision darueber")
        assertEquals(0, repository.pendingRounds(10).size, "Nichts mehr zu schicken")
        assertTrue(
            api.pushes.none { push -> push.rounds.any { it.round.id == roundId && it.revision > 2 } },
            "Es wird keine hoehere Revision nachgeschoben, die die Runde wiederbeleben wuerde",
        )
    }

    /**
     * Die stille Variante desselben Problems: bleibt die lokale Revision gleich
     * und ist nichts als pendingSync markiert, kommt es nie zu einem Push. Der
     * Delta-Pull uebersprang die Runde dann wegen `revision <= local.revision`,
     * und Server und Geraet liefen dauerhaft auseinander, ohne dass irgendwo ein
     * Konflikt sichtbar wurde.
     */
    @Test
    fun `ein Tombstone gleicher Revision wird vom Pull uebernommen`() = runTest {
        repository.saveClub(TEST_CLUB)
        val sessionId = repository.startSession(
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            startedAt = T0,
        )
        val roundId = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(roundId, dealerSeat = 0, declarerSeat = 1))
        engine.syncOnce()

        val envelope = repository.pendingRounds(10)
        assertTrue(envelope.isEmpty())

        // Der Server liefert dieselbe Revision, aber als Tombstone.
        val serverStand = repository.session(sessionId)!!.rounds.single { it.id == roundId }
        api.onPull = { since ->
            if (since == 0L) {
                io.rotaskat.shared.api.SyncPullResponse(
                    cursor = 5,
                    rounds = listOf(
                        io.rotaskat.shared.api.RoundEnvelope(
                            round = serverStand.round,
                            sessionId = sessionId,
                            sequence = serverStand.sequence,
                            revision = serverStand.revision,
                            deletedAt = T0,
                        )
                    ),
                )
            } else {
                io.rotaskat.shared.api.SyncPullResponse(cursor = since)
            }
        }

        engine.syncOnce()

        val danach = repository.session(sessionId)!!.rounds.single { it.id == roundId }
        assertEquals(
            T0,
            danach.deletedAt,
            "Der Tombstone wird uebernommen, sonst zaehlt der Abend lokal eine Runde mit, " +
                "die der Server nicht mehr fuehrt",
        )
        assertTrue(danach.deleted)
    }
}
