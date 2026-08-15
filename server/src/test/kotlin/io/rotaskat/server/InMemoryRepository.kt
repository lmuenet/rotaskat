package io.rotaskat.server

import io.rotaskat.server.repo.DeviceIdentity
import io.rotaskat.server.repo.PullPage
import io.rotaskat.server.repo.pullPageOf
import io.rotaskat.server.repo.RotaskatRepository
import io.rotaskat.server.repo.RoundWrite
import io.rotaskat.server.repo.RowVersion
import io.rotaskat.server.repo.decideSync
import io.rotaskat.server.repo.SessionWrite
import io.rotaskat.server.repo.StoredRound
import io.rotaskat.server.repo.StoredSession
import io.rotaskat.server.repo.SyncDecision
import io.rotaskat.server.repo.SyncOutcome
import io.rotaskat.shared.api.LeaderboardEntry
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncResult
import io.rotaskat.shared.model.Club
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/**
 * Die Testvariante des Repositorys.
 *
 * Kein Testcontainers und kein H2: das Schema lebt von jsonb, einem
 * plpgsql-Trigger und FILTER-Aggregaten, das bildet keine eingebettete
 * Datenbank nach, und Docker gibt es im Agentenkontext nicht. Was hier
 * getestet wird, ist die ENDPUNKTLOGIK - Idempotenz, Revisionskonflikt,
 * Nachrechnung, Delta-Pull. Die SQL-Variante haelt sich an dasselbe Interface
 * und dieselben Entscheidungsregeln.
 *
 * Der Sequenzzaehler ist absichtlich global und monoton, genau wie die
 * Postgres-Sequenz aus V2.
 */
class InMemoryRepository(private val clubs: List<Club>) : RotaskatRepository {

    private val inviteCodes = mutableMapOf<String, String>()
    private val devices = mutableMapOf<String, DeviceIdentity>()
    private val tokenHashes = mutableMapOf<String, String>()
    private val lastSeen = mutableMapOf<String, Instant>()

    private val sessions = linkedMapOf<String, StoredSession>()
    private val rounds = linkedMapOf<String, StoredRound>()
    private var seq = 0L

    fun withInviteCode(code: String, clubId: String): InMemoryRepository = apply {
        inviteCodes[code] = clubId
    }

    /** Stellt ein Token aus, ohne den Beitrittsendpunkt zu bemuehen. */
    fun issueToken(clubId: String, playerId: String, tokenHash: String): String =
        registerDevice(clubId, playerId, tokenHash, "test")

    fun lastSeenOf(deviceId: String): Instant? = lastSeen[deviceId]

    override fun findClubByInviteCode(inviteCode: String): Club? =
        inviteCodes[inviteCode]?.let { findClub(it) }

    override fun findClub(clubId: String): Club? = clubs.firstOrNull { it.id == clubId }

    override fun clubPlayerIds(clubId: String): Set<String> =
        findClub(clubId)?.roster?.map { it.id }?.toSet() ?: emptySet()

    override fun registerDevice(clubId: String, playerId: String, tokenHash: String, label: String?): String {
        val deviceId = UUID.randomUUID().toString()
        devices[deviceId] = DeviceIdentity(deviceId, playerId, clubId)
        tokenHashes[tokenHash] = deviceId
        return deviceId
    }

    override fun findDeviceByTokenHash(tokenHash: String): DeviceIdentity? =
        tokenHashes[tokenHash]?.let { devices[it] }

    override fun touchDevice(deviceId: String, at: Instant) {
        lastSeen[deviceId] = at
    }

    override fun findSession(clubId: String, sessionId: String): StoredSession? =
        sessions[sessionId]?.takeIf { it.session.clubId == clubId }

    override fun findSessions(clubId: String, sessionIds: Collection<String>): Map<String, StoredSession> =
        sessionIds.mapNotNull { findSession(clubId, it) }.associateBy { it.session.id }

    override fun roundsOfSession(clubId: String, sessionId: String): List<StoredRound> =
        if (findSession(clubId, sessionId) == null) emptyList()
        else rounds.values.filter { it.sessionId == sessionId }.sortedBy { it.sequence }

    /**
     * Dasselbe Fenster wie die SQL-Variante, aus demselben Snapshot: hier ist
     * er der Aufruf selbst, dort die Transaktion. Zugeschnitten wird mit
     * [pullPageOf] - die Regel steht einmal und wird nicht nachgebaut.
     */
    override fun pullSince(clubId: String, cursor: Long, limit: Int): PullPage = pullPageOf(
        rounds = rounds.values
            .filter { sessions[it.sessionId]?.session?.clubId == clubId && it.updatedSeq > cursor }
            .sortedBy { it.updatedSeq }
            .take(limit),
        sessions = sessions.values
            .filter { it.session.clubId == clubId && it.updatedSeq > cursor }
            .sortedBy { it.updatedSeq }
            .take(limit),
        since = cursor,
        limit = limit,
    )

    fun currentSeq(clubId: String): Long {
        val fromSessions = sessions.values.filter { it.session.clubId == clubId }.maxOfOrNull { it.updatedSeq } ?: 0L
        val fromRounds = rounds.values
            .filter { sessions[it.sessionId]?.session?.clubId == clubId }
            .maxOfOrNull { it.updatedSeq } ?: 0L
        return maxOf(fromSessions, fromRounds)
    }

