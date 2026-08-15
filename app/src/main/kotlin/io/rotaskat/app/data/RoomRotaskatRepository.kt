package io.rotaskat.app.data

import android.util.Log
import androidx.room.withTransaction
import io.rotaskat.app.data.db.ClubEntity
import io.rotaskat.app.data.db.PlayerEntity
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.app.data.db.RoundEntity
import io.rotaskat.app.data.db.SessionEntity
import io.rotaskat.app.data.db.SessionSeatEntity
import io.rotaskat.app.data.db.SyncStateEntity
import io.rotaskat.app.data.id.Uuid7
import io.rotaskat.app.data.sync.SyncStore
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.model.TableRotation
import io.rotaskat.shared.scoring.RoundScore
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private const val TAG = "RotaskatRepo"

/**
 * Die Room-Umsetzung von [RotaskatRepository] und [SyncStore].
 *
 * Beides in einer Klasse, weil beide Seiten dieselben Zeilen anfassen und die
 * Revisionsregel sonst zweimal geschrieben waere. Nach aussen bleiben es zwei
 * Schnittstellen: die Oberflaeche sieht [RotaskatRepository], der Worker
 * [SyncStore].
 */
class RoomRotaskatRepository(
    private val database: RotaskatDatabase,
    private val syncTrigger: SyncTrigger = SyncTrigger.None,
) : RotaskatRepository, SyncStore {

    private val clubDao = database.clubDao()
    private val playerDao = database.playerDao()
    private val sessionDao = database.sessionDao()
    private val seatDao = database.sessionSeatDao()
    private val roundDao = database.roundDao()
    private val syncStateDao = database.syncStateDao()

    // --- Lesen ------------------------------------------------------------

    override fun observeClub(): Flow<Club?> =
        combine(clubDao.observe(), playerDao.observeAll()) { club, players ->
            club?.toModel(players.filter { it.clubId == club.id })
        }

    override fun observeRoster(): Flow<List<Player>> =
        playerDao.observeAll().map { players -> players.map { it.toModel() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpenSession(): Flow<SessionState?> =
        sessionDao.observeOpen().flatMapLatest { session ->
            if (session == null) flowOf(null) else observeSession(session.id)
        }

    override fun observeSession(sessionId: String): Flow<SessionState?> =
        combine(
            sessionDao.observe(sessionId),
            seatDao.observeBySession(sessionId),
            roundDao.observeBySession(sessionId),
        ) { session, seats, rounds ->
            session?.let { stateOf(it.toModel(seats), rounds) }
        }

    override fun observeSessions(): Flow<List<SessionSummary>> =
        sessionDao.observeAll().map { sessions ->
            val seats = seatDao.bySessions(sessions.map { it.id }).groupBy { it.sessionId }
            sessions.map { entity ->
                SessionSummary(
                    session = entity.toModel(seats[entity.id].orEmpty()),
                    roundCount = roundDao.countLive(entity.id),
                )
            }
        }

    override fun observeSessionStates(): Flow<List<SessionState>> =
        combine(sessionDao.observeAll(), roundDao.observeAll()) { sessions, rounds ->
            val seats = seatDao.bySessions(sessions.map { it.id }).groupBy { it.sessionId }
            // Runden ohne bekannte Session bleiben liegen statt zu verschwinden:
            // der Delta-Pull kann eine Runde vor ihrer Session liefern, und beim
            // naechsten Lauf sortiert sie sich von selbst ein.
            val bySession = rounds.groupBy { it.sessionId }
            sessions.map { entity ->
                val session = entity.toModel(seats[entity.id].orEmpty())
                stateOf(session, bySession[entity.id].orEmpty())
            }
        }

    override fun observePendingSyncCount(): Flow<Int> = roundDao.observePendingCount()

    override suspend fun club(): Club? {
        val club = clubDao.current() ?: return null
        return club.toModel(playerDao.roster(club.id))
    }

    override suspend fun session(sessionId: String): SessionState? {
        val entity = sessionDao.find(sessionId) ?: return null
        val session = entity.toModel(seatDao.bySession(sessionId))
        return stateOf(session, roundDao.bySession(sessionId))
    }

    // --- Schreiben --------------------------------------------------------

    override suspend fun saveClub(club: Club) {
        database.withTransaction {
            clubDao.upsert(ClubEntity(club.id, club.name, club.scoring, club.centsPerPoint))
            if (club.roster.isNotEmpty()) {
                playerDao.upsertAll(
                    club.roster.map { PlayerEntity(it.id, club.id, it.displayName, it.active) }
                )
                // Ein Spieler, den der Server nicht mehr fuehrt, verschwindet
                // auch lokal. Bei leerem Kader passiert nichts - das waere sonst
                // eine Antwort ohne Kader, die den ganzen Verein leerraeumt.
                playerDao.deleteMissing(club.id, club.roster.map { it.id })
            }
        }
    }

    override suspend fun startSession(
        seatCount: Int,
        seats: Map<Int, String>,
        dealerSeat: Int,
        startedAt: Instant,
    ): String {
        require(seatCount in 3..4) { "seatCount muss 3 oder 4 sein, ist $seatCount" }
        require(dealerSeat in 0 until seatCount) { "dealerSeat $dealerSeat liegt ausserhalb des Tisches" }
        val missing = (0 until seatCount).filter { it !in seats.keys }
        require(missing.isEmpty()) { "Sitzplaetze $missing sind unbesetzt" }

        val club = club() ?: error("Ohne Verein laesst sich kein Abend starten")
        val session = Session.startedFor(
            id = Uuid7.next(),
            club = club,
            seatCount = seatCount,
            seats = seats,
            startedAt = startedAt,
            dealerSeat = dealerSeat,
        )
        database.withTransaction {
            sessionDao.insert(session.toEntity(revision = 1, deletedAt = null, pendingSync = true))
            seatDao.insertAll(session.toSeatEntities())
        }
        syncTrigger.requestSync()
        return session.id
    }

    override suspend fun endSession(sessionId: String, endedAt: Instant) {
        database.withTransaction {
            val entity = sessionDao.find(sessionId) ?: return@withTransaction
            if (entity.status == SessionStatus.CLOSED) return@withTransaction
            bumpSession(entity.copy(status = SessionStatus.CLOSED, endedAt = endedAt))
        }
        syncTrigger.requestSync()
    }

    override suspend fun setDealer(sessionId: String, dealerSeat: Int) {
        database.withTransaction {
            val entity = sessionDao.find(sessionId) ?: return@withTransaction
            require(dealerSeat in 0 until entity.seatCount) {
                "dealerSeat $dealerSeat liegt ausserhalb des Tisches"
            }
            if (entity.dealerSeat == dealerSeat) return@withTransaction
            bumpSession(entity.copy(dealerSeat = dealerSeat))
        }
        syncTrigger.requestSync()
    }

    override fun newRoundId(): String = Uuid7.next()

    override suspend fun recordRound(sessionId: String, round: Round): String {
        database.withTransaction {
            val session = sessionDao.find(sessionId) ?: error("Session $sessionId ist unbekannt")
            requireValid(round, session)

            val existing = roundDao.find(round.id)
            when {
                existing == null -> roundDao.insert(
                    RoundEntity(
                        id = round.id,
                        sessionId = sessionId,
                        sequence = roundDao.nextSequence(sessionId),
                        revision = 1,
                        deletedAt = null,
                        pendingSync = true,
                        payload = round,
                    )
                )
                // Derselbe Inhalt noch einmal: der Doppeltap am Tisch. Nicht als
                // Korrektur zaehlen, sonst zoege jeder versehentliche zweite Tap
                // eine Revision und einen ueberfluessigen Push nach sich.
                existing.payload == round && existing.deletedAt == null -> Unit

                else -> roundDao.upsert(
                    existing.copy(
                        payload = round,
                        revision = existing.revision + 1,
                        deletedAt = null,
                        pendingSync = true,
                    )
                )
            }
            advanceRotation(sessionId)
        }
        syncTrigger.requestSync()
        return round.id
    }

    override suspend fun correctRound(round: Round) {
        database.withTransaction {
            val existing = roundDao.find(round.id) ?: error("Runde ${round.id} ist unbekannt")
            val session = sessionDao.find(existing.sessionId)
                ?: error("Session ${existing.sessionId} ist unbekannt")
            requireValid(round, session)
            if (existing.payload == round && existing.deletedAt == null) return@withTransaction

            roundDao.upsert(
                existing.copy(
                    payload = round,
                    revision = existing.revision + 1,
                    deletedAt = null,
                    pendingSync = true,
                )
            )
            advanceRotation(existing.sessionId)
        }
        syncTrigger.requestSync()
    }

    override suspend fun deleteRound(roundId: String, at: Instant) {
        database.withTransaction {
            val existing = roundDao.find(roundId) ?: return@withTransaction
            if (existing.deletedAt != null) return@withTransaction
            // Tombstone statt DELETE: physisch geloescht kaeme die Runde beim
            // naechsten Delta-Pull ungefragt zurueck.
            roundDao.upsert(
                existing.copy(
                    deletedAt = at,
                    revision = existing.revision + 1,
                    pendingSync = true,
                )
            )
            advanceRotation(existing.sessionId)
        }
        syncTrigger.requestSync()
    }

    // --- SyncStore --------------------------------------------------------

    override suspend fun pendingRounds(limit: Int): List<RoundEnvelope> {
        val entities = roundDao.pending(limit)
        if (entities.isEmpty()) return emptyList()
        val configs = sessionDao.findAll(entities.map { it.sessionId }.distinct())
            .associate { it.id to it.scoring }
        return entities.map { it.toEnvelope(configs[it.sessionId]) }
    }

    override suspend fun pendingSessions(limit: Int): List<SessionEnvelope> =
        sessionDao.pending(limit).toEnvelopes()

    override suspend fun sessionEnvelopes(ids: Set<String>): List<SessionEnvelope> {
        if (ids.isEmpty()) return emptyList()
        return sessionDao.findAll(ids.toList()).toEnvelopes()
    }

    override suspend fun clearPending(sessions: List<SessionEnvelope>, rounds: List<RoundEnvelope>) {
        database.withTransaction {
            sessions.forEach { sessionDao.clearPending(it.session.id, it.revision) }
            rounds.forEach { roundDao.clearPending(it.round.id, it.revision) }
        }
    }

    /**
     * Ein Konflikt heisst: der Server hat unter derselben Revision einen anderen
     * Inhalt. Nach den Produktentscheidungen kann das nicht vorkommen - ein
     * Geraet fuehrt Buch. Wenn es doch passiert (wiederhergestelltes Backup,
     * zweites Geraet), ist das Geraet am Tisch dasjenige mit den frischen
     * Daten, und die duerfen auf keinen Fall verlorengehen. Deshalb wird die
     * lokale Revision ueber den Serverstand gehoben, damit der naechste Push
     * durchgeht. Der Serverstand wird dabei ueberschrieben; das ist bewusst und
     * steht im Log.
     *
     * Mit GENAU EINER Ausnahme: gegen einen Tombstone gewinnt der lokale Stand
     * nicht. Eine Loeschung ist die einzige Aenderung, die ein Wiederherstellen
     * der lokalen Datei nicht kennen kann - sie wuerde die Runde sonst auf allen
     * Geraeten und in der All-Time-Rangliste wieder aufleben lassen, ohne dass
     * jemand sie zurueckgeholt haette. Eine echte Wiederbelebung gibt es weiter,
     * sie geht aber ueber das Undo am Tisch und damit ueber eine hoehere
     * Revision.
     */
    override suspend fun resolveConflicts(conflicts: List<SyncConflict>) {
        if (conflicts.isEmpty()) return
        val now = Clock.System.now()
        database.withTransaction {
            for (conflict in conflicts) {
                val loserSide = if (conflict.deleted) "Der Tombstone des Servers gewinnt." else "Der lokale Stand gewinnt."
                Log.w(
                    TAG,
                    "Sync-Konflikt bei ${conflict.kind} ${conflict.id}, Revision ${conflict.revision}: " +
                        "Server ${conflict.serverContentHash.take(8)}, lokal ${conflict.clientContentHash.take(8)}. " +
                        loserSide,
                )
                when (conflict.kind) {
                    "round" -> roundDao.find(conflict.id)?.let { local ->
                        if (conflict.deleted && local.deletedAt == null) {
                            // Denselben Stand wie der Server uebernehmen: gleiche
                            // Revision, geloescht. Der Inhalts-Hash haengt nur am
                            // OB der Loeschung, nicht am Zeitpunkt - der naechste
                            // Push meldet die Runde deshalb als UNCHANGED.
                            roundDao.upsert(
                                local.copy(deletedAt = now, revision = conflict.revision, pendingSync = false)
                            )
                        } else {
                            roundDao.upsert(
                                local.copy(
                                    revision = maxOf(local.revision, conflict.revision) + 1,
                                    pendingSync = true,
                                )
                            )
                        }
                    }
                    "session" -> sessionDao.find(conflict.id)?.let {
                        sessionDao.upsert(
                            it.copy(revision = maxOf(it.revision, conflict.revision) + 1, pendingSync = true)
                        )
                    }
                    else -> Log.w(TAG, "Unbekannte Konfliktart '${conflict.kind}' fuer ${conflict.id}")
                }
            }
        }
    }

    override suspend fun applyPull(sessions: List<SessionEnvelope>, rounds: List<RoundEnvelope>) {
        if (sessions.isEmpty() && rounds.isEmpty()) return
        database.withTransaction {
            for (envelope in sessions) {
                val local = sessionDao.find(envelope.session.id)
                // Nur eine echte Weiterentwicklung uebernehmen. Gleiche Revision
                // heisst gleicher Stand oder ein Konflikt, den der Push-Pfad
                // aufloest - beides ist kein Grund, den Tisch zu ueberschreiben.
                if (local != null && envelope.revision <= local.revision) continue
                sessionDao.upsert(
                    envelope.session.toEntity(
                        revision = envelope.revision,
                        deletedAt = envelope.deletedAt,
                        pendingSync = false,
                    )
                )
                seatDao.deleteBySession(envelope.session.id)
                seatDao.insertAll(envelope.session.toSeatEntities())
            }
            for (envelope in rounds) {
                val local = roundDao.find(envelope.round.id)
                if (local != null && envelope.revision <= local.revision) {
                    // Gleiche Revision, aber der Server fuehrt einen Tombstone:
                    // die Loeschung wird uebernommen statt uebersprungen. Sonst
                    // zaehlt der Abend hier dauerhaft eine Runde mit, die es auf
                    // keinem anderen Geraet mehr gibt - und zwar ohne dass
                    // irgendwo ein Konflikt sichtbar wuerde.
                    val nurTombstone = envelope.revision == local.revision &&
                        envelope.deletedAt != null &&
                        local.deletedAt == null
                    if (!nurTombstone) continue
                    roundDao.upsert(local.copy(deletedAt = envelope.deletedAt, pendingSync = false))
                    continue
                }
                roundDao.upsert(
                    RoundEntity(
                        id = envelope.round.id,
                        sessionId = envelope.sessionId,
                        sequence = envelope.sequence,
                        revision = envelope.revision,
                        deletedAt = envelope.deletedAt,
                        pendingSync = false,
                        payload = envelope.round,
                    )
                )
            }
        }
    }

    override suspend fun cursor(clubId: String): Long = syncStateDao.find(clubId)?.cursor ?: 0L

    override suspend fun saveCursor(clubId: String, cursor: Long, at: Instant) {
        syncStateDao.upsert(SyncStateEntity(clubId, cursor, at))
    }

    // --- Innereien --------------------------------------------------------

    private suspend fun bumpSession(entity: SessionEntity) {
        sessionDao.upsert(entity.copy(revision = entity.revision + 1, pendingSync = true))
    }

    /**
     * Schreibt die Sitzordnung aus der zuletzt gespielten Runde fort: der Geber
     * wandert einen Platz weiter.
     *
     * Abgeleitet aus der letzten lebenden Runde statt einfach hochgezaehlt, weil
     * dieselbe Regel dann fuer Eingabe, Korrektur und Undo gilt.
     *
     * Bleibt nach einem Undo keine lebende Runde uebrig, zaehlt die Stellung der
     * zuletzt eingetragenen: ihr Geber steht weiterhin im Rohdatensatz, der
     * Tombstone loescht ihn nicht. Ohne diesen Rueckfall bliebe der Geber nach
     * dem Undo der ersten Runde einen Platz weiter stehen - der Bildschirm
     * sagte dann, jemand gebe und setze aus, der gerade erst gegeben hat.
     */
    private suspend fun advanceRotation(sessionId: String) {
        val session = sessionDao.find(sessionId) ?: return
        val rounds = roundDao.bySession(sessionId)
        val last = rounds.lastOrNull { it.deletedAt == null }
        val next = if (last != null) {
            TableRotation.nextDealerSeat(last.payload.dealerSeat, session.seatCount)
        } else {
            rounds.lastOrNull()?.payload?.dealerSeat ?: return
        }
        if (next != session.dealerSeat) bumpSession(session.copy(dealerSeat = next))
    }

    private fun requireValid(round: Round, session: SessionEntity) {
        require(round.seatCount == session.seatCount) {
            "Runde ${round.id} hat seatCount ${round.seatCount}, die Session ${session.seatCount}"
        }
        val errors = Scoring.validate(round)
        require(errors.isEmpty()) { "Ungueltige Runde ${round.id}: ${errors.joinToString("; ")}" }
    }

    private fun stateOf(session: Session, entities: List<RoundEntity>): SessionState {
        val rounds = entities.map { it.toScored(session.scoring) }
        return SessionState(session, rounds, totalsOf(session, rounds))
    }

    private fun totalsOf(session: Session, rounds: List<ScoredRound>): Map<Int, Long> {
        val totals = (0 until session.seatCount).associateWith { 0L }.toMutableMap()
        for (round in rounds) {
            if (round.deleted) continue
            for ((seat, halfPoints) in round.score.halfPoints) {
                totals[seat] = (totals[seat] ?: 0L) + halfPoints
            }
        }
        return totals
    }

    private fun RoundEntity.toScored(config: ScoringConfig): ScoredRound = ScoredRound(
        id = id,
        sequence = sequence,
        revision = revision,
        round = payload,
        score = scoreOf(payload, config),
        deletedAt = deletedAt,
        pendingSync = pendingSync,
    )

    private fun RoundEntity.toEnvelope(config: ScoringConfig?): RoundEnvelope = RoundEnvelope(
        round = payload,
        sessionId = sessionId,
        sequence = sequence,
        revision = revision,
        deletedAt = deletedAt,
        // Nur Pruefsumme. Der Server verwirft sie und rechnet neu; weicht sie
        // ab, ist das der Fruehwarner fuer Versionsdrift zwischen APK und
        // Server. Ohne bekannte Session bleibt sie leer, statt mit den falschen
        // Hausregeln einen Fehlalarm auszuloesen.
        clientHalfPoints = config?.let { scoreOf(payload, it).halfPoints } ?: emptyMap(),
    )

    /**
     * Rechnet eine Runde ab und faengt dabei ab, was [Scoring.score] als
     * Ausnahme wirft. Eine unlesbare Runde - etwa aus einer neueren APK mit
     * einer Spielart, die dieser Stand nicht kennt - darf die Rundenliste nicht
     * zum Absturz bringen; sie zaehlt dann eben null.
     */
    private fun scoreOf(round: Round, config: ScoringConfig): RoundScore {
        val errors = Scoring.validate(round)
        if (errors.isEmpty()) return Scoring.score(round, config)
        Log.w(TAG, "Runde ${round.id} ist nicht abrechenbar: ${errors.joinToString("; ")}")
        return RoundScore(round.seats.associateWith { 0 }, 0)
    }

    private suspend fun List<SessionEntity>.toEnvelopes(): List<SessionEnvelope> {
        if (isEmpty()) return emptyList()
        val seats = seatDao.bySessions(map { it.id }).groupBy { it.sessionId }
        return map { entity ->
            SessionEnvelope(
                session = entity.toModel(seats[entity.id].orEmpty()),
                revision = entity.revision,
                deletedAt = entity.deletedAt,
            )
        }
    }
}

// --- Abbildung Entity <-> Modell ------------------------------------------

internal fun ClubEntity.toModel(players: List<PlayerEntity>): Club = Club(
    id = id,
    name = name,
    scoring = scoring,
    centsPerPoint = centsPerPoint,
    roster = players.map { it.toModel() },
)

internal fun PlayerEntity.toModel(): Player = Player(id, displayName, active)

internal fun SessionEntity.toModel(seats: List<SessionSeatEntity>): Session = Session(
    id = id,
    clubId = clubId,
    seatCount = seatCount,
    seats = seats.associate { it.seat to it.playerId },
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    scoring = scoring,
    scoringVersion = scoringVersion,
    centsPerPoint = centsPerPoint,
    dealerSeat = dealerSeat,
)

internal fun Session.toEntity(revision: Long, deletedAt: Instant?, pendingSync: Boolean): SessionEntity =
    SessionEntity(
        id = id,
        clubId = clubId,
        seatCount = seatCount,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        scoring = scoring,
        scoringVersion = scoringVersion,
        centsPerPoint = centsPerPoint,
        dealerSeat = dealerSeat,
        revision = revision,
        deletedAt = deletedAt,
        pendingSync = pendingSync,
    )

internal fun Session.toSeatEntities(): List<SessionSeatEntity> =
    seats.entries.sortedBy { it.key }.map { SessionSeatEntity(id, it.key, it.value) }
