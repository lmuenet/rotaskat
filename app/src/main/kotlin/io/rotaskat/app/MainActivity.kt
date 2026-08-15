package io.rotaskat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.sync.SyncWorker
import io.rotaskat.app.ui.nav.RotaskatApp

/**
 * Der Einstieg.
 *
 * Er tut genau zwei Dinge: er reicht den Graphen der Datenschicht in die
 * Oberflaeche, und er meldet den periodischen Sync an. Der periodische Lauf
 * gehoert hierher und nicht in den Sync selbst - er ist das Sicherheitsnetz
 * fuer die letzte Runde eines Abends, nach der niemand mehr etwas eintraegt und
 * damit auch nichts mehr anstoesst. Die KEEP-Politik des Auftrags macht den
 * Aufruf bei jedem App-Start folgenlos.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = RotaskatGraph.get(this)
        SyncWorker.schedulePeriodic(this)
        setContent {
            RotaskatApp(graph)
        }
    }
}
