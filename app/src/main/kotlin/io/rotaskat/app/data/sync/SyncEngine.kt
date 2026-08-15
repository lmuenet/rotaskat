package io.rotaskat.app.data.sync

import android.util.Log
import io.rotaskat.app.data.net.NotJoinedException
import io.rotaskat.app.data.net.PushResult
import io.rotaskat.app.data.net.RotaskatApi
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.shared.api.SyncPushRequest
import kotlinx.datetime.Clock

private const val TAG = "RotaskatSync"

/** Was ein Sync-Lauf bewegt hat. Landet im Log, nicht auf dem Bildschirm. */
data class SyncSummary(
    val pushedSessions: Int = 0,
    val pushedRounds: Int = 0,
    val pulledSessions: Int = 0,
    val pulledRounds: Int = 0,
    val conflicts: Int = 0,
    val mismatches: Int = 0,
    val cursor: Long = 0,
)

/**
 * Ein Sync-Durchlauf: erst alles Wartende hoch, dann das Delta herunter.
 *
 * Die Reihenfolge ist nicht beliebig. Zuerst zu pullen wuerde beim ersten Sync
 * eines uebernommenen Geraets die eigenen, noch nicht hochgeladenen Runden
 * gegen einen aelteren Serverstand pruefen - und der Push muesste hinterher
 * dieselben Zeilen noch einmal anfassen.
 *
 * Diese Klasse kennt weder WorkManager noch Android-Kontext. Sie wirft ihre
 * Fehler nach oben durch; das Wiederholen ist Sache des Workers, der dafuer
 * Backoff und Netz-Constraint mitbringt.
 */
class SyncEngine(
    private val store: SyncStore,
    private val api: RotaskatApi,
    private val settings: AppSettings,
    private val clock: Clock = Clock.System,
) {

    suspend fun syncOnce(): SyncSummary {
        val identity = settings.identityOrNull() ?: throw NotJoinedException()
        val pushed = push()
        val pulled = pull(identity.clubId)
        return pushed.copy(
            pulledSessions = pulled.pulledSessions,
            pulledRounds = pulled.pulledRounds,
            cursor = pulled.cursor,
        )
    }

    private suspend fun push(): SyncSummary {
        var sessions = 0
        var rounds = 0
        var conflicts = 0
        var mismatches = 0

        var batch = 0
        while (batch++ < MAX_BATCHES) {
            val pendingRounds = store.pendingRounds(ROUND_BATCH)
            val pendingSessions = store.pendingSessions(SESSION_BATCH)
            if (pendingRounds.isEmpty() && pendingSessions.isEmpty()) break

            // Zu jeder Runde muss ihre Session im selben Batch liegen oder dem
            // Server schon bekannt sein - dort haengt die eingefrorene
            // ScoringConfig, mit der er nachrechnet. Die bereits synchronisierten
            // Sessions kommen deshalb mit; bei gleichem Inhalt kostet das nur ein
            // UNCHANGED.
            val known = pendingSessions.mapTo(mutableSetOf()) { it.session.id }
            val extra = store.sessionEnvelopes(pendingRounds.map { it.sessionId }.toSet() - known)
            val outgoingSessions = pendingSessions + extra

            when (val result = api.push(SyncPushRequest(outgoingSessions, pendingRounds))) {
                is PushResult.Applied -> {
                    store.clearPending(outgoingSessions, pendingRounds)
                    sessions += pendingSessions.size
                    rounds += pendingRounds.size
                    mismatches += result.response.mismatches.size
                    if (result.response.mismatches.isNotEmpty()) {
                        // Der Fruehwarner fuer Versionsdrift zwischen APK und
                        // Server. Gespeichert hat der Server seinen eigenen Wert.
                        Log.w(TAG, "Server rechnet anders: ${result.response.mismatches}")
                    }
                }

                is PushResult.Conflicted -> {
                    conflicts += result.response.conflicts.size
                    // Der Server hat NICHTS geschrieben, auch die unstrittigen
                    // Runden des Batches nicht. Aufloesen und im naechsten
                    // Durchlauf denselben Batch erneut schicken.
                    store.resolveConflicts(result.response.conflicts)
                    if (result.response.conflicts.isEmpty()) {
                        // 409 ohne Konfliktliste waere nicht aufloesbar und
                        // wuerde hier endlos kreisen.
                        Log.w(TAG, "409 ohne Konfliktliste, Push abgebrochen")
                        break
                    }
                }
            }
        }
        if (batch > MAX_BATCHES) Log.w(TAG, "Push nach $MAX_BATCHES Durchlaeufen abgebrochen")

        return SyncSummary(
            pushedSessions = sessions,
            pushedRounds = rounds,
            conflicts = conflicts,
            mismatches = mismatches,
        )
    }

    private suspend fun pull(clubId: String): SyncSummary {
        var cursor = store.cursor(clubId)
        var sessions = 0
        var rounds = 0

        var page = 0
        while (page++ < MAX_PAGES) {
            val response = api.pull(cursor, PULL_LIMIT)
            store.applyPull(response.sessions, response.rounds)
            sessions += response.sessions.size
            rounds += response.rounds.size

            if (response.cursor > cursor) {
                cursor = response.cursor
                store.saveCursor(clubId, cursor, clock.now())
            } else if (response.hasMore) {
                // Der Cursor kommt nicht voran, obwohl der Server mehr
                // ankuendigt. Weiterlaufen hiesse dieselbe Seite fuer immer zu
                // holen; der naechste Lauf versucht es erneut.
                Log.w(TAG, "Delta-Pull steht bei Cursor $cursor, obwohl hasMore gesetzt ist")
                break
            }
            // Eine Seite darf leer sein und der Cursor trotzdem vorankommen -
            // deshalb wird auf hasMore geschleift und nicht auf eine nicht-leere
            // Rundenliste.
            if (!response.hasMore) break
        }
        if (page > MAX_PAGES) Log.w(TAG, "Delta-Pull nach $MAX_PAGES Seiten abgebrochen")

        return SyncSummary(pulledSessions = sessions, pulledRounds = rounds, cursor = cursor)
    }

    private companion object {
        const val ROUND_BATCH = 200
        const val SESSION_BATCH = 50
        const val PULL_LIMIT = 200

        /**
         * Obergrenzen gegen Endlosschleifen. Ein Abend hat selten mehr als
         * fuenfzig Runden; wer hier ansteht, hat ein anderes Problem als eine zu
         * kleine Grenze.
         */
        const val MAX_BATCHES = 50
        const val MAX_PAGES = 100
    }
}
