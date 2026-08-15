package io.rotaskat.app.data.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.rotaskat.app.data.FakeAppSettings
import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.api.SyncConflict
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Die Serveranbindung gegen eine MockEngine. Reiner JVM-Test, kein Robolectric:
 * hier haengt nichts an Android.
 */
class KtorRotaskatApiTest {

    private val settings = FakeAppSettings(token = "geheim", url = "https://skat.example")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RotaskatApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(RotaskatJson) }
        }
        return KtorRotaskatApi(client, settings)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `Der Push traegt das Geraetetoken als Bearer`() = runTest {
        val api = api { respond(RotaskatJson.encodeToString(SyncPushResponse.serializer(), SyncPushResponse(cursor = 1)), HttpStatusCode.OK, jsonHeaders) }

        api.push(SyncPushRequest())

        assertEquals("Bearer geheim", requests.single().headers[HttpHeaders.Authorization])
        assertEquals("https://skat.example/sync/rounds", requests.single().url.toString())
    }

    @Test
    fun `Ein 409 ist ein regulaeres Ergebnis und keine Ausnahme`() = runTest {
        val conflictBody = SyncPushResponse(
            cursor = 3,
            conflicts = listOf(SyncConflict("r1", "round", 2, "aaa", "bbb")),
        )
        val api = api {
            respond(
                RotaskatJson.encodeToString(SyncPushResponse.serializer(), conflictBody),
                HttpStatusCode.Conflict,
                jsonHeaders,
            )
        }

        val result = api.push(SyncPushRequest())

        // Der Sync muss den Konflikt aufloesen koennen; als Ausnahme waere er
        // vom Netzfehler nicht zu unterscheiden.
        val conflicted = assertIs<PushResult.Conflicted>(result)
        assertEquals("r1", conflicted.response.conflicts.single().id)
    }

    @Test
    fun `Ein zurueckgezogenes Token wird als eigener Fehler sichtbar`() = runTest {
        val api = api { respond("", HttpStatusCode.Unauthorized) }

        // Eigener Typ, weil Wiederholen hier nichts bringt - das Geraet muss neu
        // beitreten.
        assertFailsWith<ApiUnauthorizedException> { api.pull(since = 0, limit = 10) }
    }

    @Test
    fun `Der Pull schickt Cursor und Seitengroesse mit`() = runTest {
        val api = api {
            respond(
                RotaskatJson.encodeToString(SyncPullResponse.serializer(), SyncPullResponse(cursor = 5)),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val response = api.pull(since = 17, limit = 200)

        val url = requests.single().url
        assertEquals("17", url.parameters["since"])
        assertEquals("200", url.parameters["limit"])
        assertEquals(5L, response.cursor)
    }

    @Test
    fun `Ohne Token wird gar nicht erst gefragt`() = runTest {
        settings.token = null
        val api = api { respond("", HttpStatusCode.OK) }

        assertFailsWith<NotJoinedException> { api.push(SyncPushRequest()) }
        assertTrue(requests.isEmpty(), "Es ging trotzdem eine Anfrage raus")
    }

    @Test
    fun `Ein Serverfehler traegt Status und Text nach oben`() = runTest {
        val api = api { respond("Datenbank weg", HttpStatusCode.InternalServerError) }

        val error = assertFailsWith<ApiFailureException> { api.pull(since = 0, limit = 10) }
        assertEquals(HttpStatusCode.InternalServerError, error.status)
        assertTrue(error.body.contains("Datenbank weg"))
    }
}
