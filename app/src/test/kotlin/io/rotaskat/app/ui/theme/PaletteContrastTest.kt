package io.rotaskat.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

/**
 * WCAG AA fuer die Palette: Text 4,5:1, Symbole und Raender 3:1.
 *
 * Gemessen wird gegen den Grund, auf dem die Farbe tatsaechlich steht. Ein
 * Wert, der knapp scheitert, wird aufgehellt - der Grenzwert bleibt.
 */
class PaletteContrastTest {

    private val scheme = RotaskatColorScheme
    private val score = RotaskatScoreColorsDark
    private val accent = RotaskatAccentColorsDark

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb).toDouble() / min(la, lb).toDouble()
    }

    private fun assertContrast(minimum: Double, pairs: Map<String, Pair<Color, Color>>) {
        for ((name, pair) in pairs) {
            val value = ratio(pair.first, pair.second)
            assertTrue(value >= minimum, "$name: ${"%.2f".format(value)} < $minimum")
        }
    }

    @Test
    fun `Text erreicht 4,5 zu 1`() = assertContrast(
        4.5,
        mapOf(
            "Haupttext auf Grund" to (scheme.onSurface to scheme.background),
            "Nebentext auf Grund" to (scheme.onSurfaceVariant to scheme.background),
            "Nebentext auf Kachel" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
            "Label auf Grund" to (accent.labelMuted to scheme.background),
            "Label auf Kachel" to (accent.labelMuted to scheme.surfaceContainer),
            "Label auf Leiste" to (accent.labelMuted to scheme.surfaceContainerLow),
            "gewaehlte Kachel" to (scheme.onPrimaryContainer to scheme.primaryContainer),
            "Gewonnen-Button" to (score.onGainContainer to score.gainContainer),
            "Verloren-Button" to (score.onLossContainer to score.lossContainer),
            "Gewinn auf Grund" to (score.gain to scheme.background),
            "Verlust auf Grund" to (score.loss to scheme.background),
            "Gold-Button" to (scheme.onPrimary to scheme.primary),
            "Menue" to (scheme.onSurface to scheme.surfaceContainerHigh),
            "Gold im Menue" to (scheme.primary to scheme.surfaceContainerHigh),
        ),
    )

    @Test
    fun `Symbole und Raender erreichen 3 zu 1`() = assertContrast(
        3.0,
        mapOf(
            "Auswahlrand auf Kachel" to (scheme.primary to scheme.surfaceContainer),
            "Karo/Herz auf Kachel" to (accent.suitRed to scheme.surfaceContainer),
            "Pik/Kreuz auf Kachel" to (accent.suitBlack to scheme.surfaceContainer),
            "deaktivierter Umriss" to (accent.disabledOutline to scheme.background),
        ),
    )
}
