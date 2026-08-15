package io.rotaskat.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.rotaskat.app.data.RotaskatGraph
import io.rotaskat.app.data.SyncTrigger
import io.rotaskat.app.data.net.ApiUnauthorizedException
import io.rotaskat.app.data.net.NotJoinedException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "RotaskatSync"

/**
 * Der Sync als Hintergrundarbeit.
 *
 * Die harte Regel dieser Klasse: sie darf den Eingabepfad nie blockieren. Es
 * gibt deshalb keinen Rueckweg zur Oberflaeche - kein Ergebnis, kein Fehler,
 * kein Spinner zwischen zwei Runden. Was schiefgeht, landet im Log und im
 * naechsten Versuch. Die Alternative waere ein Fehlerdialog in der Kneipe,
 * genau dort, wo die App gar kein Netz haben soll.
 */
class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val engine = RotaskatGraph.get(applicationContext).syncEngine
        return try {
            val summary = engine.syncOnce()
            Log.i(TAG, "Sync fertig: $summary")
            Result.success()
        } catch (cancellation: CancellationException) {
            // Der WorkManager hat die Arbeit abgebrochen, etwa weil das Netz
            // wegfiel. Durchreichen, sonst zaehlt der Abbruch als Fehlversuch.
            throw cancellation
        } catch (notJoined: NotJoinedException) {
            // Noch kein Vereinsbeitritt. Kein Fehler, nur nichts zu tun.
            Log.i(TAG, "Sync uebersprungen: ${notJoined.message}")
            Result.success()
        } catch (unauthorized: ApiUnauthorizedException) {
            // Ein zurueckgezogenes Token wird durch Wiederholen nicht besser.
            Log.w(TAG, "Sync abgelehnt, Geraet muss neu beitreten", unauthorized)
            Result.success()
        } catch (error: Throwable) {
            val attempt = runAttemptCount + 1
            Log.w(TAG, "Sync fehlgeschlagen (Versuch $attempt von $MAX_ATTEMPTS)", error)
            // Nach der letzten Wiederholung wird bewusst success gemeldet, nicht
            // failure: die Runden bleiben als pendingSync markiert und der
            // naechste Anlass - die naechste Runde, der naechste periodische Lauf
            // - nimmt sie mit. Ein failure wuerde die Arbeit endgueltig
            // verwerfen und den Abend am Server vorbeilaufen lassen.
            if (attempt >= MAX_ATTEMPTS) Result.success() else Result.retry()
        }
    }

    companion object {
        /**
         * Ein einziger Name fuer die gesamte App. Zusammen mit
         * [ExistingWorkPolicy.KEEP] verhindert er Sync-Stuerme: nach jeder Runde
         * wird angestossen, aber es laeuft immer hoechstens ein Auftrag.
         */
        const val UNIQUE_NAME = "rotaskat-sync"

        const val PERIODIC_NAME = "rotaskat-sync-periodic"

        /**
         * Danach gibt der Lauf auf und ueberlaesst es dem naechsten Anlass. Acht
         * Versuche mit exponentiellem Backoff ab 30 Sekunden decken gut zwei
         * Stunden ab, also einen ganzen Spielabend ohne Empfang.
         */
        private const val MAX_ATTEMPTS = 8

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        /**
         * Das Sicherheitsnetz. Der Anstoss nach jeder Runde kann eine Luecke
         * lassen: faellt die letzte Runde eines Abends genau zwischen den
         * letzten Lauf und dessen Ende, wartet sie sonst bis zum naechsten
         * Rundeneintrag - und den gibt es an diesem Abend nicht mehr.
         */
        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, oneTimeRequest())
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest(),
            )
        }

        /** Der Ausloeser, den das Repository nach jeder Schreiboperation zieht. */
        fun trigger(context: Context): SyncTrigger {
            val appContext = context.applicationContext
            return SyncTrigger { enqueue(appContext) }
        }
    }
}
