package io.rotaskat.shared.model

import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Farbe eines Farbspiels mit ihrem Grundwert nach Skatordnung.
 */
@Serializable
enum class Suit(val baseValue: Int) {
    DIAMONDS(9),
    HEARTS(10),
    SPADES(11),
    CLUBS(12),
}

/**
 * Nullspiele haben feste Spielwerte, unabhaengig von Spitzen und Stufen.
 */
@Serializable
enum class NullVariant(val gameValue: Int) {
    NULL(23),
    NULL_HAND(35),
    NULL_OUVERT(46),
    NULL_HAND_OUVERT(59),
}

/**
 * Hausregel: Kontra verdoppelt, Re vervierfacht den Spielwert.
 */
@Serializable
enum class ContraLevel(val multiplier: Int) {
    NONE(1),
    KONTRA(2),
    RE(4),
}

/**
 * Zusatzstufen eines Farb- oder Grandspiels. Jede gesetzte Stufe erhoeht
 * die Spielstufe um 1.
 */
@Serializable
data class Modifiers(
    val hand: Boolean = false,
    /** Schneider erreicht (Gegner unter 31 Augen bzw. Alleinspieler unter 31). */
    val schneider: Boolean = false,
    val schneiderAnnounced: Boolean = false,
    /** Schwarz erreicht (Gegenpartei ohne Stich). */
    val schwarz: Boolean = false,
    val schwarzAnnounced: Boolean = false,
    val ouvert: Boolean = false,
) {
    /**
     * Setzt die von der Skatordnung implizierten Stufen. Schneider angesagt
     * setzt Hand und Schneider voraus, Schwarz angesagt zusaetzlich Schneider
     * angesagt, und Ouvert impliziert Schwarz angesagt.
     */
    fun normalized(): Modifiers {
        var m = this
        if (m.ouvert) m = m.copy(hand = true, schwarzAnnounced = true)
        if (m.schwarzAnnounced) m = m.copy(hand = true, schwarz = true, schneiderAnnounced = true)
        if (m.schneiderAnnounced) m = m.copy(hand = true, schneider = true)
        if (m.schwarz) m = m.copy(schneider = true)
        return m
    }

    /** Anzahl der Stufen, die zur Spielstufe addiert werden. */
    fun levelCount(): Int = normalized().let {
        listOf(
            it.hand,
            it.schneider,
            it.schneiderAnnounced,
            it.schwarz,
            it.schwarzAnnounced,
            it.ouvert,
        ).count { flag -> flag }
    }
}

/**
 * Das angesagte Spiel. Bestimmt zusammen mit [Modifiers] den Spielwert.
 */
@Serializable
sealed interface Declaration

@Serializable
@SerialName("suit")
data class SuitGame(
    val suit: Suit,
    /** Spitzenzahl, also "mit N" oder "ohne N". Immer positiv. */
    val matadors: Int,
    val modifiers: Modifiers = Modifiers(),
) : Declaration

@Serializable
@SerialName("grand")
data class GrandGame(
    val matadors: Int,
    val modifiers: Modifiers = Modifiers(),
) : Declaration

@Serializable
@SerialName("null")
data class NullGame(
    val variant: NullVariant,
) : Declaration

@Serializable
@SerialName("ramsch")
data object RamschGame : Declaration

/**
 * Ergebnis eines Ramsch/Schieberamsch.
 *
 * Wertung im Rotaskat: der Spieler mit den meisten Augen bekommt diese als
 * Minuspunkte, die uebrigen beiden Spieler jeweils die Haelfte davon als
 * Pluspunkte. Damit ist der Ramsch genauso nullsummig wie ein normales Spiel.
 */
@Serializable
data class RamschResult(
    /** Sitzplatz mit den meisten Augen. */
    val loserSeat: Int,
    /** Augen des Verlierers, 0..120. */
    val cardPoints: Int,
    /** Ein Spieler hat keinen Stich bekommen. */
    val jungfrau: Boolean = false,
    /** Sitzplatz des Spielers mit allen Stichen, sonst null. */
    val durchmarschSeat: Int? = null,
    /** Anzahl der Schuebe des Skats beim Schieberamsch. */
    val pushes: Int = 0,
)

