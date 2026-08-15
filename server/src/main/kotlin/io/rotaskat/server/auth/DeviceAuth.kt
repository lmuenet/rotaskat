package io.rotaskat.server.auth

import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.bearer
import io.rotaskat.server.repo.DeviceIdentity
import io.rotaskat.server.repo.RotaskatRepository
import kotlinx.datetime.Clock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Geraetetoken statt Passwort.
 *
 * In der Datenbank steht ausschliesslich der Hash. Ein Datenbankabzug reicht
 * damit nicht, um sich als ein Geraet auszugeben, und ein verlorenes Token
 * laesst sich nicht nachschlagen, sondern nur neu ausstellen - das ist hier
 * gewollt, der Wiederbeschaffungsweg ist ein neuer Einladungscode.
 */
object DeviceTokens {

    /** Name des Authentication-Providers. */
    const val PROVIDER = "device"

    private val random = SecureRandom()

    /**
     * 256 Bit aus [SecureRandom], base64url ohne Polsterung. Kein UUID: eine
     * UUIDv4 hat nur 122 Bit und ist als Geheimnis knapp bemessen.
     */
    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256, hexadezimal. Bewusst kein bcrypt/argon2: das Token ist ein
     * Zufallsgeheimnis mit voller Entropie, kein vom Menschen gewaehltes
     * Passwort. Gegen Brute Force schuetzt hier die Entropie, nicht die
     * Rechenzeit - und ein teurer Hash bei jedem einzelnen Request waere
     * Aufwand ohne Gegenwert.
     */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}

/**
 * Der Bearer-Provider. Alle Endpunkte ausser /health und /clubs/join liegen
 * dahinter - /clubs/join kann nicht dahinter liegen, weil er das Token
 * ueberhaupt erst ausstellt.
 */
fun AuthenticationConfig.deviceBearer(repository: RotaskatRepository) {
    bearer(DeviceTokens.PROVIDER) {
        realm = "rotaskat"
        authenticate { credential ->
            val identity: DeviceIdentity? =
                repository.findDeviceByTokenHash(DeviceTokens.hash(credential.token))
            if (identity != null) {
                repository.touchDevice(identity.deviceId, Clock.System.now())
            }
            identity
        }
    }
}
