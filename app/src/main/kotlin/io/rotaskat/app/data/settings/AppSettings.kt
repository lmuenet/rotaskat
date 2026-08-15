package io.rotaskat.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.rotaskat.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Wer dieses Geraet ist. Wird beim Vereinsbeitritt genau einmal vergeben.
 */
data class DeviceIdentity(
    val deviceId: String,
    val playerId: String,
    val clubId: String,
)

/**
 * Wie die App betrieben wird.
 *
 * [LOCAL] ist kein Notbehelf, sondern ein vollwertiger Betriebsmodus: alles
 * ausser der vereinsweiten Rangliste funktioniert ohne Server, und man kann
 * einen ganzen Winter so spielen. Es gibt keinen Server, kein Token und keinen
 * Sync - und damit auch nichts, was ausfallen koennte.
 *
 * [CLUB] kommt hinzu, sobald ein Einladungscode eingeloest wurde. Der Weg von
 * [LOCAL] nach [CLUB] nimmt die bereits gespielten Abende mit; der Rueckweg
 * existiert bewusst nicht, denn was einmal auf dem Server liegt, laesst sich
 * lokal nicht sinnvoll "entsynchronisieren".
 */
enum class AppMode { LOCAL, CLUB }

/**
 * Der kleine, dauerhafte Rest, der nicht in die Datenbank gehoert: Serveradresse
 * und Geraetetoken.
 *
 * Hinter einem Interface, damit der Sync ohne Android-Kontext testbar bleibt -
 * DataStore braucht eine Datei, und der Sync soll seine Fallunterscheidungen
 * ohne Dateisystem beweisen koennen.
 */
interface AppSettings {

    val identity: Flow<DeviceIdentity?>

    /**
     * Der gewaehlte Betriebsmodus, oder null solange nichts gewaehlt wurde.
     *
     * Null ist der eigentliche Zustand "frisch installiert" und steuert, ob die
     * App im Einstieg landet oder im Startbildschirm. Er wird bewusst
     * gespeichert statt aus dem Vorhandensein eines Vereins abgeleitet: sonst
     * schoebe ein leerer Verein die App zurueck in den Einstieg.
     */
    val mode: Flow<AppMode?>

    suspend fun token(): String?

    suspend fun identityOrNull(): DeviceIdentity?

    suspend fun modeOrNull(): AppMode?

    /** Der Verein auf diesem Geraet, lokal oder vom Server. */
    suspend fun clubId(): String?

    /** Ohne Verein loslegen. Es gibt danach kein Token und keinen Sync. */
    suspend fun setLocalMode(clubId: String)

    suspend fun serverUrl(): String

    suspend fun setServerUrl(url: String)

    suspend fun saveJoin(token: String, identity: DeviceIdentity)

    /** Abmelden. Die Spieldaten bleiben liegen, nur das Token verschwindet. */
    suspend fun clearToken()
}

private val Context.rotaskatPreferences: DataStore<Preferences> by preferencesDataStore(name = "rotaskat")

class DataStoreAppSettings(
    private val store: DataStore<Preferences>,
    /**
     * Die Adresse aus dem Build ist nur die Vorbelegung. Ueberschrieben wird sie
     * ueber [setServerUrl], damit ein Verein seinen eigenen Server eintragen
     * kann, ohne die APK neu zu bauen.
     */
    private val defaultServerUrl: String = BuildConfig.DEFAULT_SERVER_URL,
) : AppSettings {

    constructor(context: Context) : this(context.applicationContext.rotaskatPreferences)

    override val identity: Flow<DeviceIdentity?> = store.data.map { it.readIdentity() }

    override val mode: Flow<AppMode?> = store.data.map { it.readMode() }

    override suspend fun token(): String? = store.data.first()[KEY_TOKEN]

    override suspend fun identityOrNull(): DeviceIdentity? = store.data.first().readIdentity()

    override suspend fun modeOrNull(): AppMode? = store.data.first().readMode()

    override suspend fun clubId(): String? = store.data.first()[KEY_CLUB_ID]

    override suspend fun setLocalMode(clubId: String) {
        store.edit {
            it[KEY_MODE] = AppMode.LOCAL.name
            it[KEY_CLUB_ID] = clubId
        }
    }

    override suspend fun serverUrl(): String =
        store.data.first()[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: defaultServerUrl

    override suspend fun setServerUrl(url: String) {
        store.edit { it[KEY_SERVER_URL] = url.trim().removeSuffix("/") }
    }

    override suspend fun saveJoin(token: String, identity: DeviceIdentity) {
        store.edit {
            it[KEY_TOKEN] = token
            it[KEY_DEVICE_ID] = identity.deviceId
            it[KEY_PLAYER_ID] = identity.playerId
            it[KEY_CLUB_ID] = identity.clubId
            it[KEY_MODE] = AppMode.CLUB.name
        }
    }

    override suspend fun clearToken() {
        store.edit { it.remove(KEY_TOKEN) }
    }

    private fun Preferences.readIdentity(): DeviceIdentity? {
        val deviceId = this[KEY_DEVICE_ID] ?: return null
        val playerId = this[KEY_PLAYER_ID] ?: return null
        val clubId = this[KEY_CLUB_ID] ?: return null
        return DeviceIdentity(deviceId, playerId, clubId)
    }

    /**
     * Ein unbekannter Wert gilt als "noch nichts gewaehlt". Das kann nur eine
     * aeltere oder neuere Fassung der App hinterlassen haben, und dann ist der
     * Einstieg die harmlosere Antwort als ein Absturz.
     */
    private fun Preferences.readMode(): AppMode? =
        this[KEY_MODE]?.let { raw -> AppMode.entries.firstOrNull { it.name == raw } }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("device_token")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_PLAYER_ID = stringPreferencesKey("player_id")
        val KEY_CLUB_ID = stringPreferencesKey("club_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_MODE = stringPreferencesKey("app_mode")
    }
}
