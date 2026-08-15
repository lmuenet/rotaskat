package io.rotaskat.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Das Theme der App.
 *
 * Es nimmt bewusst KEINEN `darkTheme`-Parameter und kein Dynamic Color. Beides
 * waere ein Schalter fuer eine Frage, die der Nutzungskontext bereits
 * beantwortet: gespielt wird abends bei schlechtem Licht, und die Farben fuer
 * Gewinn und Verlust muessen ueber alle Geraete hinweg dieselben sein. Ein
 * Hellmodus haette nur einen Effekt, naemlich dass ihn irgendwann jemand
 * versehentlich einschaltet.
 */
@Composable
fun RotaskatTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Das XML-Theme erbt von einem HELLEN Plattform-Theme, damit die App
            // ohne die Material-Components-Library auskommt. Die Systemleisten
            // stuenden damit auf dunklen Symbolen und waeren auf der dunklen
            // Flaeche unsichtbar; umgestellt wird deshalb hier.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalScoreColors provides RotaskatScoreColorsDark) {
        MaterialTheme(
            colorScheme = RotaskatColorScheme,
            typography = RotaskatTypography,
            content = content,
        )
    }
}

/** Kurzform fuer die Gewinn- und Verlustfarben. */
val MaterialTheme.scoreColors: RotaskatScoreColors
    @Composable
    get() = LocalScoreColors.current
