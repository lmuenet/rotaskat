package io.rotaskat.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import io.rotaskat.shared.api.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `sequence` ist Anzeigereihenfolge, keine Zusicherung.
 *
 * V1 legte darauf ein UNIQUE (session_id, sequence). Die Position vergibt aber
 * der Client aus rein lokalem Wissen: hat ein uebernommenes Geraet die Historie
 * nur teilweise gezogen, vergibt es eine bereits belegte Nummer. Der Push lief
 * dann in einen unique_violation, der Batch ist atomar, die Antwort war ein
 * 500er - und weil dieselbe Runde bei jedem Lauf wieder anstand, blieb der Sync
 * dieses Geraets dauerhaft stehen. V3 laesst die Zusicherung fallen.
 */
class SequenceUniquenessTest {

    @Test
    fun `V3 nimmt die Eindeutigkeit der Position zurueck`() {
        val v1 = migration("V1__init.sql")
        val v3 = migration("V3__sequence_and_scores.sql")

        // V1 bleibt unangetastet - sie ist angewendet, Flyway prueft die Summe.
        assertTrue(v1.contains("UNIQUE (session_id, sequence)"))
        assertTrue(
            v3.contains("ALTER TABLE round DROP CONSTRAINT IF EXISTS round_session_id_sequence_key"),
            "V3 muss die Zusicherung fallen lassen",
        )
        assertTrue(
            v3.contains("CREATE INDEX IF NOT EXISTS round_session_sequence_idx"),
            "Der Index traegt weiterhin die Rundenliste eines Abends",
        )
    }

    @Test
    fun `zwei Runden mit derselben Position sind kein Fehlerfall mehr`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    sessions = listOf(sessionEnvelope()),
                    rounds = listOf(
                        envelopeOf(testRound(ROUND_A), sequence = 0),
                        // Andere Id, gleiche Position - genau das, was ein Geraet
                        // nach einem abgebrochenen Delta-Pull erzeugt. Beide
                        // Runden zaehlen, sortiert wird ueber (sequence, id).
                        envelopeOf(testRound(ROUND_B, declarerSeat = 1), sequence = 0),
                    ),
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body: SyncPushResponse = response.body()
        assertEquals(listOf(SyncResult.ACCEPTED, SyncResult.ACCEPTED), body.rounds.map { it.result })
    }

    private fun migration(name: String): String =
        checkNotNull(javaClass.getResource("/db/migration/$name")) { "Migration $name fehlt" }.readText()

    private companion object {
        const val ROUND_A = "55555555-0000-0000-0000-00000000000a"
        const val ROUND_B = "55555555-0000-0000-0000-00000000000b"
    }
}
