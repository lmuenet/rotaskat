package io.rotaskat.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
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
data class HealthResponse(val status: String, val version: String)

@Serializable
data class ErrorResponse(val error: String)

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

    Database.connect(dataSource)

    log.info("Rotaskat-Server startet auf Port ${config.port}")
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module()
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

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CallLogging)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Ungueltige Anfrage"))
        }
        exception<Throwable> { call, cause ->
            log.error("Unbehandelter Fehler", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Interner Fehler"))
        }
    }

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", version = BuildInfo.VERSION))
        }
    }
}

object BuildInfo {
    const val VERSION = "0.1.0"
}
