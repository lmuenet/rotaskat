package io.rotaskat.app.data.id

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Runden-Id wird auf dem Client vergeben und ist damit der einzige
 * Schluessel, an dem der idempotente Sync haengt. Sie muss eindeutig,
 * zeitsortiert und ein formal gueltiges UUIDv7 sein.
 */
class Uuid7Test {

    @Test
    fun `Version und Variante entsprechen dem UUIDv7-Layout`() {
        repeat(200) {
            val id = Uuid7.next()
            assertTrue(Uuid7.isUuid7(id), "Keine UUIDv7: $id")
        }
    }

    @Test
    fun `Der eingebettete Zeitstempel ist der Erzeugungszeitpunkt`() {
        val before = System.currentTimeMillis()
        val id = Uuid7.next()
        val after = System.currentTimeMillis()

        val stamp = Uuid7.timestampMillis(id)
        assertTrue(stamp in before..after, "Zeitstempel $stamp liegt nicht in [$before, $after]")
    }

    @Test
    fun `Zehntausend Ids sind verschieden`() {
        val ids = List(10_000) { Uuid7.next() }
        assertEquals(ids.size, ids.toSet().size, "Es gab doppelte Ids")
    }

    @Test
    fun `Die lexikografische Reihenfolge ist die Erzeugungsreihenfolge`() {
        // Genau darum geht es: die Rundenliste sortiert nach (sequence, id), und
        // ein Gleichstand bei sequence soll trotzdem eindeutig geordnet sein.
        val ids = List(5_000) { Uuid7.next() }
        assertEquals(ids, ids.sorted(), "Die Ids sind nicht aufsteigend")
    }

    @Test
    fun `Eine rueckwaerts springende Uhr bricht die Sortierung nicht`() {
        val generator = Uuid7Generator()
        val start = 1_773_000_000_000L

        val vorwaerts = List(5) { generator.next(start + it) }
        // Zeitumstellung, NTP-Korrektur, Nutzer dreht am Datum: die Uhr geht um
        // eine Stunde zurueck. Neue Runden duerfen trotzdem nicht vor die alten
        // einsortiert werden.
        val rueckwaerts = List(5) { generator.next(start - 3_600_000L) }

        val alle = vorwaerts + rueckwaerts
        assertEquals(alle, alle.sorted(), "Nach dem Uhrensprung stimmt die Reihenfolge nicht mehr")
    }

    @Test
    fun `Mehr als 4096 Ids in derselben Millisekunde bleiben aufsteigend`() {
        // Der 12-Bit-Zaehler laeuft ueber; der Zeitstempel muss dann eine
        // Millisekunde vorziehen, statt die Sortierung kippen zu lassen.
        val generator = Uuid7Generator()
        val ids = List(5_000) { generator.next(1_773_000_000_000L) }

        assertEquals(ids, ids.sorted())
        assertEquals(ids.size, ids.toSet().size)
    }
}
