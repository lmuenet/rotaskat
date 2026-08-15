package io.rotaskat.server.db

import io.rotaskat.server.badRequest
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
import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncResult
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Die SQL-Umsetzung des Repositorys.
 *
 * Zwei Dinge laufen bewusst als handgeschriebenes SQL statt ueber die
 * Exposed-DSL:
 *
 *  - der bedingte Upsert `ON CONFLICT (id) DO UPDATE ... WHERE
 *    excluded.revision > round.revision`. Er ist die Idempotenz-Zusicherung
 *    des ganzen Sync und soll genau so im Code stehen, wie er in der Datenbank
 *    ankommt.
 *  - die Ranglisten-Aggregation. Sie liest die View `leaderboard_season` aus
 *    V2 und waere als DSL-Ausdruck nur schwerer zu lesen.
 *
 * Alles andere geht ueber die DSL und die Tabellen in [Tables].
 */
class PostgresRepository(private val database: Database) : RotaskatRepository {

    // --- Beitritt und Auth ---

    override fun findClubByInviteCode(inviteCode: String): Club? = transaction(database) {
        Clubs.selectAll().where { Clubs.inviteCode eq inviteCode }
            .singleOrNull()
            ?.let { clubOf(it) }
    }

    override fun findClub(clubId: String): Club? = transaction(database) {
        Clubs.selectAll().where { Clubs.id eq uuidOf(clubId, "clubId") }
            .singleOrNull()
            ?.let { clubOf(it) }
    }

    override fun clubPlayerIds(clubId: String): Set<String> = transaction(database) {
        Players.selectAll().where { Players.clubId eq uuidOf(clubId, "clubId") }
            .map { it[Players.id].toString() }
            .toSet()
    }

    override fun registerDevice(clubId: String, playerId: String, tokenHash: String, label: String?): String =
        transaction(database) {
            val deviceId = UUID.randomUUID()
            Devices.insert {
                it[Devices.id] = deviceId
                it[Devices.playerId] = uuidOf(playerId, "playerId")
                it[Devices.tokenHash] = tokenHash
                it[Devices.label] = label
            }
            deviceId.toString()
        }

    override fun findDeviceByTokenHash(tokenHash: String): DeviceIdentity? = transaction(database) {
        Devices.join(Players, JoinType.INNER, onColumn = Devices.playerId, otherColumn = Players.id)
            .selectAll()
            .where { (Devices.tokenHash eq tokenHash) and Devices.revokedAt.isNull() }
            .singleOrNull()
            ?.let {
                DeviceIdentity(
                    deviceId = it[Devices.id].toString(),
                    playerId = it[Players.id].toString(),
                    clubId = it[Players.clubId].toString(),
                )
            }
    }

    override fun touchDevice(deviceId: String, at: Instant) {
        transaction(database) {
            Devices.update({ Devices.id eq uuidOf(deviceId, "deviceId") }) {
                it[lastSeenAt] = at.toOffset()
            }
        }
    }

    // --- Lesen ---

    override fun findSession(clubId: String, sessionId: String): StoredSession? = transaction(database) {
        loadSessions(clubId, listOf(uuidOf(sessionId, "sessionId"))).values.firstOrNull()
    }

    override fun findSessions(clubId: String, sessionIds: Collection<String>): Map<String, StoredSession> {
        if (sessionIds.isEmpty()) return emptyMap()
        return transaction(database) {
            loadSessions(clubId, sessionIds.map { uuidOf(it, "sessionId") })
        }
    }

    override fun roundsOfSession(clubId: String, sessionId: String): List<StoredRound> = transaction(database) {
        val id = uuidOf(sessionId, "sessionId")
        roundsWhere(orderBySequence = true) {
            (Sessions.clubId eq uuidOf(clubId, "clubId")) and (Rounds.sessionId eq id)
        }
    }