    override fun leaderboard(clubId: String, season: Int?): List<LeaderboardEntry> {
        val club = findClub(clubId) ?: return emptyList()
        val totals = club.roster.associate { it.id to Accumulator(it.id, it.displayName) }

        for (stored in sessions.values) {
            if (stored.session.clubId != clubId || stored.deletedAt != null) continue
            // Eine Saison ist ein Kalenderjahr, geschnitten am Beginn des
            // Abends: ein Abend, der nach Mitternacht weiterlaeuft, gehoert
            // vollstaendig in sein Anfangsjahr.
            val year = stored.session.startedAt.toLocalDateTime(TimeZone.UTC).year
            if (season != null && season != year) continue

            val playedSession = mutableSetOf<String>()
            for (round in rounds.values) {
                if (round.sessionId != stored.session.id || round.deletedAt != null) continue
                for ((seat, halfPoints) in round.halfPoints) {
                    val playerId = stored.session.seats[seat] ?: continue
                    val accumulator = totals[playerId] ?: continue
                    accumulator.halfPoints += halfPoints
                    // Der Aussetzende bekommt eine Zeile mit 0. Sie darf nicht
                    // als gespielte Runde zaehlen, sonst liegt jede Quote am
                    // Vierertisch um ein Viertel daneben.
                    if (seat == round.round.sittingOutSeat) continue
                    accumulator.rounds += 1
                    if (round.round.declarerSeat == seat) {
                        accumulator.declared += 1
                        // Ueberreizt gilt immer als verloren, auch mit Stichen -
                        // dieselbe Regel wie in Scoring.score.
                        if (round.round.won && !round.round.overbid) accumulator.declaredWon += 1
                    }
                    playedSession += playerId
                }
            }
            playedSession.forEach { totals[it]?.sessions?.plusAssign(stored.session.id) }
        }

        return totals.values
            .map {
                LeaderboardEntry(
                    playerId = it.playerId,
                    displayName = it.displayName,
                    roundsPlayed = it.rounds,
                    sessionsPlayed = it.sessions.size,
                    totalHalfPoints = it.halfPoints,
                    gamesDeclared = it.declared,
                    gamesDeclaredWon = it.declaredWon,
                )
            }
            .sortedWith(compareByDescending<LeaderboardEntry> { it.totalHalfPoints }.thenBy { it.displayName })
    }

    override fun applySync(clubId: String, sessions: List<SessionWrite>, rounds: List<RoundWrite>): SyncOutcome {
        // Dieselbe Entscheidungsregel wie die SQL-Variante, nicht eine Kopie
        // davon: sonst pruefte dieser Test seine eigene Zweitschrift.
        val conflicts = mutableListOf<SyncConflict>()
        val sessionDecisions = sessions.map {
            decideSync(it.session.id, "session", it.revision, it.contentHash, versionOf(this.sessions[it.session.id]), conflicts)
        }
        val roundDecisions = rounds.map {
            decideSync(it.round.id, "round", it.revision, it.contentHash, versionOf(this.rounds[it.round.id]), conflicts)
        }

        // Atomar: bei Konflikt wird nichts geschrieben.
        if (conflicts.isNotEmpty()) {
            return SyncOutcome(conflicts = conflicts, cursor = currentSeq(clubId))
        }

        val sessionResults = sessions.mapIndexed { index, write ->
            when (sessionDecisions[index]) {
                SyncResult.ACCEPTED -> {
                    val updatedSeq = ++seq
                    this.sessions[write.session.id] = StoredSession(
                        session = write.session,
                        revision = write.revision,
                        contentHash = write.contentHash,
                        deletedAt = write.deletedAt,
                        updatedSeq = updatedSeq,
                    )
                    SyncDecision(write.session.id, SyncResult.ACCEPTED, updatedSeq)
                }

                else -> SyncDecision(
                    write.session.id,
                    sessionDecisions[index],
                    this.sessions[write.session.id]?.updatedSeq ?: 0L,
                )
            }
        }

        val roundResults = rounds.mapIndexed { index, write ->
            when (roundDecisions[index]) {
                SyncResult.ACCEPTED -> {
                    val updatedSeq = ++seq
                    this.rounds[write.round.id] = StoredRound(
                        round = write.round,
                        sessionId = write.sessionId,
                        sequence = write.sequence,
                        revision = write.revision,
                        contentHash = write.contentHash,
                        deletedAt = write.deletedAt,
                        updatedSeq = updatedSeq,
                        halfPoints = write.halfPoints,
                        gameValue = write.gameValue,
                    )
                    SyncDecision(write.round.id, SyncResult.ACCEPTED, updatedSeq)
                }

                else -> SyncDecision(
                    write.round.id,
                    roundDecisions[index],
                    this.rounds[write.round.id]?.updatedSeq ?: 0L,
                )
            }
        }

        return SyncOutcome(
            sessions = sessionResults,
            rounds = roundResults,
            cursor = currentSeq(clubId),
        )
    }

    private fun versionOf(stored: StoredSession?): RowVersion? =
        stored?.let { RowVersion(it.revision, it.contentHash, it.updatedSeq, it.deletedAt != null) }

    private fun versionOf(stored: StoredRound?): RowVersion? =
        stored?.let { RowVersion(it.revision, it.contentHash, it.updatedSeq, it.deletedAt != null) }

    private class Accumulator(val playerId: String, val displayName: String) {
        var halfPoints = 0L
        var rounds = 0
        var declared = 0
        var declaredWon = 0
        val sessions = mutableSetOf<String>()
    }
}
