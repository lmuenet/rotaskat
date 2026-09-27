package io.rotaskat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.settings.AppMode
import io.rotaskat.app.data.sync.SyncWorker
import io.rotaskat.app.ui.nav.RotaskatApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Der Einstieg.
 *
 * Er tut genau zwei Dinge: er reicht den Graphen der Datenschicht in die
 * Oberflaeche, und er meldet den periodischen Sync an. Der periodische Lauf
 * gehoert hierher und nicht in den Sync selbst - er ist das Sicherheitsnetz
 * fuer die letzte Runde eines Abends, nach der niemand mehr etwas eintraegt und
 * damit auch nichts mehr anstoesst. Die KEEP-Politik des Auftrags macht den
 * Aufruf bei jedem App-Start folgenlos.
 *
 * Angemeldet wird er nur mit Verein. Ohne Verein gibt es keinen Server, und ein
 * Auftrag, der alle halbe Stunde nur feststellt, dass es nichts zu tun gibt,
 * ist reine Batterie. Weil der Modus beobachtet wird, greift der Beitritt
 * sofort und nicht erst beim naechsten App-Start.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = RotaskatGraph.get(this)
        lifecycleScope.launch {
            graph.settings.mode.distinctUntilChanged().collect { mode ->
                if (mode == AppMode.CLUB) {
                    SyncWorker.schedulePeriodic(this@MainActivity)
                } else {
                    SyncWorker.cancelPeriodic(this@MainActivity)
                }
            }
        }
        setContent {
            RotaskatApp(graph)
        }
    }
}