    /**
     * Das Delta-Fenster, in EINER Transaktion und damit aus einem Snapshot.
     *
     * Runden und Sessions getrennt zu lesen war nicht das Problem - sie in
     * getrennten Transaktionen zu lesen war es: ein Push, der dazwischen
     * commitete, tauchte im Cursor auf, aber in keiner der beiden Listen.
     */
    override fun pullSince(clubId: String, cursor: Long, limit: Int): PullPage = transaction(database) {
        val club = uuidOf(clubId, "clubId")
        val rounds = roundsWhere(orderBySequence = false, limit = limit) {
            (Sessions.clubId eq club) and (Rounds.updatedSeq greater cursor)
        }
        val sessionRows = Sessions.selectAll()
            .where { (Sessions.clubId eq club) and (Sessions.updatedSeq greater cursor) }
            .orderBy(Sessions.updatedSeq to SortOrder.ASC)
            .limit(limit)
            .toList()
        val seats = seatsOf(sessionRows.map { it[Sessions.id] })
        val sessions = sessionRows.map { storedSessionOf(it, seats[it[Sessions.id]] ?: emptyMap()) }

        pullPageOf(rounds, sessions, since = cursor, limit = limit)
    }

    /**
     * Rangliste aus der View `leaderboard_season`. Der LEFT JOIN auf den Kader
     * sorgt dafuer, dass ein Spieler ohne einzige Runde mit 0 in der Tabelle
     * steht statt zu fehlen - eine Luecke liest sich wie ein Fehler.
     */
    override fun leaderboard(clubId: String, season: Int?): List<LeaderboardEntry> = transaction(database) {
        val seasonFilter = if (season == null) "" else " AND l.season = ?"
        val sql = """
            SELECT p.id::text                          AS player_id,
                   p.display_name                      AS display_name,
                   COALESCE(SUM(l.rounds_played), 0)   AS rounds_played,
                   COALESCE(SUM(l.sessions_played), 0) AS sessions_played,
                   COALESCE(SUM(l.total_half_points), 0) AS total_half_points,
                   COALESCE(SUM(l.games_declared), 0)  AS games_declared,
                   COALESCE(SUM(l.games_declared_won), 0) AS games_declared_won
            FROM player p
                     LEFT JOIN leaderboard_season l ON l.player_id = p.id$seasonFilter
            WHERE p.club_id = CAST(? AS UUID)
            GROUP BY p.id, p.display_name
            ORDER BY total_half_points DESC, p.display_name ASC
        """.trimIndent()

        val args = buildList<Pair<IColumnType<*>, Any?>> {
            if (season != null) add(textArg() to season.toString())
            add(textArg() to clubId)
        }
        // season kommt als Text und wird gecastet, damit alle Parameter
        // denselben Bindungsweg nehmen.
        val castSql = if (season == null) sql else sql.replace("l.season = ?", "l.season = CAST(? AS INTEGER)")

        exec(castSql, args, StatementType.SELECT) { rs ->
            val entries = mutableListOf<LeaderboardEntry>()
            while (rs.next()) {
                entries += LeaderboardEntry(
                    playerId = rs.getString("player_id"),
                    displayName = rs.getString("display_name"),
                    roundsPlayed = rs.getInt("rounds_played"),
                    sessionsPlayed = rs.getInt("sessions_played"),
                    totalHalfPoints = rs.getLong("total_half_points"),
                    gamesDeclared = rs.getInt("games_declared"),
                    gamesDeclaredWon = rs.getInt("games_declared_won"),
                )
            }
            entries.toList()
        } ?: emptyList()
    }

    // --- Schreiben ---

