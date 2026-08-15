package io.rotaskat.app.ui.round

import androidx.compose.runtime.Immutable
import io.rotaskat.app.ui.common.label
import io.rotaskat.app.ui.common.modifierLabels
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.RamschResult
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.scoring.RoundScore
import io.rotaskat.shared.scoring.Scoring
import io.rotaskat.shared.scoring.ScoringConfig

/**
 * Die ANSAGE als geordnete Skala.
 *
 * Der wichtigste Entwurfsgriff der Rundeneingabe. `Modifiers` hat sechs
 * Booleans und damit 64 Kombinationen, von denen `normalized()` die allermeisten
 * auf dieselben wenigen gueltigen abbildet - Ouvert erzwingt Hand und Schwarz
 * angesagt, Schwarz angesagt erzwingt Schneider angesagt, und so weiter.
 *
 * Sechs einzelne Schalter waeren also sechs Gelegenheiten, einen Zustand zu
 * bauen, den es nach der Skatordnung gar nicht gibt. Die Kette ist stattdessen
 * genau das, was `normalized()` ohnehin erzwingt, nur sichtbar: eine Auswahl aus
 * fuenf Stufen, von denen jede gueltig ist.
 */
enum class Announcement(val label: String) {
    NONE("keine Ansage"),
    HAND("Hand"),
    SCHNEIDER_ANNOUNCED("Schneider angesagt"),
    SCHWARZ_ANNOUNCED("Schwarz angesagt"),
    OUVERT("Ouvert"),
}

/**
 * Das ERGEBNIS als zweite geordnete Skala.
 *
 * Der haeufigste Zusatz am Tisch ist "Schneider erreicht, nicht angesagt". Mit
 * dieser Skala ist das genau ein Tap.
 */
enum class Achieved(val label: String) {
    NORMAL("normal"),
    SCHNEIDER("Schneider"),
    SCHWARZ("schwarz"),
}

/**
 * Was die Ansage am Ergebnis bereits festlegt.
 *
 * Wer Schwarz ansagt und gewinnt, HAT schwarz gespielt - "normal" ist danach
 * kein erreichbarer Zustand mehr. Die Ergebnis-Skala bekommt daraus ihre
 * Untergrenze, und die Oberflaeche legt die Stufe sofort sichtbar um. Ohne das
 * spraenge der Spielwert scheinbar grundlos nach oben.
 */
val Announcement.impliedAchieved: Achieved
    get() = when (this) {
        Announcement.NONE, Announcement.HAND -> Achieved.NORMAL
        Announcement.SCHNEIDER_ANNOUNCED -> Achieved.SCHNEIDER
        Announcement.SCHWARZ_ANNOUNCED, Announcement.OUVERT -> Achieved.SCHWARZ
    }

/**
 * Baut aus den zwei Skalen die sechs Booleans.
 *
 * Das abschliessende `normalized()` ist bewusst doppelt gemoppelt: die Skalen
 * liefern bereits einen normalisierten Zustand. Es steht da, damit diese
 * Funktion auch dann richtig bleibt, wenn die Skatordnung spaeter eine weitere
 * Implikation bekommt.
 */
fun modifiersOf(announcement: Announcement, achieved: Achieved): Modifiers {
    val effective = maxOf(achieved, announcement.impliedAchieved)
    return Modifiers(
        hand = announcement >= Announcement.HAND,
        schneider = effective >= Achieved.SCHNEIDER,
        schneiderAnnounced = announcement >= Announcement.SCHNEIDER_ANNOUNCED,
        schwarz = effective >= Achieved.SCHWARZ,
        schwarzAnnounced = announcement >= Announcement.SCHWARZ_ANNOUNCED,
        ouvert = announcement == Announcement.OUVERT,
    ).normalized()
}

/** Die Spielart, so wie sie angesagt wird. */
@Immutable
sealed interface GamePick {
    data class Colour(val suit: Suit) : GamePick
    data object Grand : GamePick
    data object Null : GamePick
    data object Ramsch : GamePick
}

