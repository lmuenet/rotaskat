package io.rotaskat.shared.scoring

import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.SuitGame
import kotlinx.serialization.Serializable

/**
 * Hausregeln, die sich zwischen Runden nicht aendern sollten. Werden pro
 * Skatverein gespeichert, damit eine spaetere Regelaenderung nachvollziehbar
 * bleibt und die Historie mit den damals gueltigen Regeln neu berechnet
 * werden kann.
 */
@Serializable
data class ScoringConfig(
    /** Jungfrau (ein Spieler ohne Stich) verdoppelt die Augen im Ramsch. */
    val jungfrauDoubles: Boolean = true,
    /** Jeder Schub beim Schieberamsch verdoppelt die Augen. */
    val pushDoubles: Boolean = true,
    /** Spielwert eines Durchmarsch, gewertet wie ein gewonnener Grand. */
    val durchmarschValue: Int = 120,
)

/**
 * Ergebnis einer Runde in HALBEN Punkten: gespeicherter Wert = Punkte * 2.
 *
 * Der Grund ist die Verteilungsregel, bei der die Gegenspieler den halben
 * Spielwert bekommen. Bei ungeradem Spielwert (etwa Null mit 23) waere das
 * ein Bruch. In halben Punkten bleibt alles ganzzahlig, die Nullsumme exakt,
 * und es gibt ueber tausende Runden keinen Rundungsdrift.
 */
data class RoundScore(
    /** Sitzplatz -> halbe Punkte. Enthaelt alle Sitzplaetze, Aussetzer mit 0. */
    val halfPoints: Map<Int, Int>,
    /** Der zur Abrechnung herangezogene Spielwert. */
    val gameValue: Int,
) {
    /** Punkte als Dezimalzahl, nur fuer die Anzeige. */
    val points: Map<Int, Double> get() = halfPoints.mapValues { it.value / 2.0 }

    /** Muss fuer jede gueltige Runde 0 ergeben. */
    fun sum(): Int = halfPoints.values.sum()
}

object Scoring {

    /**
     * Stand des Abrechnungsalgorithmus. Wird pro Session mitgeschrieben, damit
     * eine Abweichung zwischen APK und Server als Versionsdrift erkennbar ist
     * und nicht als unerklaerlicher Rechenfehler. Hochzaehlen, sobald sich das
     * Ergebnis von [score] fuer gleiche Rohdaten aendert.
     */
    const val SCORING_VERSION = 1

    /** Grundwert des Grand nach Skatordnung. */
    const val GRAND_BASE = 24

    /** Hoechstmoegliche Augenzahl einer Partei. */
    const val MAX_CARD_POINTS = 120

    /**
     * Hoechste Spitzenzahl eines Farbspiels: vier Buben plus die sieben
     * Trumpfkarten der Farbe.
     */
    const val MAX_SUIT_MATADORS = 11

    /** Beim Grand sind ausschliesslich die vier Buben Spitzen. */
    const val MAX_GRAND_MATADORS = 4

    /** Niedrigstes moegliches Reizgebot. */
    const val MIN_BID = 18

    /**
     * Hoechster regulaerer Spielwert: Grand ouvert mit 4 ergibt 24 * 11 = 264.
     * Hoeher kann auch nicht gereizt werden.
     */
    const val MAX_BID = 264

    /**
     * Obergrenze fuer Schuebe beim Schieberamsch. Real sind es hoechstens
     * zwei bis drei, die Grenze existiert vor allem, damit die Verdopplung
     * `1 shl pushes` nicht ins Int-Overflow laufen kann.
     */
    const val MAX_PUSHES = 5

    /**
     * Die zulaessigen Spitzenzahlen einer Ansage. `null` heisst: diese Spielart
     * kennt keine Spitzen.
     *
     * Die Grenze steht hier und nicht nur in der Oberflaeche: eine Zusage, die
     * ausschliesslich in einer Kachelreihe lebt, gilt fuer alles nicht, was
     * ueber den Sync hereinkommt.
     */
    fun matadorRange(declaration: Declaration): IntRange? = when (declaration) {
        is SuitGame -> 1..MAX_SUIT_MATADORS
        is GrandGame -> 1..MAX_GRAND_MATADORS
        is NullGame, is RamschGame -> null
    }

    /**
     * Spielstufe: Spitzen + 1 fuer das Spiel selbst + je 1 pro Zusatzstufe.
     */
    fun gameLevel(matadors: Int, modifiers: Modifiers): Int =
        matadors + 1 + modifiers.levelCount()

    /** Grundwert der Spielart, ohne Stufen. */
    fun baseValue(declaration: Declaration): Int = when (declaration) {
        is SuitGame -> declaration.suit.baseValue
        is GrandGame -> GRAND_BASE
        is NullGame -> declaration.variant.gameValue
        is RamschGame -> 0
    }

