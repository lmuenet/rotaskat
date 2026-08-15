package io.rotaskat.shared.api

import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Die gemeinsame Json-Konfiguration von App und Server.
 *
 * Die APK wird ueber GitHub Releases verteilt, es gibt also keinen erzwungenen
 * Update-Pfad: eine zwei Jahre alte App redet mit dem heutigen Server und
 * umgekehrt. Daraus folgen zwei harte Regeln fuer alles in dieser Datei:
 *
 *  1. `ignoreUnknownKeys` auf BEIDEN Seiten. Ein neues Feld darf einen alten
 *     Client nicht zum Absturz bringen.
 *  2. Aenderungen sind ausschliesslich ADDITIV. Neue Felder brauchen immer
 *     einen Vorgabewert, sonst scheitert das Dekodieren einer alten Nutzlast.
 *     Felder werden nicht umbenannt und nicht entfernt, sondern hoechstens
 *     unbenutzt gelassen.
 *
 * `encodeDefaults` steht an, damit die Nutzlast selbsterklaerend bleibt und
 * der serverseitige Inhalts-Hash nicht davon abhaengt, ob ein Feld zufaellig
 * seinem Vorgabewert entspricht.
 */
val RotaskatJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// --- Vereinsbeitritt -------------------------------------------------------

/**
 * Nachschlagen, welcher Verein hinter einem Einladungscode steht.
 *
 * Noetig, weil [JoinClubRequest] eine [playerId] aus dem Kader verlangt, der
 * Kader aber erst mit der Beitrittsantwort kaeme. Ohne diesen Schritt muesste
 * der Beitretende seine eigene Spieler-Id auswendig wissen.
 *
 * Der Einladungscode IST das Geheimnis: wer ihn hat, darf den Kader sehen, denn
 * er darf ohnehin gleich beitreten. Ein unbekannter Code antwortet mit
 * derselben Meldung wie ein fehlgeschlagener Beitritt, damit sich Codes nicht
 * durchprobieren lassen.
 */
@Serializable
data class ClubLookupRequest(
    val inviteCode: String,
)

/**
 * Der Verein samt Kader, aber ohne Token. Das Nachschlagen allein macht das
 * Geraet noch zu keinem Mitglied.
 */
@Serializable
data class ClubLookupResponse(
    val club: Club,
)

/**
 * Einladungscode gegen ein Geraetetoken tauschen.
 *
 * [playerId] muss ein Mitglied des Kaders sein: es gibt keine Gastspieler und
 * damit auch keinen Weg, ueber den Beitritt einen neuen Spieler anzulegen.
 */
@Serializable
data class JoinClubRequest(
    val inviteCode: String,
    val playerId: String,
    /** Frei waehlbare Geraetebezeichnung, taucht nur in der Geraeteliste auf. */
    val deviceLabel: String? = null,
)

/**
 * Das Token wird genau einmal ausgeliefert. In der Datenbank liegt nur sein
 * Hash, ein verlorenes Token laesst sich also nicht nachschlagen, sondern nur
 * neu ausstellen.
 */
@Serializable
data class JoinClubResponse(
    val token: String,
    val deviceId: String,
    val playerId: String,
    val club: Club,
)

// --- Sync ------------------------------------------------------------------

/**
 * Eine Runde auf der Leitung.
 *
 * [Round] selbst kennt weder Session noch Reihenfolge noch Revision - das sind
 * Transporteigenschaften, keine Spielfakten. Sie stehen deshalb hier aussen
 * herum statt im Modell.
 */
@Serializable
data class RoundEnvelope(
    val round: Round,
    val sessionId: String,
    /** Position innerhalb des Abends, 0-basiert und je Session eindeutig. */
    val sequence: Int,
    /**
     * Wird bei jeder Korrektur derselben Runde hochgezaehlt. Nur eine hoehere
     * Revision darf ueberschreiben, gleiche Revision mit anderem Inhalt ist
     * ein Konflikt.
     */
    val revision: Long = 1,
    /** Loeschen ist immer ein Tombstone, nie ein DELETE. */
    val deletedAt: Instant? = null,
    /**
     * Die vom Client gerechneten halben Punkte. Reine PRUEFSUMME: der Server
     * verwirft sie und rechnet mit :shared neu. Eine Abweichung ist der
     * Fruehwarner fuer Versionsdrift zwischen APK und Server.
     */
    val clientHalfPoints: Map<Int, Int> = emptyMap(),
    /** Serverseitig nachgerechnet, beim Push ignoriert. */
    val halfPoints: Map<Int, Int> = emptyMap(),
    /** Serverseitig nachgerechnet, beim Push ignoriert. */
    val gameValue: Int = 0,
    /** Serverseitiger Sequenzzaehler, Cursor fuer den Delta-Pull. */
    val updatedSeq: Long = 0,
)

/** Eine Session auf der Leitung, mit derselben Revisionslogik wie die Runde. */
@Serializable
data class SessionEnvelope(
    val session: Session,
    val revision: Long = 1,
    val deletedAt: Instant? = null,
    val updatedSeq: Long = 0,
)

@Serializable
data class SyncPushRequest(
    /**
     * Sessions kommen im selben Batch mit, weil eine Runde ohne ihre Session
     * nicht abrechenbar ist: die eingefrorene ScoringConfig haengt dort.
     */
    val sessions: List<SessionEnvelope> = emptyList(),
    val rounds: List<RoundEnvelope> = emptyList(),
)