/**
 * Die Ramsch-Eingabe.
 *
 * Die Augen werden fuer JEDEN aktiven Spieler erfasst, obwohl das Modell nur
 * die des Verlierers speichert. Grund ist die Hausregel: bei Augengleichstand
 * entscheidet der Tisch, nicht die App - und einen Gleichstand kann nur sehen,
 * wer alle drei Zahlen kennt. Die Summenprobe auf 120 faellt als kostenloser
 * Tippfehler-Test mit ab.
 *
 * Die Augen liegen als Rohtext vor, damit ein geleertes Feld leer bleiben darf
 * und nicht sofort wieder eine 0 anzeigt.
 */
@Immutable
data class RamschDraft(
    val cardPoints: Map<Int, String> = emptyMap(),
    /** Vom Tisch entschieden. Nur noetig, wenn die Augen nicht eindeutig sind. */
    val chosenLoser: Int? = null,
    val jungfrau: Boolean = false,
    val durchmarschSeat: Int? = null,
    val pushes: Int = 0,
) {
    fun pointsOf(seat: Int): Int = cardPoints[seat]?.toIntOrNull() ?: 0

    fun entered(seat: Int): Boolean = !cardPoints[seat].isNullOrBlank()

    /** Erst mit allen drei Zahlen stehen Verlierer und Summenprobe fest. */
    fun complete(activeSeats: List<Int>): Boolean = activeSeats.all { entered(it) }

    fun total(activeSeats: List<Int>): Int = activeSeats.sumOf { pointsOf(it) }

    /**
     * Traegt eine Augenzahl ein und verwirft dabei die Antwort des Tisches.
     *
     * Sie gehoert zu den Zahlen, zu denen sie gegeben wurde: wer nachzaehlt und
     * korrigiert, hat den Gleichstand unter Umstaenden gar nicht mehr, und eine
     * stehengebliebene Antwort wuerde dann still den falschen Verlierer buchen.
     */
    fun withPoints(seat: Int, value: String): RamschDraft =
        copy(cardPoints = cardPoints + (seat to value), chosenLoser = null)

    /** Die Sitzplaetze mit den meisten Augen. Mehr als einer heisst Gleichstand. */
    fun leaders(activeSeats: List<Int>): List<Int> {
        if (activeSeats.isEmpty()) return emptyList()
        val best = activeSeats.maxOf { pointsOf(it) }
        return activeSeats.filter { pointsOf(it) == best }
    }

    fun tied(activeSeats: List<Int>): Boolean = leaders(activeSeats).size > 1

    /**
     * Der Verlierer. Bei eindeutiger Augenzahl ergibt er sich, bei Gleichstand
     * erst durch die Antwort des Tisches - eine automatische Regel gibt es
     * bewusst nicht.
     *
     * Die Reihenfolge ist wesentlich: die Augen entscheiden, [chosenLoser] ist
     * nur die Antwort auf einen Gleichstand und niemals ein Vorrang davor.
     * Andersherum schluege eine einmal gegebene Antwort dauerhaft die
     * eingetippten Zahlen - nullsummig und damit unsichtbar.
     */
    fun loser(activeSeats: List<Int>): Int? {
        val leaders = leaders(activeSeats)
        return leaders.singleOrNull() ?: chosenLoser?.takeIf { it in leaders }
    }
}

/**
 * Der Zustand einer Runde waehrend der Eingabe.
 *
 * Bewusst frei von Compose und Android: die gesamte Fallunterscheidung der
 * Rundeneingabe steckt hier und ist damit ohne Bildschirm nachrechenbar. Der
 * Bildschirm daruber tut nichts weiter, als Felder zu setzen und abgeleitete
 * Werte anzuzeigen.
 *
 * Die Reihenfolge der Felder ist die Reihenfolge der muendlichen Ansage:
 * Alleinspieler, Spielart, Spitzen, Ergebnis. Deshalb beginnt die Eingabe NICHT
 * mit Gewonnen/Verloren - am Tisch wird zuerst angesagt und erst danach
 * gespielt.
 */