/**
 * Eine gespielte Runde. Bewusst als Rohdatensatz modelliert: es werden die
 * Spielfakten gespeichert, nicht die berechneten Punkte. Damit laesst sich die
 * gesamte Historie neu berechnen, wenn sich eine Hausregel aendert.
 */
@Serializable
data class Round(
    val id: String,
    /** Anzahl Spieler am Tisch, 3 oder 4. Sitzplaetze sind 0-basiert. */
    val seatCount: Int,
    /** Alleinspieler. Bei Ramsch null. */
    val declarerSeat: Int?,
    /** Aussetzender Sitzplatz. Pflicht bei seatCount == 4, sonst null. */
    val sittingOutSeat: Int?,
    val declaration: Declaration,
    /** Hat der Alleinspieler gewonnen? Bei Ramsch ohne Bedeutung. */
    val won: Boolean = false,
    val contra: ContraLevel = ContraLevel.NONE,
    /**
     * Reizwert, auf den der Alleinspieler gereizt hat. Nur noetig, wenn
     * ueberreizt wurde, dann bestimmt er den Verlustwert.
     */
    val bid: Int? = null,
    /** Alleinspieler hat sich ueberreizt: Spiel gilt immer als verloren. */
    val overbid: Boolean = false,
    val ramsch: RamschResult? = null,
    /**
     * Sitzplatz des Gebers. Am Vierertisch ist er derselbe wie
     * [sittingOutSeat] - wer gibt, setzt aus. Beide Felder bleiben trotzdem
     * gespeichert: [sittingOutSeat] ist das, was die Abrechnung braucht,
     * [dealerSeat] das, was die Rotation fortschreibt. Der Vorgabewert haelt
     * beide fuer bestehende Rohdaten automatisch konsistent.
     */
    val dealerSeat: Int = sittingOutSeat ?: 0,
) {
    /** Alle Sitzplaetze am Tisch. */
    val seats: List<Int> get() = (0 until seatCount).toList()

    /** Sitzplaetze, die an dieser Runde aktiv beteiligt sind. */
    val activeSeats: List<Int> get() = seats.filter { it != sittingOutSeat }

    /** Sitzordnung dieser Runde, Ausgangspunkt fuer die naechste. */
    fun rotation(): TableRotation = TableRotation(seatCount, dealerSeat)
}

/**
 * Der Zustand der Sitzordnung zwischen zwei Runden.
 *
 * Am Vierertisch gibt es keinen zweiten Freiheitsgrad: aus dem Geber folgt der
 * Aussetzende. Deshalb wird nur der Geber fortgeschrieben und der Aussetzende
 * abgeleitet - sonst koennten beide Werte auseinanderlaufen, und der Tisch
 * saehe einen anderen Aussetzenden als die Abrechnung.
 *
 * Bewusst nicht serialisierbar: der Zustand ist aus der Runde ableitbar und
 * hat in den Rohdaten nichts zu suchen.
 */
data class TableRotation(
    val seatCount: Int,
    val dealerSeat: Int,
) {
    init {
        require(seatCount in 3..4) { "seatCount muss 3 oder 4 sein, ist $seatCount" }
        require(dealerSeat in 0 until seatCount) { "dealerSeat $dealerSeat liegt ausserhalb des Tisches" }
    }

    /** Am Vierertisch setzt der Geber aus, am Dreiertisch spielt er mit. */
    val sittingOutSeat: Int? get() = sittingOutSeatFor(dealerSeat, seatCount)

    /** Sitzplaetze, die in dieser Stellung spielen. */
    val activeSeats: List<Int> get() = (0 until seatCount).filter { it != sittingOutSeat }

    /** Naechste Runde: das Geben wandert einen Platz weiter. */
    fun next(): TableRotation = copy(dealerSeat = nextDealerSeat(dealerSeat, seatCount))

    /** Korrektur am Tisch: ein Tap setzt den Geber direkt. */
    fun withDealer(seat: Int): TableRotation = copy(dealerSeat = seat)

    companion object {
        fun nextDealerSeat(dealerSeat: Int, seatCount: Int): Int {
            require(seatCount > 0) { "seatCount muss positiv sein, ist $seatCount" }
            return Math.floorMod(dealerSeat + 1, seatCount)
        }

        fun sittingOutSeatFor(dealerSeat: Int, seatCount: Int): Int? =
            if (seatCount == 4) dealerSeat else null
    }
}