    override fun applySync(
        clubId: String,
        sessions: List<SessionWrite>,
        rounds: List<RoundWrite>,
    ): SyncOutcome = transaction(database) {
        val club = uuidOf(clubId, "clubId")

        // Schreibende Batches eines Vereins laufen nacheinander. Der Grund ist
        // der Sequenzzaehler: nextval vergibt die Nummer bei der Ausfuehrung,
        // sichtbar wird die Zeile erst beim Commit. Ohne diese Sperre kann ein
        // langsamer Batch die kleinere Nummer ziehen und spaeter commiten als
        // ein schneller - der Delta-Pull dazwischen liefert die groessere aus,
        // der Client speichert sie als Cursor, und die kleinere faellt darunter
        // und wird nie mehr geliefert. Die Sperre nimmt der Reihenfolge des
        // Zaehlers genau diesen Freiheitsgrad: Sperre, Nummer und Commit laufen
        // in derselben Ordnung.
        //
        // Sie deckt zugleich den Anlegefall ab, an dem das FOR UPDATE unten
        // nichts ausrichtet: eine Zeile, die es noch nicht gibt, laesst sich
        // nicht sperren, und zwei gleichzeitige Pushes derselben neuen Runde
        // entschieden beide ACCEPTED.
        lockClub(clubId)

        // Der gespeicherte Stand wird mit FOR UPDATE gelesen: solange dieser
        // Batch laeuft, kann kein zweites Geraet denselben Datensatz
        // umschreiben und damit die Konfliktpruefung unterlaufen.
        val sessionState = lockState("session", sessions.map { it.session.id })
        val roundState = lockState("round", rounds.map { it.round.id })

        val conflicts = mutableListOf<SyncConflict>()
        val sessionDecisions = sessions.map { write ->
            decideSync(
                write.session.id,
                "session",
                write.revision,
                write.contentHash,
                sessionState[write.session.id],
                conflicts,
            )
        }
        val roundDecisions = rounds.map { write ->
            decideSync(write.round.id, "round", write.revision, write.contentHash, roundState[write.round.id], conflicts)
        }

        if (conflicts.isNotEmpty()) {
            // Der Batch ist atomar: entweder alles oder nichts. Ein halb
            // angenommener Batch waere fuer den Client nicht wiederholbar.
            // Geschrieben wurde an dieser Stelle noch gar nichts - das
            // rollback() gibt vor allem die Zeilensperren wieder frei.
            val cursor = currentSeqIn(club)
            rollback()
            return@transaction SyncOutcome(conflicts = conflicts, cursor = cursor)
        }

        // Angenommen entschieden, aber die Anweisung hat nichts geschrieben.
        // Das darf nicht als ACCEPTED durchgehen: der Client raeumt darauf hin
        // seine pendingSync-Markierung ab, und weil die Revision gleich bleibt,
        // holt auch der Pull die Fassung nie nach - sie erreicht den Server
        // dann nie mehr, ohne dass irgendwo ein Fehler entsteht.
        val lost = mutableListOf<SyncConflict>()

        val sessionResults = sessions.mapIndexed { index, write ->
            val decision = sessionDecisions[index]
            if (decision != SyncResult.ACCEPTED) {
                SyncDecision(write.session.id, decision, sessionState[write.session.id]?.updatedSeq ?: 0L)
            } else {
                val seq = upsertSession(write)
                if (seq == null) {
                    lost += lostWrite(write.session.id, "session", write.revision, write.contentHash, sessionState)
                    SyncDecision(write.session.id, SyncResult.STALE, 0L)
                } else {
                    writeSeats(write)
                    SyncDecision(write.session.id, SyncResult.ACCEPTED, seq)
                }
            }
        }

        val roundResults = rounds.mapIndexed { index, write ->
            val decision = roundDecisions[index]
            if (decision != SyncResult.ACCEPTED) {
                SyncDecision(write.round.id, decision, roundState[write.round.id]?.updatedSeq ?: 0L)
            } else {
                val seq = upsertRound(write)
                if (seq == null) {
                    lost += lostWrite(write.round.id, "round", write.revision, write.contentHash, roundState)
                    SyncDecision(write.round.id, SyncResult.STALE, 0L)
                } else {
                    writeScores(write)
                    SyncDecision(write.round.id, SyncResult.ACCEPTED, seq)
                }
            }
        }

        if (lost.isNotEmpty()) {
            val cursor = currentSeqIn(club)
            rollback()
            return@transaction SyncOutcome(conflicts = lost, cursor = cursor)
        }

        SyncOutcome(
            sessions = sessionResults,
            rounds = roundResults,
            conflicts = emptyList(),
            cursor = currentSeqIn(club),
        )
    }