@Immutable
data class RoundDraft(
    val roundId: String,
    val seatCount: Int,
    /** Am Vierertisch zugleich der Aussetzende. */
    val dealerSeat: Int,
    val config: ScoringConfig,
    val declarerSeat: Int? = null,
    val game: GamePick? = null,
    val nullVariant: NullVariant? = null,
    val matadors: Int = 1,
    val announcement: Announcement = Announcement.NONE,
    val achieved: Achieved = Achieved.NORMAL,
    val contra: ContraLevel = ContraLevel.NONE,
    val overbid: Boolean = false,
    val bid: Int = Scoring.MIN_BID,
    val ramsch: RamschDraft = RamschDraft(),
    /** Eine bestehende Runde wird korrigiert statt eine neue angelegt. */
    val editing: Boolean = false,
) {

    val sittingOutSeat: Int? get() = if (seatCount == 4) dealerSeat else null

    val seats: List<Int> get() = (0 until seatCount).toList()

    /** Der Aussetzende ist in der Spielerauswahl gar nicht erst antippbar. */
    val activeSeats: List<Int> get() = seats.filter { it != sittingOutSeat }

    val isRamsch: Boolean get() = game == GamePick.Ramsch

    val isNull: Boolean get() = game == GamePick.Null

    /**
     * Ueberreizt gibt es nur bei Farbspiel und Grand.
     *
     * Ein Nullspiel hat einen festen Wert, es laesst sich nicht ueberreizen -
     * und die Oberflaeche blendet den Schalter dort deshalb aus. Was
     * ausgeblendet ist, darf aber auch nicht mehr wirken: sonst bucht ein
     * getipptes "Gewonnen" nach einem Wechsel auf Null still einen Verlust.
     */
    val overbidApplies: Boolean get() = !isNull && !isRamsch

    val effectiveOverbid: Boolean get() = overbid && overbidApplies

    /**
     * Die antippbaren Spitzenzahlen.
     *
     * Beim Grand sind nur die vier Buben Spitzen. Eine Kachel "7" waere dort
     * ein Spiel, das es nicht gibt - mit vervierfachtem Wert und einer
     * Herleitung, die sich trotzdem plausibel liest.
     */
    val matadorOptions: List<Int>
        get() = if (game == GamePick.Grand) GRAND_MATADORS else SUIT_MATADORS

    /** Was das Ergebnis nach der Ansage mindestens sein muss. */
    val achievedFloor: Achieved get() = announcement.impliedAchieved

    val effectiveAchieved: Achieved get() = maxOf(achieved, achievedFloor)

    val modifiers: Modifiers get() = modifiersOf(announcement, achieved)

    /**
     * Was die Badge auf "Zusaetze" zaehlt: alles, was vom Normalfall abweicht.
     * Der eingeklappte Bereich muss von aussen verraten, dass dort etwas liegt.
     */
    val extrasCount: Int
        get() = when {
            isRamsch -> 0
            isNull -> if (contra != ContraLevel.NONE) 1 else 0
            else -> listOf(
                announcement != Announcement.NONE,
                effectiveAchieved != Achieved.NORMAL,
                contra != ContraLevel.NONE,
                effectiveOverbid,
            ).count { it }
        }

    val declaration: Declaration?
        get() = when (game) {
            null -> null
            is GamePick.Colour -> SuitGame(game.suit, matadors, modifiers)
            GamePick.Grand -> GrandGame(matadors, modifiers)
            GamePick.Null -> nullVariant?.let { NullGame(it) }
            GamePick.Ramsch -> RamschGame
        }

    /**
     * Haengt den Entwurf an eine andere Sitzordnung.
     *
     * Alles Eingegebene bleibt stehen, mit genau einer Ausnahme: wer durch die
     * Verschiebung zum Aussetzenden wird, kann nicht mehr Alleinspieler sein und
     * faellt aus der Auswahl. Sonst entstuende eine Runde, die validate() erst
     * beim Speichern ablehnt - also genau dann, wenn niemand mehr weiss, warum.
     */
    fun withDealer(newDealerSeat: Int): RoundDraft {
        val sittingOut = if (seatCount == 4) newDealerSeat else null
        return copy(
            dealerSeat = newDealerSeat,
            declarerSeat = declarerSeat?.takeIf { it != sittingOut },
            ramsch = ramsch.copy(
                chosenLoser = ramsch.chosenLoser?.takeIf { it != sittingOut },
                durchmarschSeat = ramsch.durchmarschSeat?.takeIf { it != sittingOut },
            ),
        )
    }

    /**
     * Wechselt die Spielart und raeumt dabei weg, was zur neuen Ansage nicht
     * mehr passt.
     *
     * Der Wechsel ist die Stelle, an der die Zusage "ungueltige Kombinationen
     * sind strukturell unerreichbar" bricht, wenn nur `game` gesetzt wird: der
     * Ramsch schleppt sonst einen Alleinspieler mit, das Nullspiel ein
     * "ueberreizt", das es dort gar nicht gibt, und der Grand eine Spitzenzahl,
     * die nur ein Farbspiel haben kann.
     */
    fun withGame(pick: GamePick): RoundDraft {
        val next = copy(game = pick)
        return when (pick) {
            GamePick.Ramsch -> next.copy(declarerSeat = null)
            GamePick.Null -> next.copy(overbid = false, bid = Scoring.MIN_BID)
            else -> next.copy(matadors = matadors.coerceIn(1, next.matadorOptions.last()))
        }
    }

    /**
     * Die Runde, wie sie gespeichert wuerde. `null`, solange die Eingabe noch
     * nicht vollstaendig ist.
     */
    fun toRound(won: Boolean): Round? {
        val declaration = declaration ?: return null
        if (declaration is RamschGame) {
            val loser = ramsch.loser(activeSeats) ?: ramsch.durchmarschSeat ?: return null
            return Round(
                id = roundId,
                seatCount = seatCount,
                declarerSeat = null,
                sittingOutSeat = sittingOutSeat,
                declaration = declaration,
                ramsch = RamschResult(
                    loserSeat = loser,
                    cardPoints = ramsch.pointsOf(loser),
                    jungfrau = ramsch.jungfrau,
                    durchmarschSeat = ramsch.durchmarschSeat,
                    pushes = ramsch.pushes,
                ),
                dealerSeat = dealerSeat,
            )
        }
        val declarer = declarerSeat ?: return null
        return Round(
            id = roundId,
            seatCount = seatCount,
            declarerSeat = declarer,
            sittingOutSeat = sittingOutSeat,
            declaration = declaration,
            won = won,
            contra = contra,
            // Nicht am UI-Zweig haengen lassen: was der Bildschirm ausblendet,
            // darf hier nicht mehr in die Rohdaten gelangen.
            bid = if (effectiveOverbid) bid else null,
            overbid = effectiveOverbid,
            dealerSeat = dealerSeat,
        )
    }

    /**
     * Die Abrechnung der noch nicht gespeicherten Runde.
     *
     * Bewusst ueber [Scoring.score] statt ueber eine eigene Rechnung: die Zahl
     * ueber den Ergebnisknoepfen soll nicht ungefaehr das sein, was gespeichert
     * wird, sondern exakt dasselbe. Eine zweite Formel in der Oberflaeche waere
     * genau die Art von stiller Abweichung, gegen die die Anzeige schuetzen soll.
     */
    fun scoreOrNull(won: Boolean): RoundScore? {
        val round = toRound(won) ?: return null
        if (Scoring.validate(round).isNotEmpty()) return null
        return Scoring.score(round, config)
    }

    /** Der Spielwert. Vom Ausgang unabhaengig, deshalb reicht ein Aufruf. */
    val gameValue: Int? get() = scoreOrNull(won = true)?.gameValue

    /**
     * Beim Ramsch reicht der berechnete Wert nicht als Freigabe.
     *
     * Ein leeres Feld liefert 0 Augen, damit ist nach einer einzigen Zahl
     * bereits ein eindeutiger "Verlierer" da - und die Summenprobe, das
     * eigentliche Sicherheitsnetz der Ramsch-Eingabe, ist noch gar nicht
     * sichtbar. Erst mit allen drei Zahlen steht die Runde.
     */
    val readyForResult: Boolean
        get() = gameValue != null && (!isRamsch || ramschReady)

    /**
     * Beim Durchmarsch gibt es nichts zu zaehlen, und bei einer Korrektur steht
     * der Verlierer bereits in den Rohdaten - die Augen der beiden anderen sind
     * dort nie gespeichert worden und lassen sich nicht rekonstruieren. Ohne
     * diese Ausnahme liesse sich eine gespeicherte Ramschrunde nur noch loeschen
     * statt korrigieren.
     */
    private val ramschReady: Boolean
        get() = ramsch.durchmarschSeat != null || editing || ramsch.complete(activeSeats)

    /** Von wem die Augen noch fehlen. Traegt den Hinweis unter der Eingabe. */
    val missingRamschSeats: List<Int> get() = activeSeats.filterNot { ramsch.entered(it) }

    /** Was der Alleinspieler bei diesem Ausgang bekaeme, in halben Punkten. */
    fun declarerHalfPoints(won: Boolean): Int? {
        val seat = declarerSeat ?: return null
        return scoreOrNull(won)?.halfPoints?.get(seat)
    }

    /**
     * Die Herleitung unter dem Spielwert.
     *
     * Sie ist der eigentliche Grund, warum der Spielwert nie eingetippt wird:
     * ein Reizwert allein ist mehrdeutig - 36 ist Kreuz mit 2 genauso wie Karo
     * mit 3 -, die Herleitung dagegen ist gegen die Ansage am Tisch pruefbar.
     */
    fun derivation(): String? = when (val declaration = declaration) {
        null -> null
        is SuitGame -> suitDerivation(declaration.suit.label, declaration.suit.baseValue, declaration.matadors)
        is GrandGame -> suitDerivation("Grand", Scoring.GRAND_BASE, declaration.matadors)
        is NullGame -> "${declaration.variant.label} = ${declaration.variant.gameValue}" + contraSuffix()
        is RamschGame -> ramschDerivation()
    }

    private fun suitDerivation(name: String, base: Int, matadors: Int): String {
        val levels = modifierLabels(modifiers)
        val level = Scoring.gameLevel(matadors, modifiers)
        val announcedPart = if (levels.isEmpty()) "" else ", " + levels.joinToString(", ")
        val regular = "$name mit $matadors$announcedPart = $base x $level = ${base * level}"
        if (!effectiveOverbid) return regular + contraSuffix()
        // Ueberreizt: der regulaere Wert traegt nicht mehr, gerechnet wird mit
        // dem kleinsten Vielfachen des Grundwerts, das den Reizwert erreicht.
        val declaration = declaration ?: return regular
        val overbidValue = Scoring.overbidValue(declaration, bid)
        return "$regular, ueberreizt auf $bid = $overbidValue" + contraSuffix()
    }

    private fun ramschDerivation(): String {
        val durchmarsch = ramsch.durchmarschSeat
        if (durchmarsch != null) return "Durchmarsch = ${config.durchmarschValue}"
        val loser = ramsch.loser(activeSeats) ?: return "Augen fehlen noch"
        val base = ramsch.pointsOf(loser)
        val parts = buildList {
            add("$base Augen")
            if (config.jungfrauDoubles && ramsch.jungfrau) add("x 2 (Jungfrau)")
            if (config.pushDoubles && ramsch.pushes > 0) {
                add("x ${1 shl ramsch.pushes} (${ramsch.pushes} Schub)")
            }
        }
        return parts.joinToString(" ") + " = ${gameValue ?: base}"
    }

    private fun contraSuffix(): String = when (contra) {
        ContraLevel.NONE -> ""
        else -> " x ${contra.multiplier} (${contra.label}) = ${gameValue ?: 0}"
    }

    companion object {
        /**
         * Die Spitzenzahlen je Spielart. Die Grenzen kommen aus [Scoring] und
         * nicht aus einer Zahl in der Oberflaeche: was hier antippbar ist, muss
         * dasselbe sein, was die Abrechnung fuer moeglich haelt.
         */
        val SUIT_MATADORS = (1..Scoring.MAX_SUIT_MATADORS).toList()

        val GRAND_MATADORS = (1..Scoring.MAX_GRAND_MATADORS).toList()

        /**
         * Die Reizstufen der Skatordnung. Nur fuer den Ueberreizt-Fall
         * gebraucht, und dort als Liste statt als freies Zahlenfeld: ein
         * getippter Reizwert, den es nicht gibt, waere still falsch.
         */
        val BIDS = listOf(
            18, 20, 22, 23, 24, 27, 30, 33, 35, 36, 40, 44, 45, 46, 48, 50, 54, 55,
            59, 60, 63, 66, 70, 72, 77, 80, 81, 84, 88, 90, 96, 99, 100, 108, 110,
            117, 120, 121, 126, 130, 132, 135, 140, 144, 150, 153, 156, 160, 162,
            165, 168, 170, 171, 180, 187, 192, 198, 204, 216, 240, 264,
        )

        /**
         * Baut den Entwurf fuer die naechste Runde eines Abends. Der Geber und
         * damit der Aussetzende kommen aus der automatisch fortgeschriebenen
         * Rotation, nicht aus einer Rueckfrage.
         */
        fun forNextRound(
            roundId: String,
            seatCount: Int,
            dealerSeat: Int,
            config: ScoringConfig,
        ): RoundDraft = RoundDraft(
            roundId = roundId,
            seatCount = seatCount,
            dealerSeat = dealerSeat,
            config = config,
        )

        /** Baut den Entwurf aus einer bereits gespeicherten Runde. */
        fun fromRound(round: Round, config: ScoringConfig): RoundDraft {
            val declaration = round.declaration
            val modifiers = when (declaration) {
                is SuitGame -> declaration.modifiers.normalized()
                is GrandGame -> declaration.modifiers.normalized()
                else -> Modifiers()
            }
            return RoundDraft(
                roundId = round.id,
                seatCount = round.seatCount,
                dealerSeat = round.dealerSeat,
                config = config,
                declarerSeat = round.declarerSeat,
                game = when (declaration) {
                    is SuitGame -> GamePick.Colour(declaration.suit)
                    is GrandGame -> GamePick.Grand
                    is NullGame -> GamePick.Null
                    is RamschGame -> GamePick.Ramsch
                },
                nullVariant = (declaration as? NullGame)?.variant,
                matadors = when (declaration) {
                    is SuitGame -> declaration.matadors
                    is GrandGame -> declaration.matadors
                    else -> 1
                },
                announcement = announcementOf(modifiers),
                achieved = achievedOf(modifiers),
                contra = round.contra,
                overbid = round.overbid,
                bid = round.bid ?: Scoring.MIN_BID,
                ramsch = round.ramsch?.let { result ->
                    RamschDraft(
                        // Gespeichert sind nur die Augen des Verlierers, die der
                        // beiden anderen sind aus den Rohdaten nicht
                        // rekonstruierbar. chosenLoser bleibt deshalb leer: als
                        // Vorbelegung wuerde er die neu eingetippten Augen
                        // dauerhaft schlagen und der Verlierer einer Ramschrunde
                        // waere ueber die Oberflaeche nicht mehr korrigierbar.
                        cardPoints = mapOf(result.loserSeat to result.cardPoints.toString()),
                        jungfrau = result.jungfrau,
                        durchmarschSeat = result.durchmarschSeat,
                        pushes = result.pushes,
                    )
                } ?: RamschDraft(),
                editing = true,
            )
        }

        private fun announcementOf(modifiers: Modifiers): Announcement = when {
            modifiers.ouvert -> Announcement.OUVERT
            modifiers.schwarzAnnounced -> Announcement.SCHWARZ_ANNOUNCED
            modifiers.schneiderAnnounced -> Announcement.SCHNEIDER_ANNOUNCED
            modifiers.hand -> Announcement.HAND
            else -> Announcement.NONE
        }

        private fun achievedOf(modifiers: Modifiers): Achieved = when {
            modifiers.schwarz -> Achieved.SCHWARZ
            modifiers.schneider -> Achieved.SCHNEIDER
            else -> Achieved.NORMAL
        }
    }
}
