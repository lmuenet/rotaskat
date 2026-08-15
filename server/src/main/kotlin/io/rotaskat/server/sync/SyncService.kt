package io.rotaskat.server.sync

import io.rotaskat.server.badRequest
import io.rotaskat.server.repo.RotaskatRepository
import io.rotaskat.server.repo.RoundWrite
import io.rotaskat.server.repo.SessionWrite
import io.rotaskat.server.repo.StoredSession
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.ScoreMismatch
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.api.SyncItemResult
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import org.slf4j.LoggerFactory

/**
 * Nimmt einen Sync-Batch entgegen, rechnet ihn nach und reicht ihn an die
 * Repository-Schicht weiter.
 *
 * Der Server VERWIRFT die vom Client gelieferten halben Punkte und rechnet sie
 * mit :shared neu, unter der fuer die Session eingefrorenen ScoringConfig.
 * Das ist kein Misstrauen gegen den Client, sondern die einzige Stelle, an der
 * eine Versionsdrift zwischen einer alten APK und dem Server ueberhaupt
 * auffallen kann - die APK wird ohne erzwungenen Update-Pfad verteilt.
 */
class SyncService(private val repository: RotaskatRepository) {

    private val log = LoggerFactory.getLogger(SyncService::class.java)

    /** Der Batch war widerspruchsfrei bzw. wurde wegen Konflikten verworfen. */
    sealed interface PushOutcome {
        val response: SyncPushResponse

        data class Applied(override val response: SyncPushResponse) : PushOutcome

        /** 409: gleiche Revision, anderer Inhalt. Es wurde nichts geschrieben. */
        data class Conflicted(override val response: SyncPushResponse) : PushOutcome
    }

    fun push(clubId: String, request: SyncPushRequest): PushOutcome {
        val roster = if (request.sessions.isEmpty()) emptySet() else repository.clubPlayerIds(clubId)
        val sessionWrites = request.sessions.map { it.toWrite(clubId, roster) }

        // Der gespeicherte Stand ist die Grundlage; eine Session aus dem Batch
        // zaehlt nur, wenn sie ihn ueberholt. Ein Abend wird zusammen mit
        // seinen ersten Runden hochgeladen und steht dann noch gar nicht in der
        // Datenbank - deshalb gibt es den Batch-Weg ueberhaupt.
        val batchSessions = sessionWrites.associateBy { it.session.id }
        val referenced = request.rounds.mapTo(mutableSetOf()) { it.sessionId } + batchSessions.keys
        val storedSessions = repository.findSessions(clubId, referenced)

        val mismatches = mutableListOf<ScoreMismatch>()
        val roundWrites = request.rounds.map { envelope ->
            val config = resolveConfig(envelope, batchSessions, storedSessions)
            envelope.toWrite(config, mismatches)
        }

        requireDistinctIds(sessionWrites.map { it.session.id }, "Session")
        requireDistinctIds(roundWrites.map { it.round.id }, "Runde")

        val outcome = repository.applySync(clubId, sessionWrites, roundWrites)
        val response = SyncPushResponse(
            cursor = outcome.cursor,
            sessions = outcome.sessions.map { SyncItemResult(it.id, it.result, it.updatedSeq) },
            rounds = outcome.rounds.map { SyncItemResult(it.id, it.result, it.updatedSeq) },
            mismatches = if (outcome.conflicts.isEmpty()) mismatches else emptyList(),
            conflicts = outcome.conflicts,
        )
        return if (outcome.conflicts.isEmpty()) PushOutcome.Applied(response) else PushOutcome.Conflicted(response)
    }

    /**
     * Die Hausregeln, mit denen die Runde nachgerechnet wird.
     *
     * Sie kommen aus dem Stand, der nach der Sync-Entscheidung tatsaechlich
     * gilt. Vorher hatte die Session AUS DEM BATCH bedingungslos Vorrang - ein
     * Geraet konnte damit eine veraltete Nutzlast schicken, die als STALE
     * verworfen wurde, deren ScoringConfig die Runde aber trotzdem abrechnete.
     * Das haette gleich zwei Zusagen ausgehebelt: die pro Session eingefrorene
     * Konfiguration und das Nachrechnen statt Vertrauen.
     */
    private fun resolveConfig(
        envelope: RoundEnvelope,
        batch: Map<String, SessionWrite>,
        stored: Map<String, StoredSession>,
    ): ScoringConfig {
        val fromBatch = batch[envelope.sessionId]
        val fromStore = stored[envelope.sessionId]
        val session = when {
            fromStore == null -> fromBatch?.session
            fromBatch != null && fromBatch.revision > fromStore.revision -> fromBatch.session
            else -> fromStore.session
        } ?: badRequest("Unbekannte Session ${envelope.sessionId} fuer Runde ${envelope.round.id}")

        if (session.seatCount != envelope.round.seatCount) {
            badRequest(
                "Runde ${envelope.round.id} hat seatCount ${envelope.round.seatCount}, " +
                    "die Session ${session.id} aber ${session.seatCount}"
            )
        }
        return session.scoring
    }