    // --- Interna ---

    /** Ein angenommener, aber nicht geschriebener Datensatz ist ein Konflikt. */
    private fun lostWrite(
        id: String,
        kind: String,
        revision: Long,
        contentHash: String,
        state: Map<String, RowVersion>,
    ): SyncConflict = SyncConflict(
        id = id,
        kind = kind,
        revision = revision,
        serverContentHash = state[id]?.contentHash.orEmpty(),
        clientContentHash = contentHash,
        deleted = state[id]?.deleted ?: false,
    )

    /**
     * Die Schreibsperre eines Vereins, gueltig bis zum Ende der Transaktion.
     *
     * Ein Verein hat ein schreibendes Geraet und ein paar Runden pro Abend -
     * die Serialisierung kostet hier nichts und kauft die Commit-Ordnung des
     * Sequenzzaehlers.
     */
    private fun org.jetbrains.exposed.sql.Transaction.lockClub(clubId: String) {
        exec(
            CLUB_WRITE_LOCK_SQL,
            listOf(textArg() to CLUB_LOCK_NAMESPACE.toString(), textArg() to clubId),
            StatementType.SELECT,
        ) { it.next() }
    }

    private fun org.jetbrains.exposed.sql.Transaction.lockState(
        table: String,
        ids: List<String>,
    ): Map<String, RowVersion> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(", ") { "CAST(? AS UUID)" }
        val sql = "SELECT id::text AS id, revision, content_hash, updated_seq, deleted_at " +
            "FROM $table WHERE id IN ($placeholders) FOR UPDATE"
        val args = ids.map<String, Pair<IColumnType<*>, Any?>> { textArg() to it }
        return exec(sql, args, StatementType.SELECT) { rs ->
            val state = mutableMapOf<String, RowVersion>()
            while (rs.next()) {
                state[rs.getString("id")] = RowVersion(
                    revision = rs.getLong("revision"),
                    contentHash = rs.getString("content_hash"),
                    updatedSeq = rs.getLong("updated_seq"),
                    // Der Tombstone gehoert in die Konfliktmeldung: sonst hebt
                    // der Client seine Revision an und belebt die geloeschte
                    // Runde beim naechsten Push wieder.
                    deleted = rs.getObject("deleted_at") != null,
                )
            }
            state.toMap()
        } ?: emptyMap()
    }

    private fun org.jetbrains.exposed.sql.Transaction.currentSeqIn(club: UUID): Long {
        val fromSessions = Sessions.selectAll().where { Sessions.clubId eq club }
            .orderBy(Sessions.updatedSeq to SortOrder.DESC).limit(1)
            .firstOrNull()?.get(Sessions.updatedSeq) ?: 0L
        val fromRounds = Rounds
            .join(Sessions, JoinType.INNER, onColumn = Rounds.sessionId, otherColumn = Sessions.id)
            .selectAll().where { Sessions.clubId eq club }
            .orderBy(Rounds.updatedSeq to SortOrder.DESC).limit(1)
            .firstOrNull()?.get(Rounds.updatedSeq) ?: 0L
        return maxOf(fromSessions, fromRounds)
    }

    /**
     * Der bedingte Upsert. `RETURNING` liefert nur dann eine Zeile, wenn
     * wirklich geschrieben wurde - die WHERE-Klausel ist damit nicht nur
     * Absicht, sondern nachweisbar wirksam.
     */
    private fun org.jetbrains.exposed.sql.Transaction.upsertRound(write: RoundWrite): Long? =
        exec(ROUND_UPSERT_SQL, roundUpsertArgs(write), StatementType.SELECT) { rs ->
            if (rs.next()) rs.getLong("updated_seq") else null
        }

    private fun org.jetbrains.exposed.sql.Transaction.upsertSession(write: SessionWrite): Long? =
        exec(SESSION_UPSERT_SQL, sessionUpsertArgs(write), StatementType.SELECT) { rs ->
            if (rs.next()) rs.getLong("updated_seq") else null
        }


    private fun writeSeats(write: SessionWrite) {
        val id = uuidOf(write.session.id, "sessionId")
        SessionSeats.deleteWhere { SessionSeats.sessionId eq id }
        for ((seat, playerId) in write.session.seats.entries.sortedBy { it.key }) {
            SessionSeats.insert {
                it[SessionSeats.sessionId] = id
                it[SessionSeats.seat] = seat.toShort()
                it[SessionSeats.playerId] = uuidOf(playerId, "playerId")
            }
        }
    }

    /**
     * Die Punkte werden bei jedem angenommenen Upsert vollstaendig neu
     * geschrieben. Der Nullsummen-Trigger aus V1 ist DEFERRABLE, das
     * Loeschen-und-Neuschreiben faellt ihm deshalb innerhalb der Transaktion
     * nicht auf die Fuesse.
     */
    private fun writeScores(write: RoundWrite) {
        val id = uuidOf(write.round.id, "roundId")
        RoundScores.deleteWhere { RoundScores.roundId eq id }
        for ((seat, halfPoints) in write.halfPoints.entries.sortedBy { it.key }) {
            RoundScores.insert {
                it[RoundScores.roundId] = id
                it[RoundScores.seat] = seat.toShort()
                it[RoundScores.halfPoints] = halfPoints
            }
        }
    }

    private fun loadSessions(clubId: String, ids: List<UUID>): Map<String, StoredSession> {
        if (ids.isEmpty()) return emptyMap()
        val rows = Sessions.selectAll()
            .where { (Sessions.clubId eq uuidOf(clubId, "clubId")) and (Sessions.id inList ids) }
            .toList()
        val seats = seatsOf(rows.map { it[Sessions.id] })
        return rows.associate { row ->
            val stored = storedSessionOf(row, seats[row[Sessions.id]] ?: emptyMap())
            stored.session.id to stored
        }
    }

    private fun seatsOf(sessionIds: List<UUID>): Map<UUID, Map<Int, String>> {
        if (sessionIds.isEmpty()) return emptyMap()
        return SessionSeats.selectAll().where { SessionSeats.sessionId inList sessionIds }
            .groupBy { it[SessionSeats.sessionId] }
            .mapValues { (_, rows) ->
                rows.associate { it[SessionSeats.seat].toInt() to it[SessionSeats.playerId].toString() }
            }
    }

    private fun roundsWhere(
        orderBySequence: Boolean,
        limit: Int? = null,
        predicate: SqlExpressionBuilder.() -> org.jetbrains.exposed.sql.Op<Boolean>,
    ): List<StoredRound> {
        val base = Rounds
            .join(Sessions, JoinType.INNER, onColumn = Rounds.sessionId, otherColumn = Sessions.id)
            .selectAll()
            .where { predicate() }
        // Nach (sequence, id), nicht nach sequence allein: die Position ist seit
        // V3 nicht mehr eindeutig, und die Id ist eine zeitsortierte UUIDv7 -
        // damit ist auch ein Gleichstand eindeutig geordnet statt zufaellig.
        val query = if (orderBySequence) {
            base.orderBy(Rounds.sequence to SortOrder.ASC, Rounds.id to SortOrder.ASC)
        } else {
            base.orderBy(Rounds.updatedSeq to SortOrder.ASC)
        }
        val rows = (if (limit != null) query.limit(limit) else query).toList()

        val scores = if (rows.isEmpty()) emptyMap() else {
            RoundScores.selectAll().where { RoundScores.roundId inList rows.map { it[Rounds.id] } }
                .groupBy { it[RoundScores.roundId] }
                .mapValues { (_, seatRows) ->
                    seatRows.associate { it[RoundScores.seat].toInt() to it[RoundScores.halfPoints] }
                }
        }
        return rows.map { storedRoundOf(it, scores[it[Rounds.id]] ?: emptyMap()) }
    }

    private fun clubOf(row: ResultRow): Club {
        val clubId = row[Clubs.id]
        val roster = Players.selectAll().where { Players.clubId eq clubId }
            .orderBy(Players.displayName to SortOrder.ASC)
            .map { Player(it[Players.id].toString(), it[Players.displayName], it[Players.isActive]) }
        return Club(
            id = clubId.toString(),
            name = row[Clubs.name],
            scoring = RotaskatJson.decodeFromString<ScoringConfig>(row[Clubs.scoringConfig]),
            centsPerPoint = row[Clubs.centsPerPoint],
            roster = roster,
        )
    }

    private fun storedSessionOf(row: ResultRow, seats: Map<Int, String>): StoredSession = StoredSession(
        session = Session(
            id = row[Sessions.id].toString(),
            clubId = row[Sessions.clubId].toString(),
            seatCount = row[Sessions.seatCount].toInt(),
            seats = seats,
            status = SessionStatus.valueOf(row[Sessions.status].uppercase()),
            startedAt = row[Sessions.startedAt].toKotlin(),
            endedAt = row[Sessions.endedAt]?.toKotlin(),
            scoring = RotaskatJson.decodeFromString<ScoringConfig>(row[Sessions.scoringConfig]),
            scoringVersion = row[Sessions.scoringVersion],
            centsPerPoint = row[Sessions.centsPerPoint],
            dealerSeat = row[Sessions.dealerSeat].toInt(),
        ),
        revision = row[Sessions.revision],
        contentHash = row[Sessions.contentHash],
        deletedAt = row[Sessions.deletedAt]?.toKotlin(),
        updatedSeq = row[Sessions.updatedSeq],
    )

    private fun storedRoundOf(row: ResultRow, halfPoints: Map<Int, Int>): StoredRound = StoredRound(
        round = Round(
            id = row[Rounds.id].toString(),
            // seatCount steht an der Session, nicht an der Runde: der Tisch
            // wechselt seine Groesse innerhalb eines Abends nicht.
            seatCount = row[Sessions.seatCount].toInt(),
            declarerSeat = row[Rounds.declarerSeat]?.toInt(),
            sittingOutSeat = row[Rounds.sittingOutSeat]?.toInt(),
            declaration = RotaskatJson.decodeFromString<Declaration>(row[Rounds.declaration]),
            won = row[Rounds.won],
            contra = ContraLevel.valueOf(row[Rounds.contra]),
            bid = row[Rounds.bid],
            overbid = row[Rounds.overbid],
            ramsch = row[Rounds.ramsch]?.let { RotaskatJson.decodeFromString<RamschResult>(it) },
            dealerSeat = row[Rounds.dealerSeat].toInt(),
        ),
        sessionId = row[Rounds.sessionId].toString(),
        sequence = row[Rounds.sequence],
        revision = row[Rounds.revision],
        contentHash = row[Rounds.contentHash],
        deletedAt = row[Rounds.deletedAt]?.toKotlin(),
        updatedSeq = row[Rounds.updatedSeq],
        halfPoints = halfPoints,
        gameValue = row[Rounds.gameValue],
    )
}

