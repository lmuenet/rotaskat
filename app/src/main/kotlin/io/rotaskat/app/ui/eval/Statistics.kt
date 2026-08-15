package io.rotaskat.app.ui.eval

import io.rotaskat.app.data.SessionState
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.Player
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Die Spielart, wie sie in einer Statistik gezaehlt wird.
 *
 * Groeber als [Declaration]: Spitzen und Stufen bleiben aussen vor. "Kreuz mit
 * zwei Hand" und "Kreuz mit eins" sind fuer die Frage, was jemand am liebsten
 * spielt, dasselbe Spiel.
 */
enum class GameKind(val label: String) {
    KARO("Karo"),
    HERZ("Herz"),
    PIK("Pik"),
    KREUZ("Kreuz"),
    GRAND("Grand"),
    NULL("Null"),
    RAMSCH("Ramsch"),
}

fun kindOf(declaration: Declaration): GameKind = when (declaration) {
    is SuitGame -> when (declaration.suit) {
        Suit.DIAMONDS -> GameKind.KARO
        Suit.HEARTS -> GameKind.HERZ
        Suit.SPADES -> GameKind.PIK
        Suit.CLUBS -> GameKind.KREUZ
    }

    is GrandGame -> GameKind.GRAND
    is NullGame -> GameKind.NULL
    is RamschGame -> GameKind.RAMSCH
}

/**
 * Der Zeitraum einer Auswertung.
 *
 * Eine Saison ist ein Kalenderjahr (SCOPE.md). Massgeblich ist der Anpfiff, nicht
 * das Ende: ein Abend, der ueber Mitternacht des 31. Dezember laeuft, gehoert zu
 * der Saison, in der er begonnen hat - sonst haette er zwei.
 */
sealed interface Period {

    data object AllTime : Period

    data class Season(val year: Int) : Period

    val label: String
        get() = when (this) {
            AllTime -> "Alle Jahre"
            is Season -> year.toString()
        }
}

/** Was ein Spieler an einem einzelnen Abend geholt hat. */
data class SessionResult(
    val sessionId: String,
    val startedAt: Instant,
    val halfPoints: Long,
    /** Runden, an denen der Spieler beteiligt war. Aussetzen zaehlt nicht mit. */
    val rounds: Int,
)

/**
 * Die Zahlen eines Spielers ueber einen Zeitraum.
 *
 * Jede Quote traegt ihre Grundgesamtheit mit sich. Das ist keine Zugabe,
 * sondern der Punkt: eine Gewinnquote von 100 Prozent aus zwei Alleinspielen ist
 * keine Aussage ueber den Spieler, und ohne die Zwei sieht man das nicht.
 */
