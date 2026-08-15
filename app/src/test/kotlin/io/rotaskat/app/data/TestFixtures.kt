package io.rotaskat.app.data

import io.rotaskat.app.data.net.PushResult
import io.rotaskat.app.data.net.RotaskatApi
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.data.settings.AppSettings
import io.rotaskat.app.data.settings.DeviceIdentity
import io.rotaskat.shared.api.ClubLookupRequest
import io.rotaskat.shared.api.ClubLookupResponse
import io.rotaskat.shared.api.JoinClubRequest
import io.rotaskat.shared.api.JoinClubResponse
import io.rotaskat.shared.api.SyncPullResponse
import io.rotaskat.shared.api.SyncPushRequest
import io.rotaskat.shared.api.SyncPushResponse
import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

const val CLUB_ID = "club-ruhrpott"

val TEST_CLUB = Club(
    id = CLUB_ID,
    name = "SC Ruhrpott",
    scoring = ScoringConfig(),
    centsPerPoint = 10,
    roster = listOf(
        Player("p0", "Anna"),
        Player("p1", "Bert"),
        Player("p2", "Carl"),
        Player("p3", "Dora"),
    ),
)

val T0: Instant = Instant.parse("2026-03-14T19:30:00.123456789Z")

/** Ein gewonnenes Kreuzspiel am Vierertisch. Der Geber sitzt aus. */
fun suitRound(
    id: String,
    dealerSeat: Int,
    declarerSeat: Int,
    won: Boolean = true,
    matadors: Int = 1,
    suit: Suit = Suit.CLUBS,
): Round = Round(
    id = id,
    seatCount = 4,
    declarerSeat = declarerSeat,
    sittingOutSeat = dealerSeat,
    declaration = SuitGame(suit, matadors),
    won = won,
    dealerSeat = dealerSeat,
)

class FakeAppSettings(
    var token: String? = "geheim",
    deviceIdentity: DeviceIdentity? = DeviceIdentity("device-1", "p0", CLUB_ID),
    var url: String = "https://example.invalid",
) : AppSettings {

    private val identityFlow = MutableStateFlow(deviceIdentity)

    var deviceIdentity: DeviceIdentity?
        get() = identityFlow.value
        set(value) {
            identityFlow.value = value
        }

    private val modeFlow = MutableStateFlow(deviceIdentity?.let { AppMode.CLUB })

    override val identity: Flow<DeviceIdentity?> get() = identityFlow

    override val mode: Flow<AppMode?> get() = modeFlow

    override suspend fun token(): String? = token

    override suspend fun identityOrNull(): DeviceIdentity? = identityFlow.value

    override suspend fun modeOrNull(): AppMode? = modeFlow.value

    override suspend fun clubId(): String? = identityFlow.value?.clubId ?: localClubId

    override suspend fun setLocalMode(clubId: String) {
        localClubId = clubId
        modeFlow.value = AppMode.LOCAL
    }

    private var localClubId: String? = null

    override suspend fun serverUrl(): String = url

    override suspend fun setServerUrl(url: String) {
        this.url = url
    }

    override suspend fun saveJoin(token: String, identity: DeviceIdentity) {
        this.token = token
        identityFlow.value = identity
        modeFlow.value = AppMode.CLUB
    }

    override suspend fun clearToken() {
        token = null
    }
}

/** Ein Server, dessen Antworten der Test Zeile fuer Zeile vorgibt. */
class FakeApi : RotaskatApi {

    val pushes = mutableListOf<SyncPushRequest>()
    val pullCursors = mutableListOf<Long>()

    var onPush: (SyncPushRequest) -> PushResult = { PushResult.Applied(SyncPushResponse(cursor = 0)) }
    var onPull: (Long) -> SyncPullResponse = { SyncPullResponse(cursor = it) }

    override suspend fun lookup(request: ClubLookupRequest): ClubLookupResponse =
        error("Im Test nicht benutzt")

    override suspend fun join(request: JoinClubRequest): JoinClubResponse =
        error("Im Test nicht benutzt")

    override suspend fun push(request: SyncPushRequest): PushResult {
        pushes += request
        return onPush(request)
    }

    override suspend fun pull(since: Long, limit: Int): SyncPullResponse {
        pullCursors += since
        return onPull(since)
    }
}