/**
 * Frei gewaehlter Namensraum der Vorgangssperre. Er trennt sie von jeder
 * anderen advisory lock, die spaeter einmal dazukommen koennte.
 */
internal const val CLUB_LOCK_NAMESPACE = 1734

/**
 * Die Vorgangssperre eines Vereins.
 *
 * `pg_advisory_xact_lock` haengt an der Transaktion und wird beim Commit oder
 * Rollback von selbst wieder frei - anders als eine Zeilensperre braucht sie
 * keine Zeile, die es schon gibt.
 */
internal val CLUB_WRITE_LOCK_SQL =
    "SELECT pg_advisory_xact_lock(CAST(? AS INTEGER), hashtext(CAST(? AS TEXT)))"

/**
 * Der bedingte Upsert einer Runde.
 *
 * Steht als Konstante auf Dateiebene, damit ein Test die Anweisung pruefen
 * kann, ohne eine Datenbank zu brauchen: Spaltennamen gegen das Schema,
 * Platzhalterzahl gegen die Parameterliste, und ob jede eingefuegte Spalte im
 * UPDATE-Zweig wieder auftaucht. Genau das sind die Fehler, die beim
 * Nachtragen einer Spalte passieren.
 *
 * `RETURNING` liefert nur dann eine Zeile, wenn wirklich geschrieben wurde -
 * die WHERE-Klausel ist damit nicht bloss Absicht, sondern am Rueckgabewert
 * ablesbar.
 */