    private fun SessionEnvelope.toWrite(clubId: String, roster: Set<String>): SessionWrite {
        if (revision < 1) badRequest("Revision der Session ${session.id} muss mindestens 1 sein, ist $revision")
        // Ein Geraetetoken darf ausschliesslich in seinen eigenen Verein
        // schreiben. Die Pruefung steht hier und nicht erst im SQL, weil ein
        // Fremdschluesselfehler dem Client nichts sagen wuerde.
        if (session.clubId != clubId) {
            badRequest("Session ${session.id} gehoert zu Verein ${session.clubId}, das Geraet zu $clubId")
        }
        if (session.seatCount !in 3..4) {
            badRequest("seatCount der Session ${session.id} muss 3 oder 4 sein, ist ${session.seatCount}")
        }
        if (session.dealerSeat !in 0 until session.seatCount) {
            badRequest("dealerSeat ${session.dealerSeat} liegt ausserhalb der Session ${session.id}")
        }
        val badSeats = session.seats.keys.filter { it !in 0 until session.seatCount }
        if (badSeats.isNotEmpty()) {
            badRequest("Sitzplaetze ${badSeats.sorted()} liegen ausserhalb der Session ${session.id}")
        }
        // Fester Vereinskader, keine Gaeste: wer nicht im Kader steht, kann
        // auch nicht auf einem Stuhl sitzen.
        val strangers = session.seats.values.filter { it !in roster }
        if (strangers.isNotEmpty()) {
            badRequest("Spieler ${strangers.sorted()} gehoeren nicht zum Kader von $clubId")
        }
        return SessionWrite(
            session = session,
            revision = revision,
            contentHash = ContentHash.of(session, deletedAt != null),
            deletedAt = deletedAt,
        )
    }

    private fun RoundEnvelope.toWrite(
        config: ScoringConfig,
        mismatches: MutableList<ScoreMismatch>,
    ): RoundWrite {
        if (revision < 1) badRequest("Revision der Runde ${round.id} muss mindestens 1 sein, ist $revision")
        if (sequence < 0) badRequest("sequence der Runde ${round.id} darf nicht negativ sein, ist $sequence")

        val errors = Scoring.validate(round)
        if (errors.isNotEmpty()) badRequest("Ungueltige Runde ${round.id}", errors)

        val score = Scoring.score(round, config)
        val drift = compare(round.id, clientHalfPoints, score.halfPoints)
        mismatches += drift

        return RoundWrite(
            round = round,
            sessionId = sessionId,
            sequence = sequence,
            revision = revision,
            contentHash = ContentHash.of(round, sessionId, sequence, deletedAt != null),
            deletedAt = deletedAt,
            halfPoints = score.halfPoints,
            gameValue = score.gameValue,
            clientHalfPoints = clientHalfPoints,
            scoreMismatch = drift.isNotEmpty(),
        )
    }

    /**
     * Vergleicht Pruefsumme und Nachrechnung. Ein leeres Client-Ergebnis ist
     * kein Widerspruch, sondern ein Client, der die Pruefsumme gar nicht
     * mitschickt.
     */
    private fun compare(
        roundId: String,
        client: Map<Int, Int>,
        server: Map<Int, Int>,
    ): List<ScoreMismatch> {
        if (client.isEmpty()) return emptyList()

        val seats = (client.keys + server.keys).sorted()
        val drift = seats.mapNotNull { seat ->
            val clientValue = client[seat] ?: 0
            val serverValue = server[seat] ?: 0
            if (clientValue == serverValue) null else ScoreMismatch(roundId, seat, clientValue, serverValue)
        }
        if (drift.isNotEmpty()) {
            log.warn(
                "Nachrechnung weicht ab fuer Runde {}: Client={} Server={}. Gespeichert wird der Serverwert.",
                roundId,
                seats.associateWith { client[it] ?: 0 },
                seats.associateWith { server[it] ?: 0 },
            )
        }
        return drift
    }

    private fun requireDistinctIds(ids: List<String>, kind: String) {
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            badRequest("$kind mehrfach im selben Batch: ${duplicates.sorted().joinToString(", ")}")
        }
    }
}
