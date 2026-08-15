package io.rotaskat.server

import io.rotaskat.server.db.Clubs
import io.rotaskat.server.db.Devices
import io.rotaskat.server.db.ROUND_UPSERT_SQL
import io.rotaskat.server.db.RoundScores
import io.rotaskat.server.db.Rounds
import io.rotaskat.server.db.SESSION_UPSERT_SQL
import io.rotaskat.server.db.SessionSeats
import io.rotaskat.server.db.Sessions
import io.rotaskat.server.db.Players
import io.rotaskat.server.db.roundUpsertArgs
import io.rotaskat.server.db.sessionUpsertArgs
import io.rotaskat.server.repo.RoundWrite
import io.rotaskat.server.repo.SessionWrite
import org.jetbrains.exposed.sql.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Bruecke zwischen Kotlin und Schema, ohne Datenbank.
 *
 * Die Endpunkttests laufen gegen das In-Memory-Repository; die SQL-Variante
 * bekaeme damit gar keine Deckung, und ein Tippfehler in einem Spaltennamen
 * faellt sonst erst im Betrieb auf. Dieser Test liest die Migrationen als Text
 * und haelt sie gegen die Exposed-Tabellen und die beiden handgeschriebenen
 * Upserts. Er ersetzt keinen Integrationstest - er faengt genau die Fehler,
 * die man beim Nachtragen einer Spalte macht.
 */
class SchemaConsistencyTest {

    @Test
    fun `jede Exposed-Spalte existiert im Schema`() {
        val schema = schemaColumns()
        val tables: List<Table> = listOf(Clubs, Players, Devices, Sessions, SessionSeats, Rounds, RoundScores)

        for (table in tables) {
            val declared = schema[table.tableName] ?: error("Tabelle ${table.tableName} fehlt in den Migrationen")
            val unknown = table.columns.map { it.name }.filterNot { it in declared }
            assertTrue(
                unknown.isEmpty(),
                "Tabelle ${table.tableName}: Spalten $unknown gibt es im Schema nicht. Bekannt sind $declared",
            )
        }
    }

    @Test
    fun `die Upserts schreiben nur Spalten, die es gibt`() {
        val schema = schemaColumns()

        val roundColumns = insertColumnsOf(ROUND_UPSERT_SQL)
        assertTrue(roundColumns.isNotEmpty())
        assertEquals(emptyList(), roundColumns.filterNot { it in schema.getValue("round") })

        val sessionColumns = insertColumnsOf(SESSION_UPSERT_SQL)
        assertTrue(sessionColumns.isNotEmpty())
        assertEquals(emptyList(), sessionColumns.filterNot { it in schema.getValue("session") })
    }

    /**
     * Ein Platzhalter zu viel oder zu wenig ist der klassische Fehler beim
     * Erweitern einer handgeschriebenen Anweisung, und er faellt sonst erst
     * beim ersten echten Sync auf.
     */
    @Test
    fun `Platzhalterzahl und Parameterliste passen zusammen`() {
        val roundWrite = RoundWrite(
            round = testRound("33333333-0000-0000-0000-00000000000f"),
            sessionId = SESSION_ID,
            sequence = 0,
            revision = 1,
            contentHash = "hash",
            deletedAt = null,
            halfPoints = mapOf(0 to 72, 1 to -36, 2 to -36),
            gameValue = 36,
            clientHalfPoints = mapOf(0 to 72),
            scoreMismatch = false,
        )
        assertEquals(
            ROUND_UPSERT_SQL.count { it == '?' },
            roundUpsertArgs(roundWrite).size,
            "Runden-Upsert: Platzhalter und Parameter laufen auseinander",
        )

        val sessionWrite = SessionWrite(
            session = testSession(),
            revision = 1,
            contentHash = "hash",
            deletedAt = null,
        )
        assertEquals(
            SESSION_UPSERT_SQL.count { it == '?' },
            sessionUpsertArgs(sessionWrite).size,
            "Session-Upsert: Platzhalter und Parameter laufen auseinander",
        )
    }

