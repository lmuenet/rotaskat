package io.rotaskat.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.rotaskat.server.ApiException
import io.rotaskat.server.auth.DeviceTokens
import io.rotaskat.server.badRequest
import io.rotaskat.server.notFound
import io.rotaskat.server.repo.DeviceIdentity
import io.rotaskat.server.repo.RotaskatRepository
import io.rotaskat.server.sync.SyncService
import io.rotaskat.shared.api.ClubLookupRequest
import io.rotaskat.shared.api.ClubLookupResponse
import io.rotaskat.shared.api.JoinClubRequest
import io.rotaskat.shared.api.JoinClubResponse
import io.rotaskat.shared.api.LeaderboardResponse
import io.rotaskat.shared.api.SessionDetailResponse
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest

/** Groesse einer Delta-Seite, wenn der Client nichts anderes verlangt. */
private const val DEFAULT_PULL_LIMIT = 200

/** Obergrenze, damit ein Client den Server nicht mit `limit=10000000` belegt. */
private const val MAX_PULL_LIMIT = 1000

/**
 * Unbekannter Einladungscode und unbekannter Spieler enden absichtlich in
 * derselben Meldung. Zwei unterschiedliche Texte waeren ein Orakel, mit dem
 * sich durchprobieren liesse, welche Einladungscodes existieren.
 */
private fun denyJoin(): Nothing =
    throw ApiException(HttpStatusCode.Forbidden, "Einladungscode oder Spieler unbekannt")

private fun ApplicationCall.device(): DeviceIdentity =
    principal<DeviceIdentity>() ?: throw ApiException(HttpStatusCode.Unauthorized, "Kein gueltiges Geraetetoken")

/**
 * Beitritt: Einladungscode gegen Geraetetoken.
 *
 * Bewusst NICHT hinter der Authentifizierung - das waere zirkulaer, hier wird
 * das Token ja erst ausgestellt.
 */
fun Route.clubRoutes(repository: RotaskatRepository) {
    /**
     * Kader zu einem Einladungscode. Der Beitritt braucht eine Spieler-Id, und
     * die kann der Beitretende nur waehlen, wenn er den Kader vorher sieht.
     *
     * Antwortet bei unbekanntem Code mit derselben Meldung wie der Beitritt -
     * zwei unterschiedliche Texte waeren ein Orakel fuer gueltige Codes.
     */
    post("/clubs/lookup") {
        val request = call.receive<ClubLookupRequest>()
        val club = repository.findClubByInviteCode(request.inviteCode.trim()) ?: denyJoin()
        // Nur aktive Mitglieder: ein stillgelegter Spieler soll nicht als
        // waehlbar erscheinen, der Beitritt wuerde ihn ohnehin ablehnen.
        call.respond(ClubLookupResponse(club.copy(roster = club.roster.filter { it.active })))
    }

    post("/clubs/join") {
        val request = call.receive<JoinClubRequest>()

        val club = repository.findClubByInviteCode(request.inviteCode.trim()) ?: denyJoin()
        val player = club.roster.firstOrNull { it.id == request.playerId && it.active } ?: denyJoin()

        val token = DeviceTokens.newToken()
        val deviceId = repository.registerDevice(
            clubId = club.id,
            playerId = player.id,
            tokenHash = DeviceTokens.hash(token),
            label = request.deviceLabel,
        )
        // Das Token steht genau hier ein einziges Mal in einer Antwort. Danach
        // existiert nur noch sein Hash.
        call.respond(JoinClubResponse(token = token, deviceId = deviceId, playerId = player.id, club = club))
    }
}

fun Route.syncRoutes(repository: RotaskatRepository, service: SyncService) {
    post("/sync/rounds") {
        val device = call.device()
        val request = call.receive<SyncPushRequest>()
        when (val outcome = service.push(device.clubId, request)) {
            is SyncService.PushOutcome.Applied -> call.respond(HttpStatusCode.OK, outcome.response)
            // 409 und nichts geschrieben: gleiche Revision mit abweichendem
            // Inhalt kann der Server nicht entscheiden, ohne eine Korrektur zu
            // verschlucken.
            is SyncService.PushOutcome.Conflicted -> call.respond(HttpStatusCode.Conflict, outcome.response)
        }
    }

    get("/sync/rounds") {
        val device = call.device()
        val since = call.longParam("since", default = 0L)
        if (since < 0) badRequest("since darf nicht negativ sein, ist $since")
        val limit = call.intParam("limit", default = DEFAULT_PULL_LIMIT).coerceIn(1, MAX_PULL_LIMIT)

        // Runden, Sessions und Cursor kommen aus EINEM Snapshot. Drei getrennte
        // Abfragen liessen einen Push dazwischen commiten: er zaehlte dann fuer
        // den Cursor, aber nicht fuer die Auslieferung, und war fuer dieses
        // Geraet dauerhaft verloren.
        val page = repository.pullSince(device.clubId, since, limit)

        call.respond(
            SyncPullResponse(
                cursor = page.cursor,
                rounds = page.rounds.map { it.toEnvelope() },
                sessions = page.sessions.map { it.toEnvelope() },
                hasMore = page.hasMore,
            )
        )
    }
}

fun Route.leaderboardRoutes(repository: RotaskatRepository) {
    get("/leaderboard") {
        val device = call.device()
        // Ohne Parameter all-time, mit `season=2026` das Kalenderjahr. Beides
        // gibt es, damit ein frueher Vorsprung die Tabelle nicht auf Jahre
        // einbetoniert.
        val season = call.request.queryParameters["season"]?.let { raw ->
            raw.toIntOrNull() ?: badRequest("season muss ein Kalenderjahr sein, ist '$raw'")
        }
        val entries = repository.leaderboard(device.clubId, season)
        call.respond(LeaderboardResponse(season = season, entries = entries))
    }
}

fun Route.sessionRoutes(repository: RotaskatRepository) {
    get("/sessions/{id}") {
        val device = call.device()
        val id = call.parameters["id"] ?: badRequest("Session-Id fehlt")
        val stored = repository.findSession(device.clubId, id) ?: notFound("Session $id nicht gefunden")

        val rounds = repository.roundsOfSession(device.clubId, id)
        val totals = sessionTotals(stored.session, rounds)
        call.respond(
            SessionDetailResponse(
                session = stored.session,
                rounds = rounds.map { it.toEnvelope() },
                totals = totals,
                settlement = settlementOf(stored.session, totals),
            )
        )
    }
}

private fun ApplicationCall.longParam(name: String, default: Long): Long {
    val raw = request.queryParameters[name] ?: return default
    return raw.toLongOrNull() ?: badRequest("$name muss eine Zahl sein, ist '$raw'")
}

private fun ApplicationCall.intParam(name: String, default: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    return raw.toIntOrNull() ?: badRequest("$name muss eine Zahl sein, ist '$raw'")
}
