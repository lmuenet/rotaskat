package io.rotaskat.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.rotaskat.server.repo.PullPage
import io.rotaskat.server.repo.RotaskatRepository
import io.rotaskat.server.repo.RoundWrite
import io.rotaskat.server.repo.pullPageOf
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Cursor des Delta-Pull darf niemals weiter sein als die Auslieferung.
 *
 * Frueher las die Route in DREI getrennten Transaktionen - roundsSince,
 * sessionsSince und currentSeq. Ein Push, der dazwischen commitete, zaehlte fuer
 * den Cursor, aber fuer keine der beiden Listen: der Client speicherte einen
 * Cursor jenseits einer Runde, die er nie gesehen hatte, und sie war fuer dieses
 * Geraet dauerhaft weg. Ohne Fehler, ohne Log, ohne Wiederholung.
 */
class PullCursorRaceTest {

    /**
     * Ein Repository, das genau einmal NACH dem Lesen des Fensters einen fremden
     * Schreiber dazwischenschiebt - so, wie eine zweite Transaktion unmittelbar
     * nach dem Snapshot der Route commiten wuerde.
     */
    private class RacingRepository(
        private val delegate: InMemoryRepository,
        private val afterSnapshot: () -> Unit,
    ) : RotaskatRepository by delegate {

        private var fired = false

        override fun pullSince(clubId: String, cursor: Long, limit: Int): PullPage {
            val page = delegate.pullSince(clubId, cursor, limit)
            if (!fired) {
                fired = true
                afterSnapshot()
            }
            return page
        }
    }

    @Test
    fun `ein Push unmittelbar nach dem Snapshot bleibt fuer den naechsten Pull erreichbar`() = testApplication {
        val inner = testRepository()
        lateinit var apply: () -> Unit
        val repository = RacingRepository(inner) { apply() }

        // Der fremde Schreiber geht am HTTP-Weg vorbei direkt ins Repository -
        // das entspricht einer zweiten Transaktion, die commitet, waehrend die
        // Route ihre Antwort zusammenbaut.
        apply = {
            inner.applySync(CLUB_ID, emptyList(), listOf(roundWriteOf(ROUND_B, sequence = 1)))
        }

        application { module(repository) }
        val client = jsonClient()

        val push: SyncPushResponse = client.post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    sessions = listOf(sessionEnvelope()),
                    rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
                )
            )
        }.body()

        // Erster Pull nach dem eigenen Push: nichts Neues. Der fremde Push
        // landet unmittelbar danach.
        val first: SyncPullResponse = client.get("/sync/rounds?since=${push.cursor}") { bearerAuth(TOKEN) }.body()
        assertEquals(emptyList(), first.rounds.map { it.round.id })
        assertEquals(
            push.cursor,
            first.cursor,
            "Der Cursor kommt aus den ausgelieferten Zeilen und nicht aus einer spaeteren Abfrage",
        )

        // Der Client merkt sich diesen Cursor - und bekommt die fremde Runde
        // beim naechsten Mal.
        val second: SyncPullResponse = client.get("/sync/rounds?since=${first.cursor}") { bearerAuth(TOKEN) }.body()
        assertEquals(listOf(ROUND_B), second.rounds.map { it.round.id })
        assertTrue(second.cursor > first.cursor)
    }

    /**
     * Dieselbe Zusicherung ohne HTTP: der Cursor bleibt immer innerhalb dessen,
     * was tatsaechlich in der Antwort steht.
     */
    @Test
    fun `der Cursor geht nie ueber die ausgelieferten Zeilen hinaus`() {
        val repository = testRepository()
        repository.applySync(
            CLUB_ID,
            listOf(sessionWriteOf()),
            listOf(roundWriteOf(ROUND_A, sequence = 0), roundWriteOf(ROUND_B, sequence = 1)),
        )

        for (limit in 1..4) {
            val page = repository.pullSince(CLUB_ID, 0, limit)
            val hoechste = (page.rounds.map { it.updatedSeq } + page.sessions.map { it.updatedSeq })
                .maxOrNull() ?: 0L
            assertEquals(hoechste, page.cursor, "limit=$limit")
        }
    }

    /** Eine leere Seite laesst den Cursor stehen, statt ihn nach vorn zu raten. */
    @Test
    fun `ohne Zeilen bleibt der Cursor, wo er war`() {
        val page = pullPageOf(rounds = emptyList(), sessions = emptyList(), since = 17, limit = 200)
        assertEquals(17, page.cursor)
        assertEquals(false, page.hasMore)
    }

    private fun sessionWriteOf() = io.rotaskat.server.repo.SessionWrite(
        session = testSession(),
        revision = 1,
        contentHash = "hash-session",
        deletedAt = null,
    )

    private fun roundWriteOf(id: String, sequence: Int): RoundWrite {
        val round = testRound(id, declarerSeat = 1)
        return RoundWrite(
            round = round,
            sessionId = SESSION_ID,
            sequence = sequence,
            revision = 1,
            contentHash = "hash-$id",
            deletedAt = null,
            halfPoints = mapOf(0 to -36, 1 to 72, 2 to -36),
            gameValue = 36,
            clientHalfPoints = emptyMap(),
            scoreMismatch = false,
        )
    }

    private companion object {
        const val ROUND_A = "44444444-0000-0000-0000-00000000000a"
        const val ROUND_B = "44444444-0000-0000-0000-00000000000b"
    }
}
