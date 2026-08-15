package io.rotaskat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClubDao {

    @Upsert
    suspend fun upsert(club: ClubEntity)

    @Query("SELECT * FROM club LIMIT 1")
    fun observe(): Flow<ClubEntity?>

    @Query("SELECT * FROM club LIMIT 1")
    suspend fun current(): ClubEntity?

    @Query("SELECT * FROM club WHERE id = :id")
    suspend fun find(id: String): ClubEntity?

    /**
     * Raeumt den lokalen Verein weg, nachdem seine Abende an den echten Verein
     * uebergeben wurden. Der Kader verschwindet per CASCADE mit; die Abende
     * nicht, denn `session` haengt bewusst an keinem Fremdschluessel.
     */
    @Query("DELETE FROM club WHERE id <> :keep")
    suspend fun deleteOthers(keep: String)
}

@Dao
interface PlayerDao {

    @Upsert
    suspend fun upsertAll(players: List<PlayerEntity>)

    @Query("SELECT * FROM player WHERE clubId = :clubId ORDER BY displayName")
    fun observeRoster(clubId: String): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM player ORDER BY displayName")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM player WHERE clubId = :clubId ORDER BY displayName")
    suspend fun roster(clubId: String): List<PlayerEntity>

    /**
     * Raeumt Mitglieder weg, die der Server nicht mehr kennt. Ein Spieler
     * verschwindet nur ueber diesen Weg; die App selbst loescht nie jemanden aus
     * dem Kader.
     */
    @Query("DELETE FROM player WHERE clubId = :clubId AND id NOT IN (:keep)")
    suspend fun deleteMissing(clubId: String, keep: List<String>)
}

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Upsert
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun find(id: String): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    /**
     * Der laufende Abend. Es gibt hoechstens einen: "Abend beenden" schliesst
     * ihn, und ein neuer wird erst danach angelegt.
     */
    @Query(
        "SELECT * FROM session WHERE status = 'OPEN' AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1"
    )
    fun observeOpen(): Flow<SessionEntity?>

    @Query(
        "SELECT * FROM session WHERE status = 'OPEN' AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC, id DESC LIMIT 1"
    )
    suspend fun openSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE deletedAt IS NULL ORDER BY startedAt DESC, id DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE pendingSync = 1 ORDER BY startedAt, id LIMIT :limit")
    suspend fun pending(limit: Int): List<SessionEntity>

    @Query("SELECT * FROM session WHERE id IN (:ids)")
    suspend fun findAll(ids: List<String>): List<SessionEntity>

    /**
     * Alle Abende, auch geloeschte. Nur fuer die Uebergabe an einen Verein: die
     * wird ein einziges Mal ausgefuehrt und darf keinen Abend uebersehen, auch
     * keinen zurueckgenommenen.
     */
    @Query("SELECT * FROM session ORDER BY startedAt, id")
    suspend fun all(): List<SessionEntity>

    /**
     * Nur loeschen, solange die Revision unveraendert ist. Waehrend der Sync
     * lief, kann am Tisch bereits eine Korrektur entstanden sein - die darf die
     * Antwort auf den alten Push nicht wegwischen.
     */
    @Query("UPDATE session SET pendingSync = 0 WHERE id = :id AND revision = :revision")
    suspend fun clearPending(id: String, revision: Long)
}

@Dao
interface SessionSeatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(seats: List<SessionSeatEntity>)

    @Query("DELETE FROM session_seat WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("SELECT * FROM session_seat WHERE sessionId = :sessionId ORDER BY seat")
    suspend fun bySession(sessionId: String): List<SessionSeatEntity>

    @Query("SELECT * FROM session_seat WHERE sessionId = :sessionId ORDER BY seat")
    fun observeBySession(sessionId: String): Flow<List<SessionSeatEntity>>

    @Query("SELECT * FROM session_seat WHERE sessionId IN (:sessionIds) ORDER BY sessionId, seat")
    suspend fun bySessions(sessionIds: List<String>): List<SessionSeatEntity>
}

@Dao
interface RoundDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(round: RoundEntity)

    @Upsert
    suspend fun upsert(round: RoundEntity)

    @Query("SELECT * FROM round WHERE id = :id")
    suspend fun find(id: String): RoundEntity?

    /** Auch Tombstones: die Anzeige entscheidet, was sie davon zeigt. */
    @Query("SELECT * FROM round WHERE sessionId = :sessionId ORDER BY sequence, id")
    fun observeBySession(sessionId: String): Flow<List<RoundEntity>>

    @Query("SELECT * FROM round WHERE sessionId = :sessionId ORDER BY sequence, id")
    suspend fun bySession(sessionId: String): List<RoundEntity>

    /**
     * Alle Runden aller Abende, fuer Rangliste und Statistik.
     *
     * Bewusst ohne Aggregation in SQL: die Punkte stehen nicht in der Datenbank,
     * sie werden aus den Rohdaten mit der eingefrorenen ScoringConfig des
     * jeweiligen Abends gerechnet. Ein SUM() ueber eine Spalte, die es nicht
     * gibt, waere die Abkuerzung, die genau diese Entscheidung aushebelt.
     */
    @Query("SELECT * FROM round ORDER BY sessionId, sequence, id")
    fun observeAll(): Flow<List<RoundEntity>>

    /**
     * Die naechste freie Position. Zaehlt ueber Tombstones hinweg weiter, sonst
     * bekaeme eine neue Runde die Nummer einer geloeschten und beide waeren im
     * Verlauf nicht mehr auseinanderzuhalten.
     */
    @Query("SELECT COALESCE(MAX(sequence), -1) + 1 FROM round WHERE sessionId = :sessionId")
    suspend fun nextSequence(sessionId: String): Int

    @Query("SELECT * FROM round WHERE pendingSync = 1 ORDER BY sessionId, sequence, id LIMIT :limit")
    suspend fun pending(limit: Int): List<RoundEntity>

    @Query("SELECT COUNT(*) FROM round WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    /** Siehe [SessionDao.clearPending]: die Revision ist die Absicherung. */
    @Query("UPDATE round SET pendingSync = 0 WHERE id = :id AND revision = :revision")
    suspend fun clearPending(id: String, revision: Long)

    @Query("SELECT COUNT(*) FROM round WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun countLive(sessionId: String): Int

    /**
     * Stellt alle Runden auf "wartet auf den Server". Gedacht fuer den Uebergang
     * vom lokalen Betrieb in einen Verein: bis dahin war der Sync abgeschaltet,
     * also hat keine Runde je einen Server gesehen.
     */
    @Query("UPDATE round SET pendingSync = 1")
    suspend fun markAllPending()
}

@Dao
interface SyncStateDao {

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE clubId = :clubId")
    suspend fun find(clubId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE clubId = :clubId")
    fun observe(clubId: String): Flow<SyncStateEntity?>
}
