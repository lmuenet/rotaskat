package io.rotaskat.app.data.id

import java.security.SecureRandom
import java.util.Random
import java.util.UUID

/**
 * Erzeuger zeitsortierter UUIDs nach dem Layout von UUID Version 7.
 *
 * Selbst implementiert und nicht als Abhaengigkeit hereingezogen: es sind
 * dreissig Zeilen, und jede zusaetzliche Bibliothek in der APK will spaeter
 * gepflegt werden. `java.util.UUID` dient nur dem Formatieren, dessen
 * `toString()` erzeugt die kanonische Schreibweise ohnehin korrekt.
 *
 * Warum ueberhaupt zeitsortiert: die Id wird auf dem Client vergeben, weil der
 * Sync ueber `ON CONFLICT (id)` idempotent ist und ein serverseitig vergebener
 * Primaerschluessel das unmoeglich machte. Eine rein zufaellige UUIDv4 waere
 * dafuer genauso gut - aber als Sortierschluessel waere sie wertlos. Mit v7
 * entspricht die Id-Reihenfolge der Eingabereihenfolge, und die Rundenliste
 * bekommt damit einen stabilen Zweitschluessel neben `sequence`.
 *
 * Layout (128 Bit):
 *   48 Bit  Unix-Zeit in Millisekunden
 *    4 Bit  Version (7)
 *   12 Bit  Zaehler innerhalb derselben Millisekunde
 *    2 Bit  Variante (RFC 4122)
 *   62 Bit  Zufall
 *
 * Eine eigene Klasse und nicht bloss ein `object`, weil der Monotonie-Zaehler
 * Zustand ist: ein Test mit gestellter Uhr wuerde ihn sonst fuer den Rest des
 * Laufs verstellen.
 */
internal class Uuid7Generator(private val random: Random = SecureRandom()) {

    private val lock = Any()
    private var lastMillis = 0L
    private var counter = 0

    /**
     * Eine gegenueber allen vorher erzeugten Ids streng aufsteigende Id.
     *
     * Eine rueckwaerts laufende Uhr - Zeitumstellung, NTP-Korrektur, ein
     * Nutzer, der am Datum dreht - darf die Sortierung nicht brechen. Deshalb
     * bleibt der zuletzt vergebene Zeitstempel stehen und nur der Zaehler dreht
     * weiter. Lieber eine Id, die minimal in der Zukunft liegt, als eine
     * Rundenliste, die nach einem Uhrensprung ihre Reihenfolge verliert.
     */
    fun next(nowMillis: Long): String {
        val millis: Long
        val count: Int
        synchronized(lock) {
            var candidate = maxOf(nowMillis, lastMillis)
            if (candidate == lastMillis) {
                counter++
                // Mehr als 4096 Ids in derselben Millisekunde: die Zeit eine
                // Millisekunde vorziehen, statt den Zaehler ueberlaufen und
                // damit die Sortierung kippen zu lassen.
                if (counter > MAX_COUNTER) {
                    candidate++
                    counter = 0
                }
            } else {
                counter = 0
            }
            lastMillis = candidate
            millis = candidate
            count = counter
        }

        val mostSignificant = ((millis and MILLIS_MASK) shl 16) or (7L shl 12) or count.toLong()
        // Oberste zwei Bits auf 10 setzen (RFC-4122-Variante), Rest Zufall.
        val leastSignificant = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or (1L shl 63)

        return UUID(mostSignificant, leastSignificant).toString()
    }

    companion object {
        /** Groesster Wert des 12-Bit-Zaehlers innerhalb einer Millisekunde. */
        private const val MAX_COUNTER = 0xFFF

        internal const val MILLIS_MASK = 0xFFFF_FFFF_FFFFL
    }
}

/** Der Erzeuger der App. */
object Uuid7 {

    private val generator = Uuid7Generator()

    fun next(): String = generator.next(System.currentTimeMillis())

    /** Der Zeitstempel, den eine v7-Id in sich traegt. */
    fun timestampMillis(uuid: String): Long =
        (UUID.fromString(uuid).mostSignificantBits ushr 16) and Uuid7Generator.MILLIS_MASK

    /** Prueft Version und Variante. Fuer Tests und Diagnose. */
    fun isUuid7(uuid: String): Boolean = runCatching {
        val parsed = UUID.fromString(uuid)
        parsed.version() == 7 && parsed.variant() == 2
    }.getOrDefault(false)
}
