package io.rotaskat.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.rotaskat.app.R

/**
 * OpenType-Feature fuer dicktengleiche Ziffern.
 *
 * Ohne sie ist die "1" schmaler als die "8", und eine Punktetabelle springt bei
 * jeder Runde seitlich hin und her. Am Tisch wird die Spalte im Vorbeigehen
 * abgelesen; wandert sie, muss jedes Mal neu gesucht werden. Barlow hat
 * standardmaessig proportionale Ziffern - ohne dieses Feature waere es dort
 * genauso.
 */
private const val TABULAR_FIGURES = "tnum"

/**
 * Die Schrift fuer Namen, Zahlen, Titel und Kacheln.
 *
 * Halbschmal, damit Namen bis zehn Zeichen im Stand ganz stehen: bei vier
 * Spalten auf einem 360dp-Telefon bleiben je Spalte rund 80dp, und in Roboto
 * wurde dort "Johannes" gekuerzt. Fliesstext bleibt Roboto - laengere Saetze
 * liest man in einer schmalen Schrift schlechter.
 */
val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

private fun TextStyle.tabular(size: Int? = null): TextStyle = copy(
    fontFeatureSettings = TABULAR_FIGURES,
    fontSize = size?.sp ?: fontSize,
    lineHeight = if (size != null) (size * 1.35f).sp else lineHeight,
)

private fun TextStyle.barlow(weight: FontWeight, size: Int? = null): TextStyle =
    tabular(size).copy(fontFamily = BarlowSemiCondensed, fontWeight = weight)

/**
 * Die Typografie der App.
 *
 * Abweichungen vom Material-Vorgabesatz, alle aus dem Nutzungskontext: jede
 * Rolle bekommt Tabellenziffern, die Fliesstext-Rollen liegen bei mindestens
 * 16sp statt bei 14sp, und nichts liegt unter 14sp. Gelesen wird schraeg von
 * der Seite, aus etwa einem Meter Entfernung, bei Kneipenlicht - 14sp ist dort
 * geraten, nicht gelesen.
 */
val RotaskatTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.barlow(FontWeight.Bold),
        displayMedium = displayMedium.barlow(FontWeight.Bold),
        displaySmall = displaySmall.barlow(FontWeight.Bold),
        headlineLarge = headlineLarge.barlow(FontWeight.Bold),
        headlineMedium = headlineMedium.barlow(FontWeight.Bold),
        headlineSmall = headlineSmall.barlow(FontWeight.Bold),
        titleLarge = titleLarge.barlow(FontWeight.SemiBold, 22),
        titleMedium = titleMedium.barlow(FontWeight.SemiBold, 18),
        // Die Beschriftung der Auswahlkacheln.
        titleSmall = titleSmall.barlow(FontWeight.SemiBold, 18),
        bodyLarge = bodyLarge.tabular(17),
        bodyMedium = bodyMedium.tabular(16),
        bodySmall = bodySmall.tabular(15),
        labelLarge = labelLarge.barlow(FontWeight.SemiBold, 16),
        labelMedium = labelMedium.tabular(15),
        labelSmall = labelSmall.tabular(14),
    )
}

/**
 * Rollen, die es im Material-Satz nicht gibt.
 *
 * Der Spielwert ist die einzige Zahl auf dem Bildschirm, die vor dem Speichern
 * gegengelesen wird. Er ist deshalb absichtlich groesser als jede Ueberschrift.
 */
object RotaskatTextStyles {

    val gameValue = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 60.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Punkte im Stand. */
    val scoreLarge = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Punkte in Listen und in der Undo-Zeile. */
    val scoreMedium = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Namen im Stand. */
    val standName = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Die Punkte auf "Gewonnen" und "Verloren". */
    val commitPoints = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /**
     * Abschnittslabels ("Alleinspieler", "Spitzen"). Klein, gesperrt und
     * gedaempft, damit sie ordnen, ohne mit den Kacheln zu konkurrieren.
     */
    val sectionLabel = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.06.em,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    /** Eine Zeile mit Name und Spiel, z. B. in der Undo-Zeile. */
    val compact = TextStyle(
        fontFamily = BarlowSemiCondensed,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}