    /** Regulaerer Spielwert, also der Reizwert des tatsaechlich gespielten Spiels. */
    fun gameValue(declaration: Declaration): Int = when (declaration) {
        is SuitGame -> product(
            declaration.suit.baseValue,
            gameLevel(declaration.matadors, declaration.modifiers),
            "Spielwert von $declaration",
        )

        is GrandGame -> product(
            GRAND_BASE,
            gameLevel(declaration.matadors, declaration.modifiers),
            "Spielwert von $declaration",
        )

        is NullGame -> declaration.variant.gameValue
        is RamschGame -> 0
    }

    /**
     * Bei Ueberreizung zaehlt das kleinste Vielfache des Grundwerts, das den
     * Reizwert erreicht. Nullspiele haben feste Werte und kennen das nicht.
     */
    fun overbidValue(declaration: Declaration, bid: Int): Int = when (declaration) {
        is NullGame -> declaration.variant.gameValue
        is RamschGame -> 0
        else -> {
            val base = baseValue(declaration)
            val target = maxOf(gameValue(declaration), bid)
            // Aufrundende Division statt Hochzaehlen in einer Schleife: bei
            // einem grossen bid liefe der Zaehler sonst ins Int-Overflow und
            // die Schleife wuerde nie terminieren.
            ceilToMultiple(target, base)
        }
    }

    /**
     * Jede Multiplikation der Abrechnung laeuft ueber diese beiden Helfer.
     *
     * Die Nullsummen-Invariante ist overflow-blind: `2V - V - V` ergibt auch
     * dann exakt 0, wenn V modulo 2^32 gekippt ist. Ein Ueberlauf bliebe also
     * unentdeckt, bis er in der All-Time-Rangliste steht. Deshalb wird in Long
     * gerechnet und der Bereich geprueft, statt still zu wrappen.
     */
    private fun product(left: Int, right: Int, what: String): Int =
        checked(left.toLong() * right.toLong(), what)

    private fun checked(value: Long, what: String): Int {
        require(value in INT_RANGE) { "$what laeuft ueber: $value passt nicht in einen Int" }
        return value.toInt()
    }

    private val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