internal val ROUND_UPSERT_SQL = """
    INSERT INTO round (id, session_id, sequence, declarer_seat, sitting_out_seat, dealer_seat,
                       declaration, won, contra, bid, overbid, ramsch, game_value,
                       revision, content_hash, updated_seq, client_half_points,
                       score_mismatch_at, deleted_at)
    VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS INTEGER),
            CAST(? AS SMALLINT), CAST(? AS SMALLINT), CAST(? AS SMALLINT),
            CAST(? AS JSONB), CAST(? AS BOOLEAN), ?, CAST(? AS INTEGER), CAST(? AS BOOLEAN),
            CAST(? AS JSONB), CAST(? AS INTEGER),
            CAST(? AS BIGINT), ?, nextval('sync_seq'), CAST(? AS JSONB),
            CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
    ON CONFLICT (id) DO UPDATE SET
        session_id         = excluded.session_id,
        sequence           = excluded.sequence,
        declarer_seat      = excluded.declarer_seat,
        sitting_out_seat   = excluded.sitting_out_seat,
        dealer_seat        = excluded.dealer_seat,
        declaration        = excluded.declaration,
        won                = excluded.won,
        contra             = excluded.contra,
        bid                = excluded.bid,
        overbid            = excluded.overbid,
        ramsch             = excluded.ramsch,
        game_value         = excluded.game_value,
        revision           = excluded.revision,
        content_hash       = excluded.content_hash,
        client_half_points = excluded.client_half_points,
        score_mismatch_at  = excluded.score_mismatch_at,
        deleted_at         = excluded.deleted_at,
        updated_seq        = nextval('sync_seq')
    WHERE excluded.revision > round.revision
      -- Eine Runde wechselt ihren Abend nicht. Ohne diese Bedingung genuegte
      -- eine fremde Runden-Id mit hoher Revision, um sie in eine andere Session
      -- und damit in einen anderen Verein umzuhaengen - der Upsert selbst kennt
      -- keinen Vereinsbezug.
      AND round.session_id = excluded.session_id
    RETURNING updated_seq
""".trimIndent()

