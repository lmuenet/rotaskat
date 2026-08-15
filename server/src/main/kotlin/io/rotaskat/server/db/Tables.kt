package io.rotaskat.server.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject

/**
 * Eine jsonb-Spalte als roher Text.
 *
 * Bewusst kein exposed-json mit typisierter Serialisierung: die Nutzlast wird
 * mit derselben kotlinx-Json-Konfiguration geschrieben wie auf der Leitung
 * ([io.rotaskat.shared.api.RotaskatJson]), damit ein Feld nicht auf dem Weg in
 * die Datenbank eine andere Gestalt bekommt als im Inhalts-Hash. Der Umweg
 * ueber [PGobject] ist noetig, weil Postgres eine Zeichenkette nicht
 * stillschweigend nach jsonb castet.
 */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "jsonb"

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value ?: "null"
        is String -> value
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any = PGobject().apply {
        type = "jsonb"
        this.value = value
    }

    override fun nonNullValueToString(value: String): String =
        "'" + value.replace("'", "''") + "'::jsonb"
}

fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())

object Clubs : Table("club") {
    val id = uuid("id")
    val name = text("name")
    val inviteCode = text("invite_code")
    val scoringConfig = jsonb("scoring_config")
    val centsPerPoint = integer("cents_per_point")
    override val primaryKey = PrimaryKey(id)
}

object Players : Table("player") {
    val id = uuid("id")
    val clubId = uuid("club_id")
    val displayName = text("display_name")
    val isActive = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

object Devices : Table("device") {
    val id = uuid("id")
    val playerId = uuid("player_id")
    val tokenHash = text("token_hash")
    val label = text("label").nullable()
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("session") {
    val id = uuid("id")
    val clubId = uuid("club_id")
    val seatCount = short("seat_count")
    val status = text("status")
    val startedAt = timestampWithTimeZone("started_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    val scoringConfig = jsonb("scoring_config")
    val scoringVersion = integer("scoring_version")
    val centsPerPoint = integer("cents_per_point")
    val dealerSeat = short("dealer_seat")
    val revision = long("revision")
    val contentHash = text("content_hash")
    val updatedSeq = long("updated_seq")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SessionSeats : Table("session_seat") {
    val sessionId = uuid("session_id")
    val seat = short("seat")
    val playerId = uuid("player_id")
    override val primaryKey = PrimaryKey(sessionId, seat)
}

object Rounds : Table("round") {
    val id = uuid("id")
    val sessionId = uuid("session_id")
    val sequence = integer("sequence")
    val declarerSeat = short("declarer_seat").nullable()
    val sittingOutSeat = short("sitting_out_seat").nullable()
    val dealerSeat = short("dealer_seat")
    val declaration = jsonb("declaration")
    val won = bool("won")
    val contra = text("contra")
    val bid = integer("bid").nullable()
    val overbid = bool("overbid")
    val ramsch = jsonb("ramsch").nullable()
    val gameValue = integer("game_value")
    val revision = long("revision")
    val contentHash = text("content_hash")
    val updatedSeq = long("updated_seq")
    val clientHalfPoints = jsonb("client_half_points").nullable()
    val scoreMismatchAt = timestampWithTimeZone("score_mismatch_at").nullable()
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Die abgeleiteten Punkte, in HALBEN Punkten. Werden bei jedem angenommenen
 * Upsert komplett neu geschrieben - sie sind Rechenergebnis, kein Rohdatum.
 *
 * Die Spalte ist seit V3 BIGINT, die Rechnung bleibt Int: Scoring begrenzt
 * jede Runde auf den Int-Bereich und bricht sonst ab. Die breitere Spalte ist
 * die Zusage aus SCOPE.md und das Netz gegen direkte Schreibzugriffe - Exposed
 * schreibt einen Int hinein und liest ihn wieder heraus.
 */
object RoundScores : Table("round_score") {
    val roundId = uuid("round_id")
    val seat = short("seat")
    val halfPoints = integer("half_points")
    override val primaryKey = PrimaryKey(roundId, seat)
}
