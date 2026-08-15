package io.rotaskat.app.data

import android.content.Context
import io.ktor.client.HttpClient
import io.rotaskat.app.data.db.RotaskatDatabase
import io.rotaskat.app.data.net.KtorRotaskatApi
import io.rotaskat.app.data.net.RotaskatApi
import io.rotaskat.app.data.net.rotaskatHttpClient
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.app.data.settings.DataStoreAppSettings
import io.rotaskat.app.data.sync.SyncEngine
import io.rotaskat.app.data.sync.SyncWorker

/**
 * Der Zusammenbau der Datenschicht.
 *
 * Bewusst ein handgeschriebener Service-Locator statt eines DI-Frameworks: es
 * sind fuenf Objekte mit einer einzigen Lebensdauer, und der
 * [SyncWorker] muss ohnehin von aussen an sie herankommen - der WorkManager
 * erzeugt ihn mit dem Standard-Konstruktor und kann nichts hineinreichen.
 */
class RotaskatGraph private constructor(
    val database: RotaskatDatabase,
    val settings: AppSettings,
    val httpClient: HttpClient,
    val api: RotaskatApi,
    val repository: RotaskatRepository,
    val syncEngine: SyncEngine,
) {

    companion object {
        @Volatile
        private var instance: RotaskatGraph? = null

        fun get(context: Context): RotaskatGraph {
            instance?.let { return it }
            val appContext = context.applicationContext
            return synchronized(this) {
                instance ?: build(appContext).also { instance = it }
            }
        }

        private fun build(appContext: Context): RotaskatGraph {
            val database = RotaskatDatabase.open(appContext)
            val settings = DataStoreAppSettings(appContext)
            val httpClient = rotaskatHttpClient()
            val api = KtorRotaskatApi(httpClient, settings)
            val repository = RoomRotaskatRepository(database, SyncWorker.trigger(appContext))
            return RotaskatGraph(
                database = database,
                settings = settings,
                httpClient = httpClient,
                api = api,
                repository = repository,
                syncEngine = SyncEngine(repository, api, settings),
            )
        }

        /**
         * Setzt einen fertig gebauten Graphen, etwa fuer Instrumentierung. Nur
         * vor dem ersten [get] sinnvoll.
         */
        fun install(graph: RotaskatGraph) {
            synchronized(this) { instance = graph }
        }
    }
}
