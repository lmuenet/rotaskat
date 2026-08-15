package io.rotaskat.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Haelt das Display an, solange [enabled] gilt.
 *
 * Der groesste Zeitfresser am Tisch ist nicht die Eingabe, sondern das
 * Aufwecken und Entsperren zwischen zwei Runden. Ein ausgegangener Bildschirm
 * kostet regelmaessig mehr Sekunden als der komplette Vier-Tap-Ablauf.
 *
 * Bewusst an den laufenden Abend gebunden und nicht an die App: sobald der
 * Abend beendet oder die Rundeneingabe verlassen ist, gilt wieder die
 * Systemeinstellung. Sonst leuchtet das Handy den ganzen Heimweg.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