data class PlayerStats(
    val player: Player,
    val halfPoints: Long,
    /** Abende, an denen der Spieler am Tisch sass und gespielt wurde. */
    val sessions: Int,
    /** Runden, an denen er beteiligt war. Wer aussetzt, spielt nicht mit. */
    val rounds: Int,
    val soloRounds: Int,
    val soloWins: Int,
    /** Wie oft er welches Spiel als Alleinspieler angesagt hat. */
    val declarations: Map<GameKind, Int>,
    /** Die Abende, chronologisch aufsteigend. */
    val results: List<SessionResult>,
) {

    /** `null`, solange er nie Alleinspieler war - nicht 0 Prozent. */
    val soloWinRate: Double? get() = if (soloRounds == 0) null else soloWins.toDouble() / soloRounds

    /**
     * Zu wenige Alleinspiele, um aus der Quote etwas zu lesen. Die Grenze ist
     * gegriffen, aber sie ist besser als der stillschweigende Anspruch, jede
     * Quote sei belastbar.
     */
    val soloSampleIsThin: Boolean get() = soloRounds < THIN_SOLO_SAMPLE

    val averageHalfPointsPerRound: Double?
        get() = if (rounds == 0) null else halfPoints.toDouble() / rounds

    /** Bei Gleichstand der fruehere Abend, damit die Anzeige nicht springt. */
    val bestSession: SessionResult? get() = results.maxWithOrNull(bySessionScore)

    val worstSession: SessionResult? get() = results.minWithOrNull(bySessionScore)

    val favouriteGame: Map.Entry<GameKind, Int>?
        get() = declarations.entries
            .sortedWith(compareByDescending<Map.Entry<GameKind, Int>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()

    private companion object {
        val bySessionScore: Comparator<SessionResult> =
            compareBy<SessionResult> { it.halfPoints }.thenByDescending { it.startedAt }
    }
}

/** Ab hier ist eine Alleinspieler-Quote ueberhaupt der Rede wert. */
const val THIN_SOLO_SAMPLE = 20

/**
 * Rangliste und Spielerstatistik aus den Rohdaten.
 *
 * Bewusst hier und nicht in SQL: die Punkte einer Runde stehen nirgends
 * gespeichert, sie werden mit der fuer den jeweiligen Abend eingefrorenen
 * ScoringConfig gerechnet. Eine Summenspalte in der Datenbank waere schneller
 * und wuerde genau die Eigenschaft aufgeben, um derentwillen Rohdaten
 * gespeichert werden.
 */
object Statistics {

    /** Die Saison eines Abends: das Kalenderjahr seines Anpfiffs. */
    fun seasonOf(state: SessionState, zone: TimeZone): Int =
        state.session.startedAt.toLocalDateTime(zone).year

    /** Alle Saisons, in denen gespielt wurde, die juengste zuerst. */
    fun seasons(states: List<SessionState>, zone: TimeZone): List<Int> =
        states.map { seasonOf(it, zone) }.distinct().sortedDescending()

    fun filter(states: List<SessionState>, period: Period, zone: TimeZone): List<SessionState> =
        when (period) {
            Period.AllTime -> states
            is Period.Season -> states.filter { seasonOf(it, zone) == period.year }
        }

    /**
     * Die Auswertung je Spieler, der beste zuerst.
     *
     * Gezaehlt werden nur Spieler, die auch am Tisch sassen. Ein Kadermitglied
     * ohne eine einzige Runde steht nicht mit einer Reihe Nullen in der
     * Rangliste - es hat nicht 0 Punkte, es hat nicht gespielt.
     */
    fun standings(states: List<SessionState>, roster: List<Player>): List<PlayerStats> {
        val byId = roster.associateBy { it.id }
        val builders = LinkedHashMap<String, Builder>()

        for (state in states) {
            val session = state.session
            val live = state.liveRounds
            if (live.isEmpty()) continue

            val perSeat = LinkedHashMap<Int, Builder.SessionTally>()
            for (round in live) {
                for (seat in 0 until session.seatCount) {
                    val tally = perSeat.getOrPut(seat) { Builder.SessionTally() }
                    tally.halfPoints += (round.score.halfPoints[seat] ?: 0).toLong()
                    if (seat == round.round.sittingOutSeat) continue
                    tally.rounds++
                    if (seat != round.round.declarerSeat) continue
                    tally.soloRounds++
                    // Ueberreizt gilt immer als verloren, unabhaengig vom
                    // Stichergebnis - dieselbe Regel wie in Scoring.score.
                    if (round.round.won && !round.round.overbid) tally.soloWins++
                    val kind = kindOf(round.round.declaration)
                    tally.declarations[kind] = (tally.declarations[kind] ?: 0) + 1
                }
            }

            for ((seat, tally) in perSeat) {
                // Wer an diesem Abend nur gegeben hat, war da, hat aber nicht
                // gespielt. Ein Abend mit null Runden waere in "Abende" eine
                // Zahl, hinter der nichts steht.
                if (tally.rounds == 0) continue
                val playerId = session.seats[seat] ?: continue
                val builder = builders.getOrPut(playerId) {
                    Builder(byId[playerId] ?: unknownPlayer(playerId))
                }
                builder.add(session.id, session.startedAt, tally)
            }
        }

        return builders.values
            .map { it.build() }
            .sortedWith(compareByDescending<PlayerStats> { it.halfPoints }.thenBy { it.player.displayName })
    }

    /**
     * Der laufende Stand je Sitzplatz ueber die Runden eines Abends.
     *
     * Der erste Eintrag ist der Stand VOR der ersten Runde, also 0. Nur lebende
     * Runden zaehlen: eine geloeschte Runde ist nicht passiert, und ein Knick im
     * Verlauf, der zu keiner Runde gehoert, waere nicht zu erklaeren.
     */
    fun cumulativeBySeat(state: SessionState): Map<Int, List<Long>> {
        val seats = (0 until state.session.seatCount).toList()
        val running = seats.associateWith { mutableListOf(0L) }
        for (round in state.liveRounds.sortedBy { it.sequence }) {
            for (seat in seats) {
                val series = running.getValue(seat)
                series += series.last() + (round.score.halfPoints[seat] ?: 0).toLong()
            }
        }
        return running.mapValues { it.value.toList() }
    }

    /**
     * Ein Spieler, den der lokale Kader nicht mehr kennt. Er verschwindet nicht
     * aus der Historie - seine Punkte sind gespielt worden.
     */
    private fun unknownPlayer(playerId: String) = Player(playerId, "Unbekannt", active = false)

    private class Builder(val player: Player) {

        class SessionTally {
            var halfPoints = 0L
            var rounds = 0
            var soloRounds = 0
            var soloWins = 0
            val declarations = linkedMapOf<GameKind, Int>()
        }

        private var halfPoints = 0L
        private var rounds = 0
        private var soloRounds = 0
        private var soloWins = 0
        private val declarations = linkedMapOf<GameKind, Int>()
        private val results = mutableListOf<SessionResult>()

        fun add(sessionId: String, startedAt: Instant, tally: SessionTally) {
            halfPoints += tally.halfPoints
            rounds += tally.rounds
            soloRounds += tally.soloRounds
            soloWins += tally.soloWins
            for ((kind, count) in tally.declarations) {
                declarations[kind] = (declarations[kind] ?: 0) + count
            }
            results += SessionResult(sessionId, startedAt, tally.halfPoints, tally.rounds)
        }

        fun build() = PlayerStats(
            player = player,
            halfPoints = halfPoints,
            sessions = results.size,
            rounds = rounds,
            soloRounds = soloRounds,
            soloWins = soloWins,
            declarations = declarations,
            results = results.sortedBy { it.startedAt },
        )
    }
}