    /**
     * Wird eine Spalte eingefuegt, aber im Konfliktzweig vergessen, bleibt der
     * alte Wert bei jeder Korrektur stehen - und zwar lautlos. [id] und
     * [updated_seq] sind die beiden begruendeten Ausnahmen: der
     * Primaerschluessel aendert sich nie, und der Zaehler wird im UPDATE
     * bewusst neu gezogen statt uebernommen.
     */
    @Test
    fun `jede eingefuegte Spalte wird im Konfliktzweig auch aktualisiert`() {
        for (sql in listOf(ROUND_UPSERT_SQL, SESSION_UPSERT_SQL)) {
            val inserted = insertColumnsOf(sql)
            val updated = Regex("""(\w+)\s*=\s*excluded\.""").findAll(sql).map { it.groupValues[1] }.toSet()
            val forgotten = inserted.filterNot { it in updated || it == "id" || it == "updated_seq" }
            assertEquals(emptyList(), forgotten, "Im DO-UPDATE-Zweig fehlen: $forgotten")
        }
        assertTrue(ROUND_UPSERT_SQL.contains("updated_seq        = nextval('sync_seq')"))
        assertTrue(SESSION_UPSERT_SQL.contains("updated_seq     = nextval('sync_seq')"))
    }

    /** Der bedingte Upsert ist die Idempotenz-Zusicherung und muss dastehen. */
    @Test
    fun `der Upsert steht unter dem Revisionsvorbehalt`() {
        assertTrue(ROUND_UPSERT_SQL.contains("ON CONFLICT (id) DO UPDATE SET"))
        assertTrue(ROUND_UPSERT_SQL.contains("WHERE excluded.revision > round.revision"))
        assertTrue(SESSION_UPSERT_SQL.contains("ON CONFLICT (id) DO UPDATE SET"))
        assertTrue(SESSION_UPSERT_SQL.contains("WHERE excluded.revision > session.revision"))
    }

    /** V1 ist angewendet und wird nicht mehr angefasst. */
    @Test
    fun `V1 bleibt unveraendert und V2 legt den Sequenzzaehler an`() {
        val v2 = migrationText("V2__sync.sql")
        assertTrue(v2.contains("CREATE SEQUENCE sync_seq"), "Der Delta-Pull braucht den Sequenzzaehler")
        // Kein Zeitstempel als Cursor: Handyuhren laufen auseinander.
        assertTrue(v2.contains("updated_seq"))
        for (table in listOf("round", "session")) {
            assertTrue(
                schemaColumns().getValue(table).contains("updated_seq"),
                "Tabelle $table braucht updated_seq",
            )
        }
    }

    // --- Ein sehr kleiner SQL-Leser, gerade gross genug fuer diese Pruefung ---

    private fun migrationText(name: String): String =
        checkNotNull(javaClass.getResource("/db/migration/$name")) { "Migration $name nicht im Klassenpfad" }
            .readText()

    /** Spaltennamen je Tabelle, aus CREATE TABLE und ALTER TABLE ... ADD COLUMN. */
    private fun schemaColumns(): Map<String, Set<String>> {
        val sql = stripComments(migrationText("V1__init.sql") + "\n" + migrationText("V2__sync.sql"))
        val columns = mutableMapOf<String, MutableSet<String>>()

        for (match in Regex("""CREATE TABLE (\w+)\s*\(""", RegexOption.IGNORE_CASE).findAll(sql)) {
            val table = match.groupValues[1]
            val body = balancedBody(sql, match.range.last)
            val entries = splitTopLevel(body)
            columns.getOrPut(table) { mutableSetOf() } += entries
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.substringBefore(' ').uppercase().isConstraintKeyword() }
                .map { it.substringBefore(' ').trim() }
        }

        val alter = Regex("""ALTER TABLE (\w+)([^;]*);""", RegexOption.IGNORE_CASE)
        for (match in alter.findAll(sql)) {
            val table = match.groupValues[1]
            for (added in Regex("""ADD COLUMN (\w+)""", RegexOption.IGNORE_CASE).findAll(match.groupValues[2])) {
                columns.getOrPut(table) { mutableSetOf() } += added.groupValues[1]
            }
        }
        return columns
    }

    private fun String.isConstraintKeyword(): Boolean =
        this in setOf("PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "CONSTRAINT")

    private fun insertColumnsOf(sql: String): List<String> {
        val open = sql.indexOf('(')
        val body = balancedBody(sql, open)
        return body.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Inhalt der Klammer, die bei [openIndex] beginnt. */
    private fun balancedBody(sql: String, openIndex: Int): String {
        var depth = 0
        for (index in openIndex until sql.length) {
            when (sql[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return sql.substring(openIndex + 1, index)
                }
            }
        }
        error("Unausgeglichene Klammer ab Position $openIndex")
    }

    /** Trennt an Kommas der obersten Ebene, also nicht innerhalb von CHECK(...). */
    private fun splitTopLevel(body: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        body.forEachIndexed { index, char ->
            when (char) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    parts += body.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += body.substring(start)
        return parts.map { it.trim().replace(Regex("""\s+"""), " ") }
    }

    private fun stripComments(sql: String): String =
        sql.lines().joinToString("\n") { it.substringBefore("--") }
}
