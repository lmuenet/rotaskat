package io.rotaskat.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Die Rueckmeldung der App.
 *
 * Haptik ist hier der PRIMAERE Kanal, nicht die Zugabe: am Tisch liegt der
 * Blick auf den Karten und den Mitspielern, nicht auf dem Bildschirm. Ein
 * spuerbarer Doppelpuls sagt "gespeichert", ohne dass jemand hinsehen muss.
 *
 * Audio gibt es bewusst nicht. In einer Kneipe ist jeder Ton entweder
 * unhoerbar oder stoerend, und lauter drehen loest das nicht.
 *
 * Die drei Muster sind absichtlich klar unterscheidbar - ein Signal, das man
 * verwechseln kann, ist keines:
 *
 *  - [select]  kurzer Klick beim Antippen einer Auswahl
 *  - [commit]  Doppelpuls, wenn eine Runde in der Datenbank steht
 *  - [failure] langer Puls, wenn eine Eingabe abgelehnt wurde
 */
@Stable
class RotaskatHaptics(private val vibrator: Vibrator?) {

    fun select() = play(SELECT)

    fun commit() = play(COMMIT)

    fun failure() = play(FAILURE)

    private fun play(timings: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        // createWaveform statt der einfachen Dauer, weil nur so der Doppelpuls
        // als EIN Effekt ankommt. Zwei Einzelaufrufe kurz nacheinander werden
        // vom System zusammengezogen und sind vom Klick nicht zu unterscheiden.
        device.vibrate(VibrationEffect.createWaveform(timings, NO_REPEAT))
    }

    companion object {
        private const val NO_REPEAT = -1

        // Erster Wert ist immer die Wartezeit vor dem ersten Puls.
        private val SELECT = longArrayOf(0, 12)
        private val COMMIT = longArrayOf(0, 18, 55, 18)
        private val FAILURE = longArrayOf(0, 170)

        /** Fuer Vorschauen und Tests: alles ist erlaubt, nichts passiert. */
        val None = RotaskatHaptics(null)

        fun forContext(context: Context): RotaskatHaptics {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            return RotaskatHaptics(vibrator)
        }
    }
}

/**
 * Vorbelegt mit dem stummen Exemplar. Eine Vorschau oder ein Test, der das
 * Theme nicht aufbaut, soll nicht am fehlenden Vibrator scheitern.
 */
val LocalHaptics = staticCompositionLocalOf { RotaskatHaptics.None }

@Composable
fun rememberRotaskatHaptics(): RotaskatHaptics {
    val context = LocalContext.current
    return remember(context) { RotaskatHaptics.forContext(context) }
}
