package io.rotaskat.app.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Die Bildschirmwechsel.
 *
 * Kurz: 150ms hinein, 100ms hinaus. Die Crossfades der Vorgabe dauerten im
 * Geraetetest gut eine halbe Sekunde, das fuehlte sich traege an und liess
 * Taps auf dem alten Bildschirm landen. Zwischen den Zielen der unteren Leiste
 * nur ein Fade, sonst ein kleiner Versatz in Laufrichtung.
 */
internal object RotaskatTransitions {

    private const val ENTER_MS = 150
    private const val EXIT_MS = 100

    private fun lateral(from: String?, to: String?): Boolean =
        !(Routes.isTopLevel(from) && Routes.isTopLevel(to))

    fun enter(from: String?, to: String?): EnterTransition =
        if (lateral(from, to)) {
            fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { it / 12 }
        } else {
            fadeIn(tween(ENTER_MS))
        }

    fun popEnter(from: String?, to: String?): EnterTransition =
        if (lateral(from, to)) {
            fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { -it / 12 }
        } else {
            fadeIn(tween(ENTER_MS))
        }

    fun exit(): ExitTransition = fadeOut(tween(EXIT_MS))
}

/**
 * Sperrt einen Bildschirm fuer Eingaben, sobald er verlassen wird.
 *
 * Ein Tap kurz nach "Zurueck" traf sonst noch den alten Bildschirm - im
 * Geraetetest oeffnete das den Dialog "Abend beenden?" eines Abends, den man
 * gerade verlassen hatte. Der Abfangmodifier haengt darum von Anfang an am
 * Bildschirm, nicht erst ab dem Moment des Verlassens: ein neu eingehaengtes
 * `pointerInput` braucht selbst ein paar Frames, bis seine Coroutine anlaeuft,
 * und genau in dieser Anlaufzeit waere der erste Tap nach "Zurueck" wieder
 * durchgerutscht. Stattdessen entscheidet jedes Event fuer sich, ob es
 * verbraucht wird.
 */
@Composable
internal fun AnimatedVisibilityScope.LeavingGuard(content: @Composable () -> Unit) {
    val leaving by rememberUpdatedState(transition.targetState != EnterExitState.Visible)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (leaving) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) { content() }
}
