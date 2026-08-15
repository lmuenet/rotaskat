package io.rotaskat.app.data.sync

import androidx.test.core.app.ApplicationProvider
import io.rotaskat.app.data.CLUB_ID
import io.rotaskat.app.data.FakeApi
import io.rotaskat.app.data.FakeAppSettings
import io.rotaskat.app.data.RoomRotaskatRepository
import io.rotaskat.app.data.T0
import io.rotaskat.app.data.TEST_CLUB
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.app.data.net.NotJoinedException
import io.rotaskat.app.data.net.PushResult
import io.rotaskat.app.data.suitRound
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushResponse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    private lateinit var database: RotaskatDatabase
    private lateinit var repository: RoomRotaskatRepository
    private lateinit var api: FakeApi
    private lateinit var settings: FakeAppSettings
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        database = RotaskatDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = RoomRotaskatRepository(database)
        api = FakeApi()
        settings = FakeAppSettings()
        engine = SyncEngine(repository, api, settings)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun eveningWithOneRound(): Pair<String, String> {
        repository.saveClub(TEST_CLUB)
        val sessionId = repository.startSession(
            seatCount = 4,
            seats = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            startedAt = T0,
        )
        val roundId = repository.newRoundId()
        repository.recordRound(sessionId, suitRound(roundId, dealerSeat = 0, declarerSeat = 1))
        return sessionId to roundId
    }

    @Test
    fun `Ohne Beitritt passiert nichts`() = runTest {
        settings.deviceIdentity = null
        assertFailsWith<NotJoinedException> { engine.syncOnce() }
        assertTrue(api.pushes.isEmpty())
    }

    @Test
    fun `Der Push schickt Runde und Session und raeumt danach die Markierung ab`() = runTest {
        val (sessionId, roundId) = eveningWithOneRound()

        val summary = engine.syncOnce()

        assertEquals(1, api.pushes.size, "Es haette genau ein Batch sein muessen")
        val batch = api.pushes.single()
        assertEquals(listOf(roundId), batch.rounds.map { it.round.id })
        assertEquals(listOf(sessionId), batch.sessions.map { it.session.id })
        assertEquals(1, summary.pushedRounds)

        // Nach der Bestaetigung wartet nichts mehr.
        assertTrue(repository.pendingRounds(10).isEmpty())
        assertTrue(repository.pendingSessions(10).isEmpty())
    }

    @Test
    fun `Die mitgeschickten Punkte sind die Pruefsumme der eingefrorenen Regeln`() = runTest {
        eveningWithOneRound()
        engine.syncOnce()

        val envelope = api.pushes.single().rounds.single()
        // Kreuz mit 1 ist 24, gewonnen: Alleinspieler +2V, Gegenspieler je -V,
        // Aussetzer 0. Genau das rechnet der Server nach.
        assertEquals(mapOf(0 to 0, 1 to 48, 2 to -24, 3 to -24), envelope.clientHalfPoints)
    }

    @Test
    fun `Ein zweiter Lauf ohne neue Runden schickt keinen Batch`() = runTest {
        eveningWithOneRound()
        engine.syncOnce()
        api.pushes.clear()

        engine.syncOnce()

        assertTrue(api.pushes.isEmpty(), "Es wurde ohne Anlass gepusht")
    }

    @Test
    fun `Ein Revisionskonflikt hebt die lokale Revision und geht danach durch`() = runTest {
        val (_, roundId) = eveningWithOneRound()

        var abgelehnt = false
        api.onPush = { request ->
            if (!abgelehnt) {
                abgelehnt = true
                PushResult.Conflicted(
                    SyncPushResponse(
                        cursor = 0,
                        conflicts = listOf(
                            SyncConflict(
                                id = roundId,
                                kind = "round",
                                revision = request.rounds.single().revision,
                                serverContentHash = "aaaa",
                                clientContentHash = "bbbb",
                            )
                        ),
                    )
                )
            } else {
                PushResult.Applied(SyncPushResponse(cursor = 7))
            }
        }

        val summary = engine.syncOnce()

        assertEquals(1, summary.conflicts)
        assertEquals(2, api.pushes.size, "Der Batch wurde nicht wiederholt")
        // Der zweite Versuch traegt eine hoehere Revision, sonst kaeme derselbe
        // Konflikt fuer immer zurueck.
        assertTrue(api.pushes[1].rounds.single().revision > api.pushes[0].rounds.single().revision)
        assertTrue(repository.pendingRounds(10).isEmpty())
    }

    @Test
    fun `Der Delta-Pull uebernimmt fremde Runden und merkt sich den Cursor`() = runTest {
        val (sessionId, _) = eveningWithOneRound()
        val fremdeRunde = repository.newRoundId()

        val sessionEnvelope = assertNotNull(repository.sessionEnvelopes(setOf(sessionId)).singleOrNull())
        api.onPull = { since ->
            if (since == 0L) {
                SyncPullResponse(
                    cursor = 42,
                    sessions = listOf(sessionEnvelope.copy(revision = 99)),
                    rounds = listOf(
                        RoundEnvelope(
                            round = suitRound(fremdeRunde, dealerSeat = 1, declarerSeat = 2),
                            sessionId = sessionId,
                            sequence = 1,
                            revision = 5,
                        )
                    ),
                )
            } else {
                SyncPullResponse(cursor = since)
            }
        }

        val summary = engine.syncOnce()

        assertEquals(42L, summary.cursor)
        assertEquals(42L, repository.cursor(CLUB_ID))
        val state = assertNotNull(repository.session(sessionId))
        assertTrue(state.rounds.any { it.id == fremdeRunde })
        // Eine gepullte Runde wartet nicht auf den Upload, sie kam ja von dort.
        assertEquals(false, state.rounds.single { it.id == fremdeRunde }.pendingSync)

        // Der naechste Lauf setzt beim gemerkten Cursor an.
        api.pullCursors.clear()
        engine.syncOnce()
        assertEquals(listOf(42L), api.pullCursors)
    }

    @Test
    fun `Ein aelterer Serverstand ueberschreibt die frische Korrektur am Tisch nicht`() = runTest {
        val (sessionId, roundId) = eveningWithOneRound()
        // Am Tisch wird korrigiert: Revision 2.
        repository.correctRound(suitRound(roundId, dealerSeat = 0, declarerSeat = 1, won = false))

        api.onPull = { since ->
            if (since == 0L) {
                SyncPullResponse(
                    cursor = 3,
                    rounds = listOf(
                        RoundEnvelope(
                            round = suitRound(roundId, dealerSeat = 0, declarerSeat = 1, won = true),
                            sessionId = sessionId,
                            sequence = 0,
                            revision = 1,
                        )
                    ),
                )
            } else {
                SyncPullResponse(cursor = since)
            }
        }

        engine.syncOnce()

        val runde = assertNotNull(repository.session(sessionId)).rounds.single { it.id == roundId }
        assertEquals(2L, runde.revision)
        assertEquals(false, runde.round.won, "Die Korrektur am Tisch wurde ueberschrieben")
    }

    @Test
    fun `Der Pull schleift ueber Seitengrenzen bis hasMore faellt`() = runTest {
        eveningWithOneRound()
        api.onPull = { since ->
            when (since) {
                0L -> SyncPullResponse(cursor = 10, hasMore = true)
                // Eine Seite darf leer sein und der Cursor trotzdem vorankommen.
                10L -> SyncPullResponse(cursor = 20, hasMore = true)
                else -> SyncPullResponse(cursor = 25, hasMore = false)
            }
        }

        engine.syncOnce()

        assertEquals(listOf(0L, 10L, 20L), api.pullCursors)
        assertEquals(25L, repository.cursor(CLUB_ID))
    }

    @Test
    fun `Ein stehender Cursor mit hasMore bricht ab statt zu kreisen`() = runTest {
        eveningWithOneRound()
        api.onPull = { SyncPullResponse(cursor = 0, hasMore = true) }

        engine.syncOnce()

        assertEquals(1, api.pullCursors.size, "Der Pull haette sich festgefressen")
    }

    @Test
    fun `Ein Netzfehler kommt als Ausnahme heraus, damit der Worker es erneut versucht`() = runTest {
        eveningWithOneRound()
        api.onPush = { error("Kein Netz in der Kneipe") }

        assertFailsWith<IllegalStateException> { engine.syncOnce() }
        // Nichts verloren: die Runde wartet weiter.
        assertEquals(1, repository.pendingRounds(10).size)
    }
}
