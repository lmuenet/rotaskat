package io.rotaskat.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionDetailResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import io.rotaskat.shared.api.SyncResult
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.scoring.ScoringConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die eingefrorene ScoringConfig gegen eine mitgeschickte, aber verworfene
 * Session-Nutzlast.
 *
 * `resolveConfig` bevorzugte die Session AUS DEM BATCH bedingungslos vor dem
 * gespeicherten Stand. Ob sie ueberhaupt geschrieben wird, entscheidet sich
 * aber erst in `applySync` - die Nachrechnung war da laengst gelaufen. Ein
 * Geraet konnte damit die Hausregeln ueber eine Nutzlast diktieren, die der
 * Server selbst als veraltet einstufte, und hebelte gleich beide Zusagen aus:
 * die pro Session eingefrorene Konfiguration und das Nachrechnen statt
 * Vertrauen.
 */
class FrozenConfigBypassTest {

    @Test
    fun `eine als veraltet verworfene Session bestimmt die Nachrechnung nicht`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        // Der Abend ist mit "Jungfrau verdoppelt" eingefroren, Revision 2.
        val frozen = testSession().copy(scoring = ScoringConfig(jungfrauDoubles = true))
        val first: SyncPushResponse = client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope(frozen, revision = 2)),
                rounds = listOf(ramschEnvelope(ROUND_A, sequence = 0)),
            )
        ).body()
        assertEquals(listOf(SyncResult.ACCEPTED), first.sessions.map { it.result })

        // Jungfrau bei 40 Augen, verdoppelt auf 80: Verlierer -160 halbe Punkte.
        val before: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(mapOf(0 to -160, 1 to 80, 2 to 80), before.rounds.single().halfPoints)

        // Jetzt kommt eine VERALTETE Session-Nutzlast (Revision 1) mit
        // abweichenden Hausregeln, zusammen mit einer neuen Runde.
        val second: SyncPushResponse = client.push(
            SyncPushRequest(
                sessions = listOf(
                    sessionEnvelope(
                        testSession().copy(scoring = ScoringConfig(jungfrauDoubles = false)),
                        revision = 1,
                    )
                ),
                rounds = listOf(ramschEnvelope(ROUND_B, sequence = 1)),
            )
        ).body()

        assertEquals(
            listOf(SyncResult.STALE),
            second.sessions.map { it.result },
            "Die Session-Nutzlast wurde als veraltet verworfen",
        )
        assertEquals(listOf(SyncResult.ACCEPTED), second.rounds.map { it.result })

        val after: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(
            ScoringConfig(jungfrauDoubles = true),
            after.session.scoring,
            "Der gespeicherte Abend fuehrt weiterhin die eingefrorenen Regeln",
        )

        val neu = after.rounds.single { it.round.id == ROUND_B }
        assertEquals(
            mapOf(0 to -160, 1 to 80, 2 to 80),
            neu.halfPoints,
            "Die neue Runde rechnet mit den fuer den Abend eingefrorenen Regeln, " +
                "nicht mit denen aus der verworfenen Nutzlast",
        )
    }

    /**
     * Der Batch-Weg bleibt: ein Abend wird zusammen mit seinen ersten Runden
     * hochgeladen und steht dann noch gar nicht in der Datenbank.
     */
    @Test
    fun `eine neue Session aus dem Batch traegt ihre Regeln selbst`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val ohneJungfrau = testSession().copy(scoring = ScoringConfig(jungfrauDoubles = false))
        client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope(ohneJungfrau)),
                rounds = listOf(ramschEnvelope(ROUND_A, sequence = 0)),
            )
        )

        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(mapOf(0 to -80, 1 to 40, 2 to 40), detail.rounds.single().halfPoints)
    }

    private fun ramschEnvelope(id: String, sequence: Int): RoundEnvelope = RoundEnvelope(
        round = Round(
            id = id,
            seatCount = 3,
            declarerSeat = null,
            sittingOutSeat = null,
            declaration = RamschGame,
            ramsch = RamschResult(loserSeat = 0, cardPoints = 40, jungfrau = true),
        ),
        sessionId = SESSION_ID,
        sequence = sequence,
        revision = 1,
    )

    private suspend fun io.ktor.client.HttpClient.push(request: SyncPushRequest) =
        post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.also { check(it.status == HttpStatusCode.OK) { "Push scheiterte: ${it.status}" } }

    private companion object {
        const val ROUND_A = "66666666-0000-0000-0000-00000000000a"
        const val ROUND_B = "66666666-0000-0000-0000-00000000000b"
    }
}
