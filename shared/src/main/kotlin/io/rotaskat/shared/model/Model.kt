package io.rotaskat.shared.model

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
) {
    /** Alle Sitzplaetze am Tisch. */
    val seats: List<Int> get() = (0 until seatCount).toList()

    /** Sitzplaetze, die an dieser Runde aktiv beteiligt sind. */
    val activeSeats: List<Int> get() = seats.filter { it != sittingOutSeat }
}
