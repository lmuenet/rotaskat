package io.rotaskat.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.rotaskat.server.auth.DeviceTokens
import io.rotaskat.shared.api.RotaskatJson
import io.rotaskat.shared.api.RoundEnvelope
import io.rotaskat.shared.api.SessionEnvelope
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import kotlinx.datetime.Instant

const val CLUB_ID = "11111111-1111-1111-1111-111111111111"
const val INVITE_CODE = "SKAT-2026"
const val SESSION_ID = "22222222-2222-2222-2222-222222222222"

const val ANNA = "aaaaaaaa-0000-0000-0000-000000000001"
const val BERND = "aaaaaaaa-0000-0000-0000-000000000002"
const val CLARA = "aaaaaaaa-0000-0000-0000-000000000003"

/** Klartext-Token der Tests. In der Datenbank landet nur sein Hash. */
const val TOKEN = "test-token-anna"

val TEST_CLUB = Club(
    id = CLUB_ID,
    name = "Skatverein Testhausen",
    centsPerPoint = 10,
    roster = listOf(
        Player(ANNA, "Anna"),
        Player(BERND, "Bernd"),
        Player(CLARA, "Clara"),
    ),
)

fun testSession(
    startedAt: Instant = Instant.parse("2026-03-14T19:00:00Z"),
    id: String = SESSION_ID,
): Session = Session(
    id = id,
    clubId = CLUB_ID,
    seatCount = 3,
    seats = mapOf(0 to ANNA, 1 to BERND, 2 to CLARA),
    startedAt = startedAt,
    centsPerPoint = TEST_CLUB.centsPerPoint,
)

/**
 * Kreuz mit 2, gewonnen: Spielwert 12 * 3 = 36. In halben Punkten bekommt der
 * Alleinspieler +72, jeder Gegenspieler -36.
 */
fun testRound(
    id: String,
    declarerSeat: Int = 0,
    won: Boolean = true,
    matadors: Int = 2,
): Round = Round(
    id = id,
    seatCount = 3,
    declarerSeat = declarerSeat,
    sittingOutSeat = null,
    declaration = SuitGame(Suit.CLUBS, matadors),
    won = won,
)

fun envelopeOf(
    round: Round,
    sequence: Int,
    revision: Long = 1,
    clientHalfPoints: Map<Int, Int> = emptyMap(),
    sessionId: String = SESSION_ID,
): RoundEnvelope = RoundEnvelope(
    round = round,
    sessionId = sessionId,
    sequence = sequence,
    revision = revision,
    clientHalfPoints = clientHalfPoints,
)

fun sessionEnvelope(session: Session = testSession(), revision: Long = 1): SessionEnvelope =
    SessionEnvelope(session = session, revision = revision)

/** Ein Repository mit Verein, Einladungscode und einem bereits ausgestellten Token. */
fun testRepository(vararg clubs: Club = arrayOf(TEST_CLUB)): InMemoryRepository =
    InMemoryRepository(clubs.toList())
        .withInviteCode(INVITE_CODE, CLUB_ID)
        .also { it.issueToken(CLUB_ID, ANNA, DeviceTokens.hash(TOKEN)) }

/** Testclient mit derselben Json-Konfiguration wie App und Server. */
fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(RotaskatJson) }
}
