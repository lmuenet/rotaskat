package io.rotaskat.app.data.sync

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Auftrag selbst, ohne ihn laufen zu lassen.
 *
 * Was hier geprueft wird, sind genau die drei Eigenschaften, deren Verlust man
 * im Betrieb erst merkt, wenn es zu spaet ist: ohne Netz-Constraint laeuft der
 * Sync in der Kneipe in jede Sekunde Funkloch, ohne exponentielles Backoff
 * frisst er den Akku, und ohne einheitlichen Namen loest jede Runde einen
 * eigenen Auftrag aus.
 */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerRequestTest {

    @Test
    fun `Der Sync laeuft nur mit Netz`() {
        val request = SyncWorker.oneTimeRequest()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `Wiederholt wird mit exponentiellem Backoff`() {
        val spec = SyncWorker.oneTimeRequest().workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertTrue(spec.backoffDelayDuration >= 30_000, "Backoff startet zu frueh: ${spec.backoffDelayDuration}")
    }

    @Test
    fun `Auch der periodische Lauf haengt am Netz`() {
        val spec = SyncWorker.periodicRequest().workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertTrue(spec.isPeriodic)
    }

    @Test
    fun `Einmaliger und periodischer Lauf haben verschiedene Namen`() {
        // Sonst wuerde der Anstoss nach jeder Runde das Sicherheitsnetz
        // verdraengen oder umgekehrt.
        assertTrue(SyncWorker.UNIQUE_NAME != SyncWorker.PERIODIC_NAME)
    }
}
