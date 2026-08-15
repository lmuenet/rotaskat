package io.rotaskat.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.rotaskat.server.auth.DeviceTokens
import io.rotaskat.server.auth.deviceBearer
import io.rotaskat.server.db.PostgresRepository
import io.rotaskat.server.repo.RotaskatRepository
import io.rotaskat.server.routes.clubRoutes
import io.rotaskat.server.routes.leaderboardRoutes
import io.rotaskat.server.routes.sessionRoutes
import io.rotaskat.server.routes.syncRoutes
import io.rotaskat.server.sync.SyncService
import io.rotaskat.shared.api.ApiError
import io.rotaskat.shared.scoring.Scoring
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Konfiguration kommt ausschliesslich aus der Umgebung. Im Repo liegt nur
 * .env.example, die echte .env ist nicht eingecheckt.
 */
data class ServerConfig(
    val port: Int,
    val jdbcUrl: String,
    val dbUser: String,
    val dbPassword: String,
) {
    companion object {
        fun fromEnvironment(): ServerConfig {
            fun required(name: String): String = System.getenv(name)
                ?: error("Umgebungsvariable $name fehlt. Siehe .env.example.")

            return ServerConfig(
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                jdbcUrl = required("DATABASE_URL"),
                dbUser = required("DATABASE_USER"),
                dbPassword = required("DATABASE_PASSWORD"),
            )
        }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    /**
     * Der Stand des Abrechnungsalgorithmus. Steht bewusst in /health: eine App,
     * die eine andere Zahl mitbringt, rechnet moeglicherweise anders als der
     * Server, und das soll ohne Sync sichtbar sein.
     */
    val scoringVersion: Int = Scoring.SCORING_VERSION,
)

private val log = LoggerFactory.getLogger("io.rotaskat.server")

fun main() {
    val config = ServerConfig.fromEnvironment()
    val dataSource = createDataSource(config)

    // Flyway laeuft vor dem Start, damit der Server nie gegen ein veraltetes
    // Schema hochkommt.
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()

    val database = Database.connect(dataSource)
    val repository = PostgresRepository(database)

    log.info("Rotaskat-Server startet auf Port ${config.port}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(repository)
    }.start(wait = true)
}

fun createDataSource(config: ServerConfig): DataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.dbUser
        password = config.dbPassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 5
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
)

/**
 * Der Anwendungsaufbau nimmt das Repository von aussen entgegen. Nicht aus
 * Dogmatik, sondern damit die Endpunkttests ohne Datenbank und ohne Docker
 * laufen koennen - das Schema lebt von jsonb, plpgsql und FILTER-Aggregaten
 * und laesst sich nicht sinnvoll in eine eingebettete Datenbank uebersetzen.
 */
fun Application.module(repository: RotaskatRepository) {
    val syncService = SyncService(repository)

    install(ContentNegotiation) {
        // ignoreUnknownKeys ist hier Pflicht, nicht Bequemlichkeit: die APK
        // kommt ueber GitHub Releases ohne erzwungenen Update-Pfad, ein
        // neueres Feld darf einen alten Client nicht abwuergen - und
        // umgekehrt.
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false })
    }
    install(CallLogging)
    install(Authentication) {
        deviceBearer(repository)
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.message, cause.details))
        }
        // Deserialisierungsfehler und die require()-Bloecke im Modell landen
        // hier. Ohne diesen Zweig waere eine manipulierte oder schlicht alte
        // Nutzlast ein 500er statt eines 400ers.
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Ungueltige Anfrage"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Ungueltige Anfrage"))
        }
        exception<Throwable> { call, cause ->
            log.error("Unbehandelter Fehler", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Interner Fehler"))
        }
    }

    routing {
        // /health und /clubs/join bleiben offen: das eine ist der Health-Check
        // des Betreibers, das andere stellt das Token ueberhaupt erst aus.
        get("/health") {
            call.respond(HealthResponse(status = "ok", version = BuildInfo.VERSION))
        }
        clubRoutes(repository)

        authenticate(DeviceTokens.PROVIDER) {
            syncRoutes(repository, syncService)
            leaderboardRoutes(repository)
            sessionRoutes(repository)
        }
    }
}

object BuildInfo {
    const val VERSION = "0.1.0"
}