    /** Kleinstes Vielfaches von [multiple], das [value] erreicht. */
    private fun ceilToMultiple(value: Int, multiple: Int): Int {
        require(multiple > 0) { "multiple muss positiv sein" }
        if (value <= multiple) return multiple
        // Zwischenrechnung in Long, damit auch ein absurd hoher Wert nicht
        // still ins Negative kippt.
        val rounded = ((value.toLong() + multiple - 1) / multiple) * multiple
        return rounded.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Rechnet eine Runde ab.
     *
     * Verteilung eines normalen Spiels mit Spielwert V:
     *  - Alleinspieler gewinnt: Alleinspieler +V, jeder Gegenspieler -V/2
     *  - Alleinspieler verliert: Alleinspieler -2V, jeder Gegenspieler +V
     *
     * Ramsch mit Augenzahl A des Verlierers:
     *  - Verlierer -A, jeder andere +A/2
     *
     * Ein Aussetzender am Vierertisch bekommt immer 0.
     */
    fun score(round: Round, config: ScoringConfig = ScoringConfig()): RoundScore {
        validate(round).let { errors ->
            require(errors.isEmpty()) { "Ungueltige Runde ${round.id}: ${errors.joinToString("; ")}" }
        }

        val halfPoints = round.seats.associateWith { 0 }.toMutableMap()
        val active = round.activeSeats

        if (round.declaration is RamschGame) {
            val ramsch = round.ramsch!!

            val durchmarsch = ramsch.durchmarschSeat
            if (durchmarsch != null) {
                // Durchmarsch gewinnt wie ein Grand, also nach normaler Verteilung.
                val value = config.durchmarschValue
                halfPoints[durchmarsch] = product(2, value, "Durchmarsch der Runde ${round.id}")
                active.filter { it != durchmarsch }.forEach { halfPoints[it] = -value }
                return RoundScore(halfPoints, value)
            }

            var cardPoints = ramsch.cardPoints.toLong()
            if (config.jungfrauDoubles && ramsch.jungfrau) cardPoints *= 2
            if (config.pushDoubles && ramsch.pushes > 0) cardPoints *= (1L shl ramsch.pushes)
            val augen = checked(cardPoints, "Augenwert der Runde ${round.id}")

            halfPoints[ramsch.loserSeat] = checked(-2L * cardPoints, "Ramsch der Runde ${round.id}")
            active.filter { it != ramsch.loserSeat }.forEach { halfPoints[it] = augen }
            return RoundScore(halfPoints, augen)
        }

        val declarer = round.declarerSeat!!
        val rawValue = if (round.overbid && round.bid != null) {
            overbidValue(round.declaration, round.bid)
        } else {
            gameValue(round.declaration)
        }
        val value = product(rawValue, round.contra.multiplier, "Spielwert der Runde ${round.id}")

        // Ueberreizt gilt immer als verloren, unabhaengig vom Stichergebnis.
        val lost = round.overbid || !round.won
        val opponents = active.filter { it != declarer }

        if (lost) {
            halfPoints[declarer] = product(-4, value, "Verlust der Runde ${round.id}")
            opponents.forEach { halfPoints[it] = product(2, value, "Gewinn der Runde ${round.id}") }
        } else {
            halfPoints[declarer] = product(2, value, "Gewinn der Runde ${round.id}")
            opponents.forEach { halfPoints[it] = -value }
        }
        return RoundScore(halfPoints, value)
    }

    /**
     * Prueft eine Runde auf strukturelle Fehler. Leere Liste heisst gueltig.
     */
    fun validate(round: Round): List<String> {
        val errors = mutableListOf<String>()

        if (round.seatCount !in 3..4) {
            errors += "seatCount muss 3 oder 4 sein, ist ${round.seatCount}"
            return errors
        }
        val range = 0 until round.seatCount

        if (round.dealerSeat !in range) {
            errors += "dealerSeat ${round.dealerSeat} liegt ausserhalb des Tisches"
        }

        if (round.seatCount == 4) {
            val out = round.sittingOutSeat
            if (out == null) {
                errors += "sittingOutSeat ist am Vierertisch Pflicht"
            } else if (out !in range) {
                errors += "sittingOutSeat $out liegt ausserhalb des Tisches"
            } else if (out != round.dealerSeat) {
                // Sonst rotiert die App an einer anderen Stellung weiter, als
                // die Runde abgerechnet wurde.
                errors += "Am Vierertisch muss der Geber aussetzen: dealerSeat=${round.dealerSeat}, sittingOutSeat=$out"
            }
        } else if (round.sittingOutSeat != null) {
            errors += "sittingOutSeat darf am Dreiertisch nicht gesetzt sein"
        }

        if (round.declaration is RamschGame) {
            if (round.declarerSeat != null) errors += "Ramsch hat keinen Alleinspieler"
            val ramsch = round.ramsch
            if (ramsch == null) {
                errors += "Ramsch braucht ein RamschResult"
            } else {
                if (ramsch.loserSeat !in range) {
                    errors += "loserSeat ${ramsch.loserSeat} liegt ausserhalb des Tisches"
                } else if (ramsch.loserSeat == round.sittingOutSeat) {
                    errors += "Der Aussetzende kann den Ramsch nicht verlieren"
                }
                if (ramsch.cardPoints !in 0..MAX_CARD_POINTS) {
                    errors += "cardPoints ${ramsch.cardPoints} liegt ausserhalb 0..$MAX_CARD_POINTS"
                }
                val durchmarsch = ramsch.durchmarschSeat
                if (durchmarsch != null && durchmarsch !in range) {
                    errors += "durchmarschSeat $durchmarsch liegt ausserhalb des Tisches"
                } else if (durchmarsch != null && durchmarsch == round.sittingOutSeat) {
                    errors += "Der Aussetzende kann keinen Durchmarsch machen"
                }
                if (ramsch.pushes !in 0..MAX_PUSHES) {
                    errors += "pushes ${ramsch.pushes} liegt ausserhalb 0..$MAX_PUSHES"
                }
            }
        } else {
            val declarer = round.declarerSeat
            if (declarer == null) {
                errors += "declarerSeat ist ausserhalb des Ramsch Pflicht"
            } else if (declarer !in range) {
                errors += "declarerSeat $declarer liegt ausserhalb des Tisches"
            } else if (declarer == round.sittingOutSeat) {
                errors += "Der Aussetzende kann nicht Alleinspieler sein"
            }
            if (round.ramsch != null) errors += "RamschResult nur bei Ramsch erlaubt"

            val matadors = when (val d = round.declaration) {
                is SuitGame -> d.matadors
                is GrandGame -> d.matadors
                else -> null
            }
            val allowed = matadorRange(round.declaration)
            if (matadors != null && allowed != null && matadors !in allowed) {
                // Die Obergrenze ist keine Schikane: ein Grand mit 7 gibt es
                // nicht, waere aber nullsummig und damit fuer jede Invariante
                // unsichtbar - und beim Farbspiel liegt dahinter der Ueberlauf.
                errors += "Spitzenzahl muss zwischen ${allowed.first} und ${allowed.last} liegen, ist $matadors"
            }
            if (round.overbid && round.bid == null) {
                errors += "Ueberreizt ohne Reizwert laesst sich nicht abrechnen"
            }
            val bid = round.bid
            if (bid != null && bid !in MIN_BID..MAX_BID) {
                errors += "Reizwert $bid liegt ausserhalb $MIN_BID..$MAX_BID"
            }
        }

        return errors
    }
}
