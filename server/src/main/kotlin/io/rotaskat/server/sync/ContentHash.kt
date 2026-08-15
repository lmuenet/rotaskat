package io.rotaskat.server.sync

import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

/**
 * Der Inhalts-Hash einer Runde bzw. Session.
 *
 * Er beantwortet genau eine Frage: kam dieselbe Revision mit demselben Inhalt
 * noch einmal an (dann ist es die harmlose Wiederholung eines abgebrochenen
 * Sync) oder mit anderem Inhalt (dann haben zwei Geraete unabhaengig dieselbe
 * Revisionsnummer vergeben, und stilles Ueberschreiben wuerde eine Korrektur
 * verschlucken).
 *
 * Bewusst NICHT gehasht werden die vom Client mitgeschickten Punkte: die sind
 * nur Pruefsumme, der Server rechnet sie ohnehin neu. Eine alte APK, die
 * anders rechnet, wuerde sonst bei jedem Sync einen Konflikt ausloesen, obwohl
 * die Spielfakten identisch sind.
 */
object ContentHash {

    @Serializable
    private data class RoundPayload(
        val round: Round,
        val sessionId: String,
        val sequence: Int,
        /**
         * Nur das Ob, nicht das Wann. Der Loeschzeitpunkt kommt von der
         * Handyuhr; zwei Geraete mit derselben Korrektur sollen sich nicht an
         * ein paar Millisekunden Uhrendrift aufhaengen.
         */
        val deleted: Boolean,
    )

    @Serializable
    private data class SessionPayload(
        val session: Session,
        val deleted: Boolean,
    )

    fun of(round: Round, sessionId: String, sequence: Int, deleted: Boolean): String =
        sha256(RotaskatJson.encodeToString(RoundPayload(round, sessionId, sequence, deleted)))

    fun of(session: Session, deleted: Boolean): String =
        sha256(RotaskatJson.encodeToString(SessionPayload(session, deleted)))

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}
