package io.rotaskat.app.ui.common

import androidx.annotation.DrawableRes
import io.rotaskat.app.R
import io.rotaskat.shared.model.ContraLevel
import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.RamschGame
import io.rotaskat.shared.model.SuitGame
import io.rotaskat.shared.model.Suit

/**
 * Die Benennungen am Tisch.
 *
 * Sie folgen der muendlichen Ansage und nicht den Bezeichnern im Modell:
 * gesagt wird "Kreuz", nicht "CLUBS", und "mit zwei", nicht "matadors=2".
 * Wo die Oberflaeche anders heisst als der Tisch, wird beim Gegenlesen
 * uebersetzt statt gelesen.
 */
val Suit.label: String
    get() = when (this) {
        Suit.DIAMONDS -> "Karo"
        Suit.HEARTS -> "Herz"
        Suit.SPADES -> "Pik"
        Suit.CLUBS -> "Kreuz"
    }

/**
 * Das Farbsymbol als Vector-Drawable, nicht als Zeichen.
 *
 * Die Unicode-Zeichen ♦♥♠♣ rendern auf Android als farbige Emoji - Karo und
 * Herz kommen dann grell rot, unabhaengig vom Kupferton der Oberflaeche, und
 * verletzen damit die Regel, dass Rot nur Gewinn/Verlust markiert.
 */
val Suit.icon: Int
    @DrawableRes get() = when (this) {
        Suit.DIAMONDS -> R.drawable.ic_suit_diamonds
        Suit.HEARTS -> R.drawable.ic_suit_hearts
        Suit.SPADES -> R.drawable.ic_suit_spades
        Suit.CLUBS -> R.drawable.ic_suit_clubs
    }

val NullVariant.label: String
    get() = when (this) {
        NullVariant.NULL -> "Null"
        NullVariant.NULL_HAND -> "Null Hand"
        NullVariant.NULL_OUVERT -> "Null Ouvert"
        NullVariant.NULL_HAND_OUVERT -> "Null Hand Ouvert"
    }

val ContraLevel.label: String
    get() = when (this) {
        ContraLevel.NONE -> "kein Kontra"
        ContraLevel.KONTRA -> "Kontra"
        ContraLevel.RE -> "Re"
    }

/** Kurzbeschreibung eines Spiels fuer die Rundenliste. */
fun Declaration.label(): String = when (this) {
    is SuitGame -> buildString {
        append(suit.label)
        append(" mit ")
        append(matadors)
        modifierLabels(modifiers.normalized()).forEach { append(", ").append(it) }
    }

    is GrandGame -> buildString {
        append("Grand mit ")
        append(matadors)
        modifierLabels(modifiers.normalized()).forEach { append(", ").append(it) }
    }

    is NullGame -> variant.label
    is RamschGame -> "Ramsch"
}

/**
 * Die gesetzten Stufen als Wortliste, in der Reihenfolge, in der sie angesagt
 * werden. Die von `normalized()` mitgesetzten Stufen sind dabei absichtlich
 * enthalten: sie stehen im Spielwert, also muessen sie auch dastehen.
 */
fun modifierLabels(modifiers: io.rotaskat.shared.model.Modifiers): List<String> = buildList {
    if (modifiers.hand) add("Hand")
    if (modifiers.schneider) add("Schneider")
    if (modifiers.schneiderAnnounced) add("Schneider angesagt")
    if (modifiers.schwarz) add("Schwarz")
    if (modifiers.schwarzAnnounced) add("Schwarz angesagt")
    if (modifiers.ouvert) add("Ouvert")
}
