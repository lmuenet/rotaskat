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
 * Der kleine, dauerhafte Rest, der nicht in die Datenbank gehoert: Serveradresse
 * und Geraetetoken.
 *
 * Hinter einem Interface, damit der Sync ohne Android-Kontext testbar bleibt -
 * DataStore braucht eine Datei, und der Sync soll seine Fallunterscheidungen
 * ohne Dateisystem beweisen koennen.
 */
interface AppSettings {

    val identity: Flow<DeviceIdentity?>

    suspend fun token(): String?

    suspend fun identityOrNull(): DeviceIdentity?

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

    override suspend fun token(): String? = store.data.first()[KEY_TOKEN]

    override suspend fun identityOrNull(): DeviceIdentity? = store.data.first().readIdentity()

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

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("device_token")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_PLAYER_ID = stringPreferencesKey("player_id")
        val KEY_CLUB_ID = stringPreferencesKey("club_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }
}