/**
 * Ein Mitglied des Vereinskaders. Gastspieler gibt es bewusst nicht, wer
 * nicht im Kader steht, kann nicht erfasst werden.
 */
@Serializable
data class Player(
    val id: String,
    val displayName: String,
    val active: Boolean = true,
)

/**
 * Ein Skatverein mit seinen Hausregeln.
 *
 * Die Regeln haengen am Verein, nicht am Code: aendert der Verein etwas, ist
 * die Historie mit den damals gueltigen Regeln nachrechenbar, statt still
 * umgedeutet zu werden.
 */
@Serializable
data class Club(
    val id: String,
    val name: String,
    val scoring: ScoringConfig = ScoringConfig(),
    /**
     * Cent je (ganzem) Punkt. Ganzzahlig, damit die Abrechnung nie in
     * Fliesskomma rechnen muss.
     */
    val centsPerPoint: Int = DEFAULT_CENTS_PER_POINT,
    val roster: List<Player> = emptyList(),
) {
    init {
        require(centsPerPoint >= 0) { "centsPerPoint darf nicht negativ sein, ist $centsPerPoint" }
    }

    companion object {
        const val DEFAULT_CENTS_PER_POINT = 10
    }
}

@Serializable
enum class SessionStatus { OPEN, CLOSED }

/**
 * Ein Spielabend.
 *
 * [scoring], [centsPerPoint] und [scoringVersion] sind Kopien der
 * Vereinseinstellung zum Zeitpunkt des Anpfiffs, nicht Verweise darauf.
 * Sonst saehe der Spieler am Tisch andere Zahlen als spaeter in der
 * Rangliste, sobald jemand eine Hausregel aendert. [scoringVersion] haelt
 * zusaetzlich fest, mit welchem Stand des Algorithmus gerechnet wurde -
 * damit ist eine Abweichung zwischen APK und Server erkennbar statt bloss
 * verwirrend.
 */
@Serializable
data class Session(
    val id: String,
    val clubId: String,
    val seatCount: Int,
    /** Sitzplatz -> Spieler-Id. Sitzplaetze sind 0-basiert. */
    val seats: Map<Int, String> = emptyMap(),
    val status: SessionStatus = SessionStatus.OPEN,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val scoring: ScoringConfig = ScoringConfig(),
    val scoringVersion: Int = Scoring.SCORING_VERSION,
    val centsPerPoint: Int = Club.DEFAULT_CENTS_PER_POINT,
    /** Geber der naechsten Runde, wird nach jeder Runde fortgeschrieben. */
    val dealerSeat: Int = 0,
) {
    /** Friert die aktuellen Vereinsregeln fuer diesen Abend ein. */
    companion object {
        fun startedFor(
            id: String,
            club: Club,
            seatCount: Int,
            seats: Map<Int, String>,
            startedAt: Instant,
            dealerSeat: Int = 0,
        ): Session = Session(
            id = id,
            clubId = club.id,
            seatCount = seatCount,
            seats = seats,
            startedAt = startedAt,
            scoring = club.scoring,
            centsPerPoint = club.centsPerPoint,
            dealerSeat = dealerSeat,
        )
    }

    val rotation: TableRotation get() = TableRotation(seatCount, dealerSeat)

    /** Sitzordnung nach einer gespielten Runde. */
    fun withNextDealer(): Session = copy(dealerSeat = rotation.next().dealerSeat)
}
