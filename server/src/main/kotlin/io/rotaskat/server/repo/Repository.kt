package io.rotaskat.server.repo

import io.rotaskat.shared.api.LeaderboardEntry
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncResult
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import kotlinx.datetime.Instant

/**
 * Wer hinter einem Bearer-Token steckt. Bewusst flach: der Verein haengt am
 * Spieler, und jeder Endpunkt filtert ueber [clubId], damit ein Token nie
 * Daten eines fremden Vereins sieht.
 */
data class DeviceIdentity(
    val deviceId: String,
    val playerId: String,
    val clubId: String,
)

/**
 * Eine Runde so, wie sie gespeichert ist: die Rohdaten plus die
 * Transporteigenschaften und die SERVERSEITIG nachgerechneten Punkte.
 */
data class StoredRound(
    val round: Round,
    val sessionId: String,
    val sequence: Int,
    val revision: Long,
    val contentHash: String,
    val deletedAt: Instant?,
    val updatedSeq: Long,
    val halfPoints: Map<Int, Int>,
    val gameValue: Int,
)

data class StoredSession(
    val session: Session,
    val revision: Long,
    val contentHash: String,
    val deletedAt: Instant?,
    val updatedSeq: Long,
)

/**
 * Ein Schreibauftrag fuer eine Runde. [halfPoints] und [gameValue] sind bereits
 * nachgerechnet - die Repository-Schicht speichert, sie rechnet nicht.
 */
data class RoundWrite(
    val round: Round,
    val sessionId: String,
    val sequence: Int,
    val revision: Long,
    val contentHash: String,
    val deletedAt: Instant?,
    val halfPoints: Map<Int, Int>,
    val gameValue: Int,
    val clientHalfPoints: Map<Int, Int>,
    val scoreMismatch: Boolean,
)

data class SessionWrite(
    val session: Session,
    val revision: Long,
    val contentHash: String,
    val deletedAt: Instant?,
)

data class SyncDecision(
    val id: String,
    val result: SyncResult,
    val updatedSeq: Long,
)

/** Der gespeicherte Stand eines Datensatzes, so wie ihn die Konfliktpruefung braucht. */
data class RowVersion(
    val revision: Long,
    val contentHash: String,
    val updatedSeq: Long,
    /** Tombstone. Gehoert in die Konfliktmeldung, siehe [SyncConflict.deleted]. */
    val deleted: Boolean = false,
)

/**
 * Die Entscheidungsregel des Sync, an genau einer Stelle.
 *
 * Sie steht hier und nicht in den Implementierungen, weil die Tests gegen die
 * In-Memory-Variante laufen: waere die Regel zweimal geschrieben, wuerden die
 * Tests eine Kopie pruefen und ueber die SQL-Variante nichts aussagen.
 *
 *  - unbekannt oder hoehere Revision  -> annehmen
 *  - niedrigere Revision             -> veraltet, stillschweigend verwerfen
 *  - gleiche Revision, gleicher Hash -> die harmlose Wiederholung
 *  - gleiche Revision, anderer Hash  -> Konflikt, nichts wird geschrieben
 */
fun decideSync(
    id: String,
    kind: String,
    revision: Long,
    contentHash: String,
    current: RowVersion?,
    conflicts: MutableList<SyncConflict>,
): SyncResult = when {
    current == null -> SyncResult.ACCEPTED
    revision > current.revision -> SyncResult.ACCEPTED
    revision < current.revision -> SyncResult.STALE
    contentHash == current.contentHash -> SyncResult.UNCHANGED
    else -> {
        conflicts += SyncConflict(
            id = id,
            kind = kind,
            revision = revision,
            serverContentHash = current.contentHash,
            clientContentHash = contentHash,
            deleted = current.deleted,
        )
        SyncResult.STALE
    }
}

/**
 * Ein Fenster des Delta-Pull: was ausgeliefert wird, und der Cursor, der genau
 * dazu passt.
 *
 * Beides gehoert zusammen und wird deshalb in EINEM Zug gelesen. Frueher las die
 * Route Runden, Sessions und den hoechsten Zaehlerstand in drei getrennten
 * Transaktionen; ein Push, der dazwischen commitete, hob den Cursor ueber eine
 * Zeile hinweg, die dem Client nie geliefert wurde - ohne Fehler, ohne Log und
 * ohne zweite Gelegenheit.
 */
data class PullPage(
    val rounds: List<StoredRound> = emptyList(),
    val sessions: List<StoredSession> = emptyList(),
    val cursor: Long = 0,
    val hasMore: Boolean = false,
)

