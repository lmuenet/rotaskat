package io.rotaskat.app.data.sync

import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.api.SyncConflict
import kotlinx.datetime.Instant

/**
 * Die Sicht des Sync auf die lokale Datenbank.
 *
 * Getrennt von [io.rotaskat.app.data.RotaskatRepository], weil die Oberflaeche
 * von Revisionen, Cursorn und Tombstones nichts wissen muss - und weil ein
 * Bildschirm, der `resolveConflicts` aufrufen koennte, ein Fehler waere.
 */
interface SyncStore {

    suspend fun pendingRounds(limit: Int): List<RoundEnvelope>

    suspend fun pendingSessions(limit: Int): List<SessionEnvelope>

    /**
     * Die Sessions zu bereits synchronisierten Runden. Der Server braucht zu
     * jeder Runde ihre Session, weil dort die eingefrorene ScoringConfig haengt,
     * mit der er nachrechnet. Sie noch einmal mitzuschicken kostet nichts: bei
     * gleichem Inhalt antwortet er UNCHANGED.
     */
    suspend fun sessionEnvelopes(ids: Set<String>): List<SessionEnvelope>

    /**
     * Streicht die Sync-Markierung der tatsaechlich uebertragenen Staende.
     * Datensaetze, die waehrend des Sync am Tisch korrigiert wurden, behalten
     * sie - sonst verschwaende die Antwort auf den alten Push die Korrektur.
     */
    suspend fun clearPending(sessions: List<SessionEnvelope>, rounds: List<RoundEnvelope>)

    /** Hebt die lokale Revision ueber den Serverstand, siehe [SyncEngine]. */
    suspend fun resolveConflicts(conflicts: List<SyncConflict>)

    suspend fun applyPull(sessions: List<SessionEnvelope>, rounds: List<RoundEnvelope>)

    suspend fun cursor(clubId: String): Long

    suspend fun saveCursor(clubId: String, cursor: Long, at: Instant)
}
