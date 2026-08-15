package io.rotaskat.shared.scoring

import io.rotaskat.shared.model.Declaration
import io.rotaskat.shared.model.GrandGame
import io.rotaskat.shared.model.Modifiers
import io.rotaskat.shared.model.NullGame
import io.rotaskat.shared.model.NullVariant
import io.rotaskat.shared.model.Round
import io.rotaskat.shared.model.Suit
import io.rotaskat.shared.model.SuitGame
import java.io.File

/**
 * Erzeugt die vollstaendige Spielwerttabelle.
 *
 * Der Raum aller legalen Ansagen ist klein genug, um ihn abzuzaehlen statt zu
 * bestichproben: vier Farben und Grand, dazu die Spitzenzahlen und die vom
 * Regelwerk erlaubten Stufenkombinationen. Was hier herauskommt, wird als
 * Golden-Datei eingecheckt; eine Regelaenderung ist damit ein lesbarer Diff
 * statt einer stillen Verschiebung ueber tausende Runden.
 */
object GameValueTable {

    /**
     * Die Spitzengrenzen stehen in [Scoring] und werden hier nur benutzt. Als
     * eigene Konstanten waren sie die Stelle, an der die Tabelle etwas anderes
     * fuer moeglich hielt als die Produktivpruefung.
     */
    const val MAX_SUIT_MATADORS = Scoring.MAX_SUIT_MATADORS

    const val MAX_GRAND_MATADORS = Scoring.MAX_GRAND_MATADORS

    const val HEADER =
        "spiel\tspitzen\thand\tschneider\tschneiderAngesagt\tschwarz\tschwarzAngesagt\touvert\t" +
            "stufe\tspielwert\tallein_gewonnen\tgegner_gewonnen\tallein_verloren\tgegner_verloren"

    /**
     * Alle vom Regelwerk unterscheidbaren Stufenkombinationen.
     *
     * Aufgezaehlt wird ueber alle 64 Rohkombinationen; [Modifiers.normalized]
     * faltet die impliziten Stufen zusammen, uebrig bleiben genau die
     * ansagbaren Zustaende.
     */
    fun legalModifiers(): List<Modifiers> {
        val all = mutableListOf<Modifiers>()
        for (bits in 0 until 64) {
            all += Modifiers(
                hand = bits and 1 != 0,
                schneider = bits and 2 != 0,
                schneiderAnnounced = bits and 4 != 0,
                schwarz = bits and 8 != 0,
                schwarzAnnounced = bits and 16 != 0,
                ouvert = bits and 32 != 0,
            ).normalized()
        }
        return all.distinct().sortedWith(
            compareBy(
                { it.levelCount() },
                { it.hand },
                { it.schneider },
                { it.schneiderAnnounced },
                { it.schwarz },
                { it.schwarzAnnounced },
                { it.ouvert },
            )
        )
    }

    /** Alle legalen Ansagen in stabiler Reihenfolge. */
    fun allDeclarations(): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        for (suit in Suit.entries) {
            for (matadors in 1..MAX_SUIT_MATADORS) {
                for (modifiers in legalModifiers()) {
                    declarations += SuitGame(suit, matadors, modifiers)
                }
            }
        }
        for (matadors in 1..MAX_GRAND_MATADORS) {
            for (modifiers in legalModifiers()) {
                declarations += GrandGame(matadors, modifiers)
            }
        }
        for (variant in NullVariant.entries) {
            declarations += NullGame(variant)
        }
        return declarations
    }

    /** Die Tabelle als TSV-Zeilen, Kopfzeile inklusive. */
    fun rows(): List<String> = listOf(HEADER) + allDeclarations().map { row(it) }

    private fun row(declaration: Declaration): String {
        val won = score(declaration, won = true)
        val lost = score(declaration, won = false)
        val modifiers = modifiersOf(declaration)
        return listOf(
            name(declaration),
            matadorsOf(declaration)?.toString() ?: "-",
            flag(modifiers.hand),
            flag(modifiers.schneider),
            flag(modifiers.schneiderAnnounced),
            flag(modifiers.schwarz),
            flag(modifiers.schwarzAnnounced),
            flag(modifiers.ouvert),
            levelOf(declaration)?.toString() ?: "-",
            Scoring.gameValue(declaration).toString(),
            won.halfPoints.getValue(0).toString(),
            won.halfPoints.getValue(1).toString(),
            lost.halfPoints.getValue(0).toString(),
            lost.halfPoints.getValue(1).toString(),
        ).joinToString("\t")
    }

    private fun score(declaration: Declaration, won: Boolean): RoundScore = Scoring.score(
        Round(
            id = "table",
            seatCount = 3,
            declarerSeat = 0,
            sittingOutSeat = null,
            declaration = declaration,
            won = won,
        )
    )

    private fun name(declaration: Declaration): String = when (declaration) {
        is SuitGame -> when (declaration.suit) {
            Suit.DIAMONDS -> "karo"
            Suit.HEARTS -> "herz"
            Suit.SPADES -> "pik"
            Suit.CLUBS -> "kreuz"
        }

        is GrandGame -> "grand"
        is NullGame -> when (declaration.variant) {
            NullVariant.NULL -> "null"
            NullVariant.NULL_HAND -> "null_hand"
            NullVariant.NULL_OUVERT -> "null_ouvert"
            NullVariant.NULL_HAND_OUVERT -> "null_hand_ouvert"
        }

        else -> error("Ramsch hat keine Ansage")
    }

    private fun matadorsOf(declaration: Declaration): Int? = when (declaration) {
        is SuitGame -> declaration.matadors
        is GrandGame -> declaration.matadors
        else -> null
    }

    private fun levelOf(declaration: Declaration): Int? = when (declaration) {
        is SuitGame -> Scoring.gameLevel(declaration.matadors, declaration.modifiers)
        is GrandGame -> Scoring.gameLevel(declaration.matadors, declaration.modifiers)
        else -> null
    }

    /** Nullspiele kennen keine Stufen, ihre Varianten tragen sie im Namen. */
    private fun modifiersOf(declaration: Declaration): Modifiers = when (declaration) {
        is SuitGame -> declaration.modifiers.normalized()
        is GrandGame -> declaration.modifiers.normalized()
        is NullGame -> when (declaration.variant) {
            NullVariant.NULL -> Modifiers()
            NullVariant.NULL_HAND -> Modifiers(hand = true)
            NullVariant.NULL_OUVERT -> Modifiers(ouvert = true)
            NullVariant.NULL_HAND_OUVERT -> Modifiers(hand = true, ouvert = true)
        }

        else -> Modifiers()
    }

    private fun flag(value: Boolean): String = if (value) "x" else "-"
}

/**
 * Schreibt die Golden-Datei neu. Aufruf ausschliesslich von Hand ueber
 * `:shared:generateGameValueTable`, niemals aus einem Test heraus: die Datei
 * ist die Referenz, gegen die geprueft wird. Wuerde sie automatisch
 * mitwandern, wuerde jede versehentliche Regelaenderung ihren eigenen Beweis
 * gleich mitliefern.
 */
fun main() {
    val target = File("src/test/resources/scoring/spielwerte.tsv")
    target.parentFile.mkdirs()
    target.writeText(GameValueTable.rows().joinToString("\n", postfix = "\n"))
    println("Geschrieben: ${target.absolutePath} (${GameValueTable.rows().size - 1} Ansagen)")
}
