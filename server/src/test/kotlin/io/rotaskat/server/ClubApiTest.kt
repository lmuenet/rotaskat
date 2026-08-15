package io.rotaskat.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.rotaskat.shared.api.ClubLookupRequest
import io.rotaskat.shared.api.ClubLookupResponse
import io.rotaskat.shared.api.JoinClubRequest
import io.rotaskat.shared.api.JoinClubResponse
import io.rotaskat.shared.api.LeaderboardResponse
import io.rotaskat.shared.api.SessionDetailResponse
import io.rotaskat.shared.api.SyncPushRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClubApiTest {

    /**
     * Ohne diesen Schritt gaebe es keinen Beitritt: der Beitretende muss eine
     * Spieler-Id nennen, und die kann er nur waehlen, wenn er den Kader vorher
     * sieht.
     */
    @Test
    fun `Nachschlagen liefert den Kader ohne Token`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.post("/clubs/lookup") {
            contentType(ContentType.Application.Json)
            setBody(ClubLookupRequest(inviteCode = INVITE_CODE))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body: ClubLookupResponse = response.body()
        assertEquals(CLUB_ID, body.club.id)
        assertEquals(3, body.club.roster.size)
        assertTrue(body.club.roster.all { it.active })

        // Der Kader allein macht das Geraet zu keinem Mitglied: ohne Token
        // bleiben die geschuetzten Endpunkte zu.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/leaderboard").status)
    }

    @Test
    fun `Nachschlagen mit falschem Code verraet nichts`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.post("/clubs/lookup") {
            contentType(ContentType.Application.Json)
            setBody(ClubLookupRequest(inviteCode = "ausgedacht"))
        }
        // Dieselbe Antwort wie ein fehlgeschlagener Beitritt. Ein eigener
        // Fehlertext waere ein Orakel, mit dem sich Einladungscodes
        // durchprobieren liessen.
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(CLUB_ID !in response.bodyAsText())
    }

    @Test
    fun `Einladungscode wird gegen ein Geraetetoken getauscht`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.post("/clubs/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinClubRequest(inviteCode = INVITE_CODE, playerId = ANNA, deviceLabel = "Annas Handy"))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body: JoinClubResponse = response.body()
        assertTrue(body.token.isNotBlank())
        assertEquals(ANNA, body.playerId)
        assertEquals(CLUB_ID, body.club.id)
        assertEquals(3, body.club.roster.size)

        // Das frisch ausgestellte Token oeffnet die geschuetzten Endpunkte.
        val leaderboard = client.get("/leaderboard") { bearerAuth(body.token) }
        assertEquals(HttpStatusCode.OK, leaderboard.status)
    }

    /**
     * Unbekannter Code und unbekannter Spieler liefern dieselbe Meldung: sonst
     * liesse sich ueber die Fehlermeldung durchprobieren, welche
     * Einladungscodes existieren.
     */
    @Test
    fun `falscher Code und fremder Spieler sind ununterscheidbar`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val wrongCode = client.post("/clubs/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinClubRequest(inviteCode = "GIBTSNICHT", playerId = ANNA))
        }
        val wrongPlayer = client.post("/clubs/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinClubRequest(inviteCode = INVITE_CODE, playerId = "99999999-9999-9999-9999-999999999999"))
        }

        assertEquals(HttpStatusCode.Forbidden, wrongCode.status)
        assertEquals(HttpStatusCode.Forbidden, wrongPlayer.status)
        assertEquals(wrongCode.bodyAsText(), wrongPlayer.bodyAsText())
    }

    @Test
    fun `geschuetzte Endpunkte brauchen ein gueltiges Token`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/sync/rounds").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/leaderboard") { bearerAuth("falsch") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/sessions/$SESSION_ID").status)

        // /health bleibt offen, sonst kann kein Betreiber danach schauen.
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    @Test
    fun `Rangliste gibt es all-time und pro Kalenderjahr`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    sessions = listOf(sessionEnvelope()),
                    rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
                )
            )
        }

        val allTime: LeaderboardResponse = client.get("/leaderboard") { bearerAuth(TOKEN) }.body()
        assertEquals(null, allTime.season)
        assertEquals(listOf("Anna", "Bernd", "Clara"), allTime.entries.map { it.displayName })
        assertEquals(72L, allTime.entries.first().totalHalfPoints)
        assertEquals(36.0, allTime.entries.first().totalPoints)
        assertEquals(1, allTime.entries.first().gamesDeclaredWon)

        val season: LeaderboardResponse = client.get("/leaderboard?season=2026") { bearerAuth(TOKEN) }.body()
        assertEquals(2026, season.season)
        assertEquals(72L, season.entries.first { it.displayName == "Anna" }.totalHalfPoints)

        // Am 1. Januar faengt die Saisontabelle bei null an, all-time laeuft weiter.
        val other: LeaderboardResponse = client.get("/leaderboard?season=2025") { bearerAuth(TOKEN) }.body()
        assertTrue(other.entries.all { it.totalHalfPoints == 0L })
        assertEquals(3, other.entries.size)
    }

    /**
     * Ueberreizt gilt immer als verloren, auch mit Stichen. Die Oberflaeche
     * laesst "Gewonnen" dort bewusst zu und zeigt trotzdem einen negativen Wert
     * an - die Rangliste muss dieselbe Regel kennen, sonst zaehlt der Server
     * einen Sieg, den die App nicht zaehlt.
     */
    @Test
    fun `ein ueberreiztes Spiel zaehlt nicht als Sieg des Alleinspielers`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val ueberreizt = testRound(ROUND_A, matadors = 1).copy(won = true, overbid = true, bid = 36)
        client.post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    sessions = listOf(sessionEnvelope()),
                    rounds = listOf(envelopeOf(ueberreizt, sequence = 0)),
                )
            )
        }

        val board: LeaderboardResponse = client.get("/leaderboard") { bearerAuth(TOKEN) }.body()
        val anna = board.entries.single { it.displayName == "Anna" }
        assertEquals(1, anna.gamesDeclared)
        assertEquals(0, anna.gamesDeclaredWon, "Ueberreizt ist ein Verlust, auch mit 61 Augen")
        // Kreuz mit 1 ist 24 wert, gereizt wurde 36, abgerechnet werden 36:
        // der Alleinspieler zahlt -4V in halben Punkten.
        assertEquals(-144L, anna.totalHalfPoints)
    }

    @Test
    fun `Sessiondetail liefert Stand und Geldabrechnung`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        client.post("/sync/rounds") {
            bearerAuth(TOKEN)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequest(
                    sessions = listOf(sessionEnvelope()),
                    rounds = listOf(envelopeOf(testRound(ROUND_A), sequence = 0)),
                )
            )
        }

        val detail: SessionDetailResponse = client.get("/sessions/$SESSION_ID") { bearerAuth(TOKEN) }.body()
        assertEquals(mapOf(0 to 72L, 1 to -36L, 2 to -36L), detail.totals)

        val settlement = assertNotNull(detail.settlement)
        assertEquals(10, settlement.centsPerPoint)
        // 72 halbe Punkte * 10 Cent / 2 = 360 Cent.
        assertEquals(listOf(360L, -180L, -180L), settlement.balances.map { it.cents })
        assertEquals(ANNA, settlement.balances.first().playerId)
        // Minimale Anzahl Zahlungen: bei drei Spielern hoechstens zwei.
        assertEquals(2, settlement.payments.size)
        assertTrue(settlement.payments.all { it.toSeat == 0 && it.cents == 180L })
    }

    @Test
    fun `unbekannte Session liefert 404`() = testApplication {
        val repository = testRepository()
        application { module(repository) }
        val client = jsonClient()

        val response = client.get("/sessions/44444444-4444-4444-4444-444444444444") { bearerAuth(TOKEN) }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private companion object {
        const val ROUND_A = "33333333-0000-0000-0000-0000000000a1"
    }
}