/**
 * Alle Parameter gehen als Text hinein und werden im SQL gecastet. Das haelt
 * die Bindung einheitlich und erspart eine Handvoll Exposed-Spaltentypen, die
 * ausser dem Bindungsweg nichts beitragen wuerden.
 */
internal fun roundUpsertArgs(write: RoundWrite): List<Pair<IColumnType<*>, Any?>> {
    val round = write.round
    return listOf(
        textArg() to round.id,
        textArg() to write.sessionId,
        textArg() to write.sequence.toString(),
        nullableTextArg() to round.declarerSeat?.toString(),
        nullableTextArg() to round.sittingOutSeat?.toString(),
        textArg() to round.dealerSeat.toString(),
        textArg() to RotaskatJson.encodeToString<Declaration>(round.declaration),
        textArg() to round.won.toString(),
        textArg() to round.contra.name,
        nullableTextArg() to round.bid?.toString(),
        textArg() to round.overbid.toString(),
        nullableTextArg() to round.ramsch?.let { RotaskatJson.encodeToString(it) },
        textArg() to write.gameValue.toString(),
        textArg() to write.revision.toString(),
        textArg() to write.contentHash,
        // Leere Pruefsumme heisst: der Client hat keine geschickt. Dann bleibt
        // die Spalte NULL, statt ein leeres Objekt zu behaupten.
        nullableTextArg() to write.clientHalfPoints.takeIf { it.isNotEmpty() }
            ?.let { RotaskatJson.encodeToString(it) },
        nullableTextArg() to if (write.scoreMismatch) OffsetDateTime.now(ZoneOffset.UTC).toString() else null,
        nullableTextArg() to write.deletedAt?.toOffset()?.toString(),
    )
}

