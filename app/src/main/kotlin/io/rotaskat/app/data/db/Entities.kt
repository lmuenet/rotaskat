package io.rotaskat.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.SessionStatus
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.datetime.Instant

/**
 * Der Verein mit seinen Hausregeln.
 *
 * Auf dem Geraet steht immer genau ein Verein - das Geraetetoken haengt an
 * einem. Die Tabelle ist trotzdem eine Tabelle und keine Preference, weil
 * `scoring` und `centsPerPoint` beim Anlegen einer Session eingefroren werden
 * und deshalb transaktional zusammen mit der Session gelesen werden muessen.
 */
@Entity(tableName = "club")
data class ClubEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scoring: ScoringConfig,
    val centsPerPoint: Int,
)

/** Ein Mitglied des Kaders. Gastspieler gibt es nicht, siehe SCOPE.md. */
@Entity(
    tableName = "player",
    foreignKeys = [
        ForeignKey(
            entity = ClubEntity::class,
            parentColumns = ["id"],
            childColumns = ["clubId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("clubId")],
)
data class PlayerEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val displayName: String,
    val active: Boolean,
)

/**
 * Ein Spielabend.
 *
 * [scoring], [scoringVersion] und [centsPerPoint] sind Kopien des
 * Vereinsstands zum Anpfiff, keine Verweise. Genau wie auf dem Server: sonst
 * saehe der Spieler am Tisch andere Zahlen als spaeter in der Rangliste.
 *
 * [revision], [deletedAt] und [pendingSync] sind Transporteigenschaften. Sie
 * stehen hier und nicht im geteilten [io.rotaskat.shared.model.Session], weil
 * sie keine Spielfakten sind.
 */
@Entity(
    tableName = "session",
    indices = [
        Index("clubId"),
        Index("pendingSync"),
        Index("status"),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val clubId: String,
    val seatCount: Int,
    val status: SessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val scoring: ScoringConfig,
    val scoringVersion: Int,
    val centsPerPoint: Int,
    /** Geber der naechsten Runde. Am Vierertisch zugleich der Aussetzende. */
    val dealerSeat: Int,
    val revision: Long,
    val deletedAt: Instant?,
    val pendingSync: Boolean,
)

/**
 * Die Sitzordnung eines Abends: Platz -> Spieler.
 *
 * Als eigene Tabelle und nicht als Nutzlast, im Gegensatz zur Runde. Der Grund
 * ist die Form: die Sitzordnung sind drei bis vier feste Paare, sie aendert
 * ihre Struktur nie und wird nach Spieler abgefragt. Eine Runde dagegen ist ein
 * Summentyp mit Varianten, der bei jeder Hausregel waechst.
 */
@Entity(
    tableName = "session_seat",
    primaryKeys = ["sessionId", "seat"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId")],
)
data class SessionSeatEntity(
    val sessionId: String,
    val seat: Int,
    val playerId: String,
)

/**
 * Eine gespielte Runde: JSON-Nutzlast plus die wenigen Spalten, nach denen
 * tatsaechlich gefiltert und sortiert wird.
 *
 * Was NICHT hier steht, sind die Punkte. Sie sind abgeleitet und werden beim
 * Lesen mit der fuer die Session eingefrorenen ScoringConfig neu gerechnet -
 * dieselbe Regel wie auf dem Server, aus demselben Grund: aendert sich eine
 * Hausregel, wird die Historie neu gerechnet statt unrettbar zu sein.
 *
 * Bewusst KEIN Fremdschluessel auf `session`: der Delta-Pull kann eine Runde
 * vor ihrer Session ausliefern, wenn die Seitengrenze dazwischen faellt. Ein
 * Constraint-Fehler wuerde den Sync dann dauerhaft blockieren, und ein
 * blockierter Sync ist schlimmer als eine kurzzeitig heimatlose Runde.
 */
@Entity(
    tableName = "round",
    indices = [
        // Nicht unique, und auf dem Server seit V3 auch nicht mehr: die
        // Position vergibt der Client aus lokalem Wissen, nach einem
        // abgebrochenen Delta-Pull kollidiert sie zwangslaeufig. Sortiert wird
        // ueber (sequence, id), und weil die Id eine zeitsortierte UUIDv7 ist,
        // ist auch ein Gleichstand eindeutig geordnet.
        Index(value = ["sessionId", "sequence"]),
        Index(value = ["pendingSync"]),
    ],
)
data class RoundEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** Position innerhalb des Abends, 0-basiert. */
    val sequence: Int,
    /** Wird bei jeder Korrektur derselben Runde hochgezaehlt. */
    val revision: Long,
    /** Tombstone. Physisch geloescht wird nie - der Sync brauchte die Runde sonst zurueck. */
    val deletedAt: Instant?,
    val pendingSync: Boolean,
    val payload: Round,
)

/**
 * Der Stand des Delta-Pull.
 *
 * Ein serverseitiger Sequenzzaehler, kein Zeitstempel: Handyuhren laufen
 * auseinander, ein Zeitstempel wuerde Runden ueberspringen. Der Cursor liegt in
 * Room und nicht im DataStore, damit er transaktional zusammen mit den Zeilen
 * fortgeschrieben wird, die er beschreibt.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val clubId: String,
    val cursor: Long,
    val lastSyncAt: Instant?,
)
