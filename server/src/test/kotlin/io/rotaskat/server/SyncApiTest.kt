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
import io.rotaskat.shared.api.SessionDetailResponse
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import io.rotaskat.shared.api.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncApiTest {

    /**
     * Der Sync laeuft in einer Kneipe mit schlechtem Empfang: ein Batch wird
     * regelmaessig zweimal abgeschickt, weil die erste Antwort nie ankam.
     * Der zweite Durchlauf darf nichts aendern - weder Daten noch Cursor.
     */
    @Test
    fun `derselbe Batch zweimal geschickt aendert nichts`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val request = SyncPushRequest(
            sessions = listOf(sessionEnvelope()),
            rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
        )

        val first: SyncPushResponse = client.push(request).body()
        assertEquals(listOf(SyncResult.ACCEPTED), first.rounds.map { it.result })
        assertEquals(listOf(SyncResult.ACCEPTED), first.sessions.map { it.result })

        val second: SyncPushResponse = client.push(request).body()
        assertEquals(listOf(SyncResult.UNCHANGED), second.rounds.map { it.result })
        assertEquals(listOf(SyncResult.UNCHANGED), second.sessions.map { it.result })
        assertEquals(first.cursor, second.cursor, "Ein wiederholter Batch darf den Cursor nicht bewegen")

        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(1, detail.rounds.size)
        assertEquals(first.rounds.single().updatedSeq, detail.rounds.single().updatedSeq)
    }

    /**
     * Gleiche Revision, anderer Inhalt: zwei Geraete haben unabhaengig
     * dieselbe Revisionsnummer vergeben. Still ueberschreiben wuerde eine
     * Korrektur verschlucken, deshalb 409 - und der Batch bleibt komplett
     * ungeschrieben.
     */
    @Test
    fun `gleiche Revision mit anderem Inhalt ist ein Konflikt`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(envelopeOf(testRound(ROUND_A, won = true), sequence = 0)),
            )
        )

        val response = client.push(
            SyncPushRequest(rounds = listOf(envelopeOf(testRound(ROUND_A, won = false), sequence = 0)))
        )
        assertEquals(HttpStatusCode.Conflict, response.status)

        val body: SyncPushResponse = response.body()
        assertEquals(listOf(ROUND_A), body.conflicts.map { it.id })
        assertEquals("round", body.conflicts.single().kind)
        assertTrue(body.conflicts.single().serverContentHash != body.conflicts.single().clientContentHash)

        // Nichts geschrieben: der gespeicherte Stand ist unveraendert gewonnen.
        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(true, detail.rounds.single().round.won)
        assertEquals(mapOf(0 to 72, 1 to -36, 2 to -36), detail.rounds.single().halfPoints)
    }

    /** Eine hoehere Revision ist eine Korrektur und darf ueberschreiben. */
    @Test
    fun `hoehere Revision ueberschreibt, niedrigere ist veraltet`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(envelopeOf(testRound(ROUND_A, won = true), sequence = 0)),
            )
        )

        val corrected: SyncPushResponse = client.push(
            SyncPushRequest(rounds = listOf(envelopeOf(testRound(ROUND_A, won = false), sequence = 0, revision = 2)))
        ).body()
        assertEquals(SyncResult.ACCEPTED, corrected.rounds.single().result)

        val stale: SyncPushResponse = client.push(
            SyncPushRequest(rounds = listOf(envelopeOf(testRound(ROUND_A, won = true), sequence = 0, revision = 1)))
        ).body()
        assertEquals(SyncResult.STALE, stale.rounds.single().result)

        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(false, detail.rounds.single().round.won)
        // Verloren: Alleinspieler -4V, Gegenspieler je +2V, in halben Punkten.
        assertEquals(mapOf(0 to -144, 1 to 72, 2 to 72), detail.rounds.single().halfPoints)
    }

    /**
     * Der Fruehwarner fuer Versionsdrift: eine alte APK rechnet anders, der
     * Server rechnet nach und speichert SEINEN Wert.
     */
    @Test
    fun `Nachrechnung korrigiert falsche Clientwerte`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response: SyncPushResponse = client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(
                    envelopeOf(
                        testRound(ROUND_A),
                        sequence = 0,
                        // Der Client haelt einen gewonnenen Kreuz mit 2 fuer
                        // 36 statt 72 halbe Punkte.
                        clientHalfPoints = mapOf(0 to 36, 1 to -18, 2 to -18),
                    )
                ),
            )
        ).body()

        assertEquals(3, response.mismatches.size)
        assertEquals(setOf(ROUND_A), response.mismatches.map { it.roundId }.toSet())
        val declarer = response.mismatches.single { it.seat == 0 }
        assertEquals(36, declarer.clientHalfPoints)
        assertEquals(72, declarer.serverHalfPoints)

        // Trotz Abweichung wird die Runde gespeichert, mit dem Serverwert.
        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(mapOf(0 to 72, 1 to -36, 2 to -36), detail.rounds.single().halfPoints)
    }

    /** Stimmt die Pruefsumme, taucht sie in der Antwort gar nicht auf. */
    @Test
    fun `passende Clientwerte melden keine Abweichung`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response: SyncPushResponse = client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(
                    envelopeOf(
                        testRound(ROUND_A),
                        sequence = 0,
                        clientHalfPoints = mapOf(0 to 72, 1 to -36, 2 to -36),
                    )
                ),
            )
        ).body()

        assertEquals(emptyList(), response.mismatches)
    }

    /**
     * Delta-Pull: ab dem Cursor genau die Aenderungen, nicht mehr und nicht
     * weniger. Der Cursor ist ein serverseitiger Zaehler - Handyuhren laufen
     * auseinander und wuerden hier Runden ueberspringen.
     */
    @Test
    fun `Delta-Pull liefert genau die Aenderungen ab dem Cursor`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val first: SyncPushResponse = client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
            )
        ).body()

        val full: SyncPullResponse = client.get("/sync/rounds") { bearerAuth(TOKEN) }.body()
        assertEquals(listOf(ROUND_A), full.rounds.map { it.round.id })
        assertEquals(listOf(SESSION_ID), full.sessions.map { it.session.id })
        assertEquals(first.cursor, full.cursor)

        client.push(SyncPushRequest(rounds = listOf(envelopeOf(testRound(ROUND_B, declarerSeat = 1), sequence = 1))))

        val delta: SyncPullResponse = client.get("/sync/rounds?since=${full.cursor}") { bearerAuth(TOKEN) }.body()
        assertEquals(listOf(ROUND_B), delta.rounds.map { it.round.id })
        assertEquals(emptyList(), delta.sessions.map { it.session.id }, "Die Session hat sich nicht geaendert")
        assertTrue(delta.cursor > full.cursor)
        assertEquals(false, delta.hasMore)

        // Am Ende angekommen: derselbe Cursor liefert nichts mehr nach.
        val empty: SyncPullResponse = client.get("/sync/rounds?since=${delta.cursor}") { bearerAuth(TOKEN) }.body()
        assertEquals(emptyList(), empty.rounds)
        assertEquals(delta.cursor, empty.cursor)
    }

    /**
     * Mit Seitengrenze wandert der Cursor nur so weit, wie BEIDE Listen
     * vollstaendig ausgeliefert wurden - sonst faellt genau das der
     * Seitengrenze zum Opfer, was der naechste Pull dann ueberspringt. Der
     * Preis dafuer ist eine gelegentlich leere Seite; entscheidend ist, dass
     * der Cursor trotzdem vorankommt und der Client nicht im Kreis laeuft.
     */
    @Test
    fun `Delta-Pull mit Seitengrenze kommt vollstaendig ans Ende`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(
                    envelopeOf(testRound(ROUND_A), sequence = 0),
                    envelopeOf(testRound(ROUND_B, declarerSeat = 1), sequence = 1),
                ),
            )
        )

        val seen = mutableListOf<String>()
        var cursor = 0L
        var pages = 0
        while (true) {
            val page: SyncPullResponse = client.get("/sync/rounds?limit=1&since=$cursor") { bearerAuth(TOKEN) }.body()
            seen += page.rounds.map { it.round.id }
            if (!page.hasMore) break
            assertTrue(page.cursor > cursor, "Der Cursor muss bei jeder Seite vorankommen")
            cursor = page.cursor
            if (++pages > 10) error("Der Delta-Pull kommt nicht ans Ende")
        }

        assertEquals(listOf(ROUND_A, ROUND_B), seen, "Jede Runde genau einmal, in Reihenfolge des Zaehlers")
    }

    /** Eine Runde ohne bekannte Session ist ein Eingabefehler, kein 500er. */
    @Test
    fun `Runde ohne Session wird abgelehnt`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.push(
            SyncPushRequest(rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)))
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    /** Loeschen ist ein Tombstone: die Runde bleibt, faellt aber aus der Abrechnung. */
    @Test
    fun `Tombstone nimmt die Runde aus der Abrechnung`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.push(
            SyncPushRequest(
                sessions = listOf(sessionEnvelope()),
                rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
            )
        )
        client.push(
            SyncPushRequest(
                rounds = listOf(
                    envelopeOf(testRound(ROUND_A), sequence = 0, revision = 2)
                        .copy(deletedAt = kotlinx.datetime.Instant.parse("2026-03-14T21:00:00Z"))
                )
            )
        )

        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(1, detail.rounds.size, "Der Tombstone bleibt sichtbar, damit der Sync ihn weitertragen kann")
        assertEquals(mapOf(0 to 0L, 1 to 0L, 2 to 0L), detail.totals)
    }

    private suspend fun io.ktor.client.HttpClient.push(request: SyncPushRequest) =
        post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private companion object {
        const val ROUND_A = "33333333-0000-0000-0000-00000000000a"
        const val ROUND_B = "33333333-0000-0000-0000-00000000000b"
    }
}