internal val SESSION_UPSERT_SQL = """
    INSERT INTO session (id, club_id, seat_count, status, started_at, ended_at,
                         scoring_config, scoring_version, cents_per_point, dealer_seat,
                         revision, content_hash, updated_seq, deleted_at)
    VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS SMALLINT), ?,
            CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ),
            CAST(? AS JSONB), CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS SMALLINT),
            CAST(? AS BIGINT), ?, nextval('sync_seq'), CAST(? AS TIMESTAMPTZ))
    ON CONFLICT (id) DO UPDATE SET
        club_id         = excluded.club_id,
        seat_count      = excluded.seat_count,
        status          = excluded.status,
        started_at      = excluded.started_at,
        ended_at        = excluded.ended_at,
        scoring_config  = excluded.scoring_config,
        scoring_version = excluded.scoring_version,
        cents_per_point = excluded.cents_per_point,
        dealer_seat     = excluded.dealer_seat,
        revision        = excluded.revision,
        content_hash    = excluded.content_hash,
        deleted_at      = excluded.deleted_at,
        updated_seq     = nextval('sync_seq')
    WHERE excluded.revision > session.revision
    RETURNING updated_seq
""".trimIndent()

internal fun sessionUpsertArgs(write: SessionWrite): List<Pair<IColumnType<*>, Any?>> {
    val session = write.session
    return listOf(
        textArg() to session.id,
        textArg() to session.clubId,
        textArg() to session.seatCount.toString(),
        // V1 schreibt den Status klein vor, das Modell fuehrt ihn als Enum.
        textArg() to session.status.name.lowercase(),
        textArg() to session.startedAt.toOffset().toString(),
        nullableTextArg() to session.endedAt?.toOffset()?.toString(),
        textArg() to RotaskatJson.encodeToString(session.scoring),
        textArg() to session.scoringVersion.toString(),
        textArg() to session.centsPerPoint.toString(),
        textArg() to session.dealerSeat.toString(),
        textArg() to write.revision.toString(),
        textArg() to write.contentHash,
        nullableTextArg() to write.deletedAt?.toOffset()?.toString(),
    )
}

private fun textArg(): TextColumnType = TextColumnType()

private fun nullableTextArg(): TextColumnType = TextColumnType().apply { nullable = true }

private fun uuidOf(value: String, field: String): UUID = try {
    UUID.fromString(value)
} catch (_: IllegalArgumentException) {
    badRequest("$field '$value' ist keine gueltige UUID")
}

private fun Instant.toOffset(): OffsetDateTime = toJavaInstant().atOffset(ZoneOffset.UTC)

private fun OffsetDateTime.toKotlin(): Instant = toInstant().toKotlinInstant()
