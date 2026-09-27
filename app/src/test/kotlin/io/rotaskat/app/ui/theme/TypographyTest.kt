package io.rotaskat.app.ui.theme

import androidx.compose.ui.text.TextStyle
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Welche Rolle welche Schrift bekommt, und dass keine die Tabellenziffern verliert. */
class TypographyTest {

    private val t = RotaskatTypography

    @Test
    fun `Titel und Kacheln stehen in Barlow`() {
        for (style in listOf(t.titleLarge, t.titleMedium, t.titleSmall, t.labelLarge, t.headlineSmall)) {
            assertEquals(BarlowSemiCondensed, style.fontFamily)
        }
    }

    @Test
    fun `Fliesstext bleibt Roboto`() {
        for (style in listOf(t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelMedium, t.labelSmall)) {
            assertNotEquals(BarlowSemiCondensed, style.fontFamily)
        }
    }

    @Test
    fun `jede Rolle hat Tabellenziffern und mindestens 14sp`() {
        val all: List<TextStyle> = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            RotaskatTextStyles.gameValue, RotaskatTextStyles.scoreLarge,
            RotaskatTextStyles.scoreMedium, RotaskatTextStyles.standName,
            RotaskatTextStyles.commitPoints, RotaskatTextStyles.sectionLabel,
            RotaskatTextStyles.compact,
        )
        for (style in all) {
            assertEquals("tnum", style.fontFeatureSettings)
            assertTrue(style.fontSize.value >= 14f, "${style.fontSize} < 14sp")
        }
    }
}
