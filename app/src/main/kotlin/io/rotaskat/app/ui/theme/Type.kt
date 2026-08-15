package io.rotaskat.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * OpenType-Feature fuer dicktengleiche Ziffern.
 *
 * Ohne sie ist die "1" schmaler als die "8", und eine Punktetabelle springt bei
 * jeder Runde seitlich hin und her. Am Tisch wird die Spalte im Vorbeigehen
 * abgelesen; wandert sie, muss jedes Mal neu gesucht werden.
 */
private const val TABULAR_FIGURES = "tnum"

private fun TextStyle.tabular(size: Int? = null): TextStyle = copy(
    fontFeatureSettings = TABULAR_FIGURES,
    fontSize = size?.sp ?: fontSize,
    lineHeight = if (size != null) (size * 1.35f).sp else lineHeight,
)

/**
 * Die Typografie der App.
 *
 * Zwei Abweichungen vom Material-Vorgabesatz, beide aus dem Nutzungskontext:
 * jede Rolle bekommt Tabellenziffern, und die Fliesstext-Rollen liegen bei
 * mindestens 16sp statt bei 14sp. Gelesen wird schraeg von der Seite, aus etwa
 * einem Meter Entfernung, bei Kneipenlicht - 14sp ist dort geraten, nicht
 * gelesen.
 */
val RotaskatTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.tabular(),
        displayMedium = displayMedium.tabular(),
        displaySmall = displaySmall.tabular(),
        headlineLarge = headlineLarge.tabular(),
        headlineMedium = headlineMedium.tabular(),
        headlineSmall = headlineSmall.tabular(),
        titleLarge = titleLarge.tabular(),
        titleMedium = titleMedium.tabular(18),
        titleSmall = titleSmall.tabular(16),
        bodyLarge = bodyLarge.tabular(17),
        bodyMedium = bodyMedium.tabular(16),
        bodySmall = bodySmall.tabular(15),
        labelLarge = labelLarge.tabular(16),
        labelMedium = labelMedium.tabular(15),
        labelSmall = labelSmall.tabular(14),
    )
}

/**
 * Rollen, die es im Material-Satz nicht gibt: der live gerechnete Spielwert und
 * die Punktespalten.
 *
 * Der Spielwert ist die einzige Zahl auf dem Bildschirm, die vor dem Speichern
 * gegengelesen wird. Er ist deshalb absichtlich groesser als jede Ueberschrift.
 */
object RotaskatTextStyles {

    val gameValue = TextStyle(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    val scoreLarge = TextStyle(
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )

    val scoreMedium = TextStyle(
        fontSize = 19.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = TABULAR_FIGURES,
    )
}
