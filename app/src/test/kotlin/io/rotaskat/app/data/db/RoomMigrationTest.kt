package io.rotaskat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Migrationstest ohne `room-testing` und ohne Instrumentierung.
 *
 * Der Kern ist derselbe wie beim MigrationTestHelper: aus dem exportierten
 * Schema einer alten Version wird eine echte SQLite-Datei gebaut, dann oeffnet
 * Room sie und laesst seine eigene Pruefung laufen. Sie schlaegt an, sobald das
 * kompilierte Schema und die exportierte JSON-Datei auseinanderlaufen - und
 * genau darauf steht jeder spaetere Migrationsschritt.
 *
 * Der Test braucht die Dateien unter `app/schemas`, die KSP beim Uebersetzen
 * schreibt. Fehlen sie, ist die `room.schemaLocation`-Einstellung verlorengegangen
 * und es gaebe ab dann keinen Migrationstest mehr - deshalb ist ihr Fehlen hier
 * ein Fehlschlag und kein uebersprungener Test.
 */
@RunWith(RobolectricTestRunner::class)
class RoomMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: RotaskatDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `Die Migrationskette deckt jeden Versionsschritt genau einmal ab`() {
        val steps = RotaskatDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until RotaskatDatabase.VERSION).map { it to it + 1 }

        // Faellt in dem Moment, in dem jemand VERSION hochsetzt, ohne die
        // Migration nachzureichen - und das ist der einzige Weg, wie ein Abend
        // ohne Netz verlorengehen kann: die App kaeme nicht mehr an ihre
        // Datenbank.
        assertEquals(expected, steps, "Die Migrationskette hat eine Luecke oder eine Dopplung")
    }

    @Test
    fun `Fuer jede Version liegt ein exportiertes Schema vor`() {
        for (version in 1..RotaskatDatabase.VERSION) {
            val file = schemaFile(version)
            assertTrue(file.isFile, "Kein exportiertes Schema unter ${file.absolutePath}")
            assertEquals(version, readSchema(version).database.version)
        }
    }

    @Test
    fun `Room akzeptiert eine Datenbank, die aus dem exportierten Schema gebaut wurde`() {
        val schema = readSchema(RotaskatDatabase.VERSION)
        createDatabaseFromSchema(schema)

        // Passt der Identitaets-Hash nicht, wirft Room beim Oeffnen - dann ist
        // das exportierte Schema nicht mehr das, was der Code erwartet.
        val opened = openWithRoom().openHelper.writableDatabase
        opened.query("SELECT COUNT(*) FROM round").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `Die Rundentabelle bleibt Nutzlast plus indizierte Spalten`() {
        val round = readSchema(RotaskatDatabase.VERSION).database.entities.single { it.tableName == "round" }

        // Die tragende Entscheidung der Datenschicht: Declaration ist ein sealed
        // interface mit vier Auspraegungen, Modifiers hat sechs Schalter.
        // Relational ausgefaltet zwaenge jede neue Hausregel zu einer Room- UND
        // einer Flyway-Migration. Waechst diese Spaltenliste, ist genau das
        // gerade passiert.
        assertEquals(
            setOf("id", "sessionId", "sequence", "revision", "deletedAt", "pendingSync", "payload"),
            round.fields.map { it.columnName }.toSet(),
        )
    }

    @Test
    fun `Der Tombstone ist nullbar und die Nutzlast nicht`() {
        val round = readSchema(RotaskatDatabase.VERSION).database.entities.single { it.tableName == "round" }
        assertEquals(false, round.fields.single { it.columnName == "deletedAt" }.notNull)
        assertEquals(true, round.fields.single { it.columnName == "payload" }.notNull)
    }

    // --- Werkzeug ---------------------------------------------------------

    private fun openWithRoom(): RotaskatDatabase =
        RotaskatDatabase.open(context).also { database = it }

    /**
     * Baut die Datei so auf, wie sie eine App mit dieser Schemaversion
     * hinterlassen haette: Tabellen, Indizes und der Identitaets-Hash in
     * `room_master_table`.
     */
    private fun createDatabaseFromSchema(schema: SchemaFile) {
        val file = databaseFile()
        file.parentFile?.mkdirs()
        SQLiteDatabase.deleteDatabase(file)

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            raw.beginTransaction()
            for (entity in schema.database.entities) {
                raw.execSQL(entity.createSql.replace("\${TABLE_NAME}", entity.tableName))
                entity.indices.forEach { index ->
                    raw.execSQL(index.createSql.replace("\${TABLE_NAME}", entity.tableName))
                }
            }
            raw.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            raw.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.database.identityHash),
            )
            raw.version = schema.database.version
            raw.setTransactionSuccessful()
        } finally {
            raw.endTransaction()
            raw.close()
        }
    }

    private fun databaseFile(): File = context.getDatabasePath(RotaskatDatabase.FILE_NAME)

    private fun schemaFile(version: Int): File =
        File("schemas/${RotaskatDatabase::class.qualifiedName}/$version.json")

    private fun readSchema(version: Int): SchemaFile {
        val file = schemaFile(version)
        assertTrue(file.isFile, "Kein exportiertes Schema unter ${file.absolutePath}")
        return assertNotNull(json.decodeFromString(SchemaFile.serializer(), file.readText()))
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Nur die Felder, die dieser Test braucht - das Schemaformat von Room hat
    // deutlich mehr davon, und ignoreUnknownKeys haelt sie draussen.
    @Serializable
    data class SchemaFile(val database: SchemaDatabase)

    @Serializable
    data class SchemaDatabase(
        val version: Int,
        val identityHash: String,
        val entities: List<SchemaEntity>,
    )

    @Serializable
    data class SchemaEntity(
        val tableName: String,
        val createSql: String,
        val fields: List<SchemaField>,
        val indices: List<SchemaIndex> = emptyList(),
    )

    // Room laesst notNull bei nullbaren Spalten weg, statt false zu schreiben.
    @Serializable
    data class SchemaField(val columnName: String, val notNull: Boolean = false)

    @Serializable
    data class SchemaIndex(val name: String, val createSql: String)
}