/**
 * Schneidet ein Delta-Fenster zu, aus Listen EINES Snapshots.
 *
 * Zwei Regeln, beide aus demselben Grund - ein Cursor, der weiter ist als die
 * Auslieferung, macht Daten unrettbar:
 *
 *  - Er wandert nur so weit, wie BEIDE Listen vollstaendig hineinpassten. Sonst
 *    ueberspringt der naechste Pull genau das, was der Seitengrenze zum Opfer
 *    fiel.
 *  - Er kommt aus den ausgelieferten Zeilen selbst und niemals aus einer
 *    spaeteren Abfrage.
 */
fun pullPageOf(
    rounds: List<StoredRound>,
    sessions: List<StoredSession>,
    since: Long,
    limit: Int,
): PullPage {
    val roundsTruncated = rounds.size >= limit
    val sessionsTruncated = sessions.size >= limit
    val hasMore = roundsTruncated || sessionsTruncated

    val ceiling = if (hasMore) {
        minOf(
            if (roundsTruncated) rounds.last().updatedSeq else Long.MAX_VALUE,
            if (sessionsTruncated) sessions.last().updatedSeq else Long.MAX_VALUE,
        )
    } else {
        maxOf(
            rounds.lastOrNull()?.updatedSeq ?: 0L,
            sessions.lastOrNull()?.updatedSeq ?: 0L,
        )
    }

    return PullPage(
        rounds = rounds.filter { it.updatedSeq <= ceiling },
        sessions = sessions.filter { it.updatedSeq <= ceiling },
        cursor = maxOf(since, ceiling),
        hasMore = hasMore,
    )
}

/**
 * Ergebnis eines Batch-Upsert.
 *
 * Sind [conflicts] nicht leer, wurde NICHTS geschrieben: der Batch ist
 * atomar. Ein halb angenommener Batch waere fuer den Client nicht wiederholbar,
 * und Wiederholbarkeit ist der einzige Grund, warum der Sync ueberhaupt
 * idempotent gebaut ist.
 */
data class SyncOutcome(
    val sessions: List<SyncDecision> = emptyList(),
    val rounds: List<SyncDecision> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    val cursor: Long = 0,
)

/**
 * Der Datenzugriff, hinter einem Interface.
 *
 * Nicht aus Prinzipientreue, sondern weil die Tests sonst Docker braeuchten:
 * das Schema lebt von jsonb, plpgsql-Triggern und FILTER-Aggregaten, das
 * bildet keine eingebettete Datenbank nach. Die Endpunktlogik wird deshalb
 * gegen eine In-Memory-Variante getestet, die SQL-Variante bleibt duenn.
 */
interface RotaskatRepository {

    // --- Beitritt und Auth ---

    fun findClubByInviteCode(inviteCode: String): Club?

    fun findClub(clubId: String): Club?

    /**
     * Der Kader als reine Id-Menge. Der Sync prueft damit vor dem Schreiben,
     * ob eine Sitzordnung nur Vereinsmitglieder enthaelt - sonst schluege
     * spaeter der Fremdschluessel zu und aus einem Eingabefehler des Clients
     * wuerde ein 500er.
     */
    fun clubPlayerIds(clubId: String): Set<String>

    /** Legt ein Geraet an und liefert seine Id. Der Token liegt nur als Hash vor. */
    fun registerDevice(clubId: String, playerId: String, tokenHash: String, label: String?): String

    fun findDeviceByTokenHash(tokenHash: String): DeviceIdentity?

    fun touchDevice(deviceId: String, at: Instant)

    // --- Lesen ---

    fun findSession(clubId: String, sessionId: String): StoredSession?

    fun findSessions(clubId: String, sessionIds: Collection<String>): Map<String, StoredSession>

    fun roundsOfSession(clubId: String, sessionId: String): List<StoredRound>

    /**
     * Ein Delta-Fenster ab [cursor], hoechstens [limit] Zeilen je Art.
     *
     * Bewusst EINE Methode statt dreier Einzelabfragen: Runden, Sessions und
     * der Cursor muessen aus demselben Snapshot stammen, sonst wandert der
     * Cursor ueber nie ausgelieferte Zeilen hinweg. Siehe [pullPageOf].
     */
    fun pullSince(clubId: String, cursor: Long, limit: Int): PullPage

    /** [season] ist ein Kalenderjahr, null bedeutet all-time. */
    fun leaderboard(clubId: String, season: Int?): List<LeaderboardEntry>

    // --- Schreiben ---

    fun applySync(clubId: String, sessions: List<SessionWrite>, rounds: List<RoundWrite>): SyncOutcome
}