/** Was mit einem einzelnen Datensatz passiert ist. */
@Serializable
enum class SyncResult {
    /** Neu geschrieben oder durch eine hoehere Revision ersetzt. */
    ACCEPTED,

    /** Gleiche Revision, gleicher Inhalt - der idempotente Wiederholungsfall. */
    UNCHANGED,

    /** Der Server hat bereits eine hoehere Revision, das Paket ist veraltet. */
    STALE,
}

@Serializable
data class SyncItemResult(
    val id: String,
    val result: SyncResult,
    val updatedSeq: Long = 0,
)

/**
 * Gleiche Revision, abweichender Inhalt. Das ist der einzige Fall, den der
 * Server nicht selbst entscheiden kann, ohne stillschweigend Daten zu
 * verlieren - deshalb 409 statt "letzter gewinnt".
 */
@Serializable
data class SyncConflict(
    val id: String,
    val kind: String,
    val revision: Long,
    val serverContentHash: String,
    val clientContentHash: String,
    /**
     * Der Serverstand ist ein Tombstone.
     *
     * Ohne dieses Feld kann der Client nur die Hashes vergleichen und weiss
     * damit zwar, DASS er abweicht, aber nicht, dass die Runde auf dem Server
     * geloescht ist. Er hebt dann seine Revision an und schickt den lebenden
     * Stand erneut - die geloeschte Runde steht danach ueberall wieder in der
     * Rangliste. Additiv mit Vorgabewert, ein alter Client liest es schlicht
     * nicht.
     */
    val deleted: Boolean = false,
)

/**
 * Die Nachrechnung hat einen anderen Wert ergeben als der Client geschickt
 * hat. Die Runde wird trotzdem gespeichert, mit dem SERVERWERT.
 */
@Serializable
data class ScoreMismatch(
    val roundId: String,
    val seat: Int,
    val clientHalfPoints: Int,
    val serverHalfPoints: Int,
)

@Serializable
data class SyncPushResponse(
    val cursor: Long,
    val sessions: List<SyncItemResult> = emptyList(),
    val rounds: List<SyncItemResult> = emptyList(),
    val mismatches: List<ScoreMismatch> = emptyList(),
    /** Nur im 409-Fall gefuellt; dann wurde nichts geschrieben. */
    val conflicts: List<SyncConflict> = emptyList(),
)

/**
 * Antwort des Delta-Pull.
 *
 * [cursor] ist der hoechste ausgelieferte `updated_seq`. Der Client merkt ihn
 * sich und schickt ihn beim naechsten Mal als `since` zurueck. Bewusst ein
 * serverseitiger Zaehler und kein Zeitstempel: Handyuhren laufen auseinander,
 * ein Zeitstempel wuerde Runden ueberspringen.
 */
@Serializable
data class SyncPullResponse(
    val cursor: Long,
    val rounds: List<RoundEnvelope> = emptyList(),
    /**
     * Die Sessions, die im selben Fenster geaendert wurden. Ohne sie kann ein
     * frisch eingerichtetes Geraet die mitgelieferten Runden nicht einordnen.
     */
    val sessions: List<SessionEnvelope> = emptyList(),
    /** Es liegen noch Aenderungen jenseits von [cursor] bereit. */
    val hasMore: Boolean = false,
)

// --- Rangliste -------------------------------------------------------------

@Serializable
data class LeaderboardEntry(
    val playerId: String,
    val displayName: String,
    val roundsPlayed: Int = 0,
    val sessionsPlayed: Int = 0,
    /** Summe in HALBEN Punkten, in Long: ueber eine Saison wird Int knapp. */
    val totalHalfPoints: Long = 0,
    val gamesDeclared: Int = 0,
    val gamesDeclaredWon: Int = 0,
) {
    /** Nur fuer die Anzeige. */
    val totalPoints: Double get() = totalHalfPoints / 2.0
}

@Serializable
data class LeaderboardResponse(
    /**
     * Kalenderjahr der Saison, oder null fuer all-time. Ein frueher Vorsprung
     * soll die Tabelle nicht auf Jahre einbetonieren, deshalb gibt es beide.
     */
    val season: Int? = null,
    val entries: List<LeaderboardEntry> = emptyList(),
)

// --- Sessiondetail ---------------------------------------------------------

/** Saldo eines Sitzplatzes in ganzen Cent. Positiv heisst: bekommt Geld. */
@Serializable
data class BalanceDto(
    val seat: Int,
    val playerId: String? = null,
    val cents: Long,
)

@Serializable
data class PaymentDto(
    val fromSeat: Int,
    val toSeat: Int,
    val fromPlayerId: String? = null,
    val toPlayerId: String? = null,
    val cents: Long,
)

@Serializable
data class SettlementDto(
    val centsPerPoint: Int,
    val balances: List<BalanceDto> = emptyList(),
    val payments: List<PaymentDto> = emptyList(),
)

@Serializable
data class SessionDetailResponse(
    val session: Session,
    val rounds: List<RoundEnvelope> = emptyList(),
    /** Summe der halben Punkte je Sitzplatz ueber den ganzen Abend. */
    val totals: Map<Int, Long> = emptyMap(),
    /**
     * Die Geldabrechnung. Abgeleitet und jederzeit neu berechenbar, wird
     * deshalb nicht gespeichert, sondern bei jeder Abfrage gerechnet.
     */
    val settlement: SettlementDto? = null,
)

// --- Fehler ----------------------------------------------------------------

@Serializable
data class ApiError(
    val error: String,
    val details: List<String> = emptyList(),
)
