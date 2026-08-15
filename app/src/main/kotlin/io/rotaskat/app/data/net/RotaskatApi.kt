package io.rotaskat.app.data.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.rotaskat.app.BuildConfig
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.shared.api.JoinClubRequest
import io.rotaskat.shared.api.JoinClubResponse
import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse

/** Der Server hat den Batch angenommen oder wegen eines Konflikts verworfen. */
sealed interface PushResult {

    val response: SyncPushResponse

    data class Applied(override val response: SyncPushResponse) : PushResult

    /**
     * 409: gleiche Revision, anderer Inhalt. Der Server hat NICHTS geschrieben,
     * auch nicht die unstrittigen Runden des Batches.
     */
    data class Conflicted(override val response: SyncPushResponse) : PushResult
}

/** Das Geraetetoken ist weg oder wurde stillgelegt. Erneuter Beitritt noetig. */
class ApiUnauthorizedException : IllegalStateException("Geraetetoken ungueltig")

/** Das Geraet ist noch keinem Verein beigetreten - es gibt nichts zu synchronisieren. */
class NotJoinedException : IllegalStateException("Kein Geraetetoken hinterlegt")

class ApiFailureException(
    val status: HttpStatusCode,
    val body: String,
) : IllegalStateException("Server antwortete mit $status: $body")

interface RotaskatApi {

    suspend fun join(request: JoinClubRequest): JoinClubResponse

    suspend fun push(request: SyncPushRequest): PushResult

    suspend fun pull(since: Long, limit: Int): SyncPullResponse
}

/**
 * Baut den HTTP-Client der App.
 *
 * Bewusst OHNE Retry-Plugin: das Wiederholen macht der WorkManager mit
 * exponentiellem Backoff und ueberlebt dabei den Prozesstod. Ein zweiter
 * Wiederholungsmechanismus im Client wuerde sich mit dem Backoff ueberlagern und
 * in der Kneipe ohne Netz nur Akku kosten.
 */
fun rotaskatHttpClient(): HttpClient = HttpClient(OkHttp) {
    // Fehlerstatus wird hier ausgewertet, nicht als Exception geworfen: ein 409
    // ist ein regulaeres Ergebnis des Sync und kein Ausnahmefall.
    expectSuccess = false

    install(ContentNegotiation) {
        json(RotaskatJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    if (BuildConfig.DEBUG) {
        install(Logging) {
            level = LogLevel.INFO
            // Das Geraetetoken darf auch im Debug-Log nicht auftauchen: Logcat
            // liest jede App mit passender Berechtigung mit.
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }
}

/**
 * Die Serveranbindung. Adresse und Token kommen bei JEDEM Aufruf frisch aus den
 * Einstellungen - ein einmal in den Client gebranntes Token wuerde nach einem
 * erneuten Beitritt weiterverwendet.
 */
class KtorRotaskatApi(
    private val client: HttpClient,
    private val settings: AppSettings,
) : RotaskatApi {

    override suspend fun join(request: JoinClubRequest): JoinClubResponse {
        val response = client.post("${base()}/clubs/join") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.requireSuccess().body()
    }

    override suspend fun push(request: SyncPushRequest): PushResult {
        val token = requireToken()
        val response = client.post("${base()}/sync/rounds") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return when (response.status) {
            HttpStatusCode.OK -> PushResult.Applied(response.body())
            HttpStatusCode.Conflict -> PushResult.Conflicted(response.body())
            else -> response.requireSuccess().let { PushResult.Applied(it.body()) }
        }
    }

    override suspend fun pull(since: Long, limit: Int): SyncPullResponse {
        val token = requireToken()
        val response = client.get("${base()}/sync/rounds") {
            bearerAuth(token)
            parameter("since", since)
            parameter("limit", limit)
        }
        return response.requireSuccess().body()
    }

    private suspend fun base(): String {
        val url = settings.serverUrl()
        if (url.isBlank()) throw NotJoinedException()
        return url.removeSuffix("/")
    }

    private suspend fun requireToken(): String = settings.token() ?: throw NotJoinedException()

    private suspend fun HttpResponse.requireSuccess(): HttpResponse = when {
        status.isSuccess() -> this
        // 401 ist kein Netzfehler und wird durch Wiederholen nicht besser.
        status == HttpStatusCode.Unauthorized -> throw ApiUnauthorizedException()
        else -> throw ApiFailureException(status, bodyAsText().take(MAX_ERROR_BODY))
    }

    private companion object {
        /** Fehlertexte des Servers landen im Log, deshalb gekuerzt. */
        const val MAX_ERROR_BODY = 500
    }
}
