package io.rotaskat.app.data

import io.rotaskat.shared.model.Club
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Session
import io.rotaskat.shared.model.TableRotation
import io.rotaskat.shared.scoring.RoundScore
import io.rotaskat.shared.settlement.Settlement
import io.rotaskat.shared.settlement.Settlements
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Eine Runde mit ihrem abgeleiteten Ergebnis.
 *
 * [score] steht nicht in der Datenbank. Er wird bei jedem Lesen mit der fuer
 * die Session eingefrorenen ScoringConfig gerechnet, damit eine geaenderte
 * Hausregel die Historie neu bewertet statt sie unrettbar zu machen.
 */
data class ScoredRound(
    val id: String,
    val sequence: Int,
    val revision: Long,
    val round: Round,
    val score: RoundScore,
    val deletedAt: Instant?,
    val pendingSync: Boolean,
) {
    val deleted: Boolean get() = deletedAt != null
}

/**
 * Der vollstaendige Stand eines Abends, so wie ihn die Oberflaeche braucht.
 */
data class SessionState(
    val session: Session,
    /** Alle Runden inklusive Tombstones, aufsteigend. */
    val rounds: List<ScoredRound>,
    /** Summe der halben Punkte je Sitzplatz. Enthaelt jeden Platz, auch mit 0. */
    val totals: Map<Int, Long>,
) {
    val liveRounds: List<ScoredRound> get() = rounds.filter { !it.deleted }

    /** Stellung fuer die naechste Runde. */
    val rotation: TableRotation get() = session.rotation

    /**
     * Die Geldabrechnung des Abends. Abgeleitet und deshalb nicht gespeichert -
     * dieselbe Entscheidung wie beim Rundenergebnis.
     */
    fun settlement(): Settlement<Int> = Settlements.settle(totals, session.centsPerPoint)
}

/** Ein Abend in der Uebersicht, ohne die Runden zu laden. */
data class SessionSummary(
    val session: Session,
    val roundCount: Int,
)

/**
 * Loest einen Sync-Lauf aus, ohne auf ihn zu warten.
 *
 * Der Rueckgabetyp ist `Unit` und die Funktion ist nicht suspendierend - und
 * das ist die eigentliche Aussage: der Sync darf den Eingabepfad nie
 * blockieren. Zwischen zwei Runden gibt es keinen Spinner und keinen
 * Fehlerdialog.
 */
fun interface SyncTrigger {
    fun requestSync()

    companion object {
        /** Fuer Tests und fuer den Zustand vor dem Vereinsbeitritt. */
        val None: SyncTrigger = SyncTrigger { }
    }
}

/**
 * Die einzige Schnittstelle, die die Oberflaeche kennt.
 *
 * Room ist waehrend des Spielens die Wahrheit, der Server wird nur nachgezogen.
 * Es gibt deshalb keine Methode, die auf das Netz wartet: jede Schreiboperation
 * ist fertig, sobald sie in der lokalen Datenbank steht.
 *
 * Runden sind unveraenderlich in dem Sinn, der fuer den Sync zaehlt: eine
 * Korrektur behaelt die Id und bekommt eine hoehere Revision, ein Loeschen
 * setzt [deleteRound] nur einen Tombstone. Physisch geloescht wird nie - die
 * Runde kaeme ueber den idempotenten Delta-Pull sofort zurueck.
 */
interface RotaskatRepository {

    // --- Lesen ---

    fun observeClub(): Flow<Club?>

    fun observeRoster(): Flow<List<Player>>

    /** Der laufende Abend, oder null. Es gibt hoechstens einen. */
    fun observeOpenSession(): Flow<SessionState?>

    fun observeSession(sessionId: String): Flow<SessionState?>

    fun observeSessions(): Flow<List<SessionSummary>>

    /**
     * Alle Abende samt ihren Runden und deren Ergebnissen.
     *
     * Die Grundlage von Rangliste und Statistik. Bewusst der volle Stand statt
     * fertiger Summen: jeder Abend rechnet mit der Hausregel, die zu seinem
     * Anpfiff galt, und das laesst sich nur an der Session entlang machen. Ein
     * Verein spielt in der Groessenordnung hundert Abende zu je dreissig Runden -
     * das sind ein paar tausend Zeilen, also kein Anlass fuer eine Seitenlogik.
     */
    fun observeSessionStates(): Flow<List<SessionState>>

    /** Wie viele Runden noch auf den Server warten. Reine Anzeige, kein Tor. */
    fun observePendingSyncCount(): Flow<Int>

    suspend fun club(): Club?

    suspend fun session(sessionId: String): SessionState?

    // --- Schreiben ---

    suspend fun saveClub(club: Club)

    /**
     * Uebergibt die lokal gespielten Abende an einen echten Verein.
     *
     * Der Weg dorthin ist der Normalfall und nicht der Sonderfall: man spielt
     * erst ein paar Abende ohne Server und richtet den Server spaeter ein. Ohne
     * diese Uebergabe waeren genau diese ersten Abende verloren.
     *
     * [playerMapping] bildet die lokalen Spieler-Ids auf Mitglieder des
     * Vereinskaders ab und muss JEDEN lokal belegten Sitzplatz abdecken.
     * Die Runden selbst bleiben unberuehrt: sie kennen nur Sitzplatznummern,
     * keine Spieler - deshalb genuegt es, die Sitzordnung umzuhaengen.
     *
     * Danach warten alle Abende und Runden auf den Sync.
     */
    suspend fun adoptLocalData(club: Club, playerMapping: Map<String, String>)

    /**
     * Startet einen Abend und friert die Vereinsregeln dafuer ein.
     * Liefert die Id der neuen Session.
     */
    suspend fun startSession(
        seatCount: Int,
        seats: Map<Int, String>,
        dealerSeat: Int = 0,
        startedAt: Instant = Clock.System.now(),
    ): String

    suspend fun endSession(sessionId: String, endedAt: Instant = Clock.System.now())

    /** Ein Tap korrigiert die automatisch fortgeschriebene Rotation. */
    suspend fun setDealer(sessionId: String, dealerSeat: Int)

    /** Eine neue, zeitsortierte Runden-Id. Vergeben wird sie auf dem Client. */
    fun newRoundId(): String

    /**
     * Speichert eine Runde. Zweimal mit derselben Id und demselben Inhalt
     * aufgerufen entsteht genau EINE Runde - der Doppeltap am Tisch ist damit
     * folgenlos.
     */
    suspend fun recordRound(sessionId: String, round: Round): String

    /** Korrektur: gleiche Id, neue Revision. */
    suspend fun correctRound(round: Round)

    suspend fun deleteRound(roundId: String, at: Instant = Clock.System.now())
}
