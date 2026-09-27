package io.rotaskat.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Absturz beim ersten Tap am Tisch.
 *
 * `vibrate()` wirft ohne VIBRATE-Berechtigung eine SecurityException, und
 * `hasVibrator()` prueft nur die Hardware. Weder assembleDebug noch die
 * bisherigen Tests haben das gefunden: in Vorschau und Tests greift
 * [RotaskatHaptics.None], der echte Vibrator wurde nie angefasst.
 */
@RunWith(RobolectricTestRunner::class)
class HapticsTest {

    @Test
    fun `das Manifest fordert die Vibrationsberechtigung an`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertTrue(Manifest.permission.VIBRATE in requested, "Angefordert: $requested")
    }

    @Test
    fun `eine verweigerte Vibration reisst die Eingabe nicht mit`() {
        var attempts = 0
        // Verhaelt sich wie das Geraet ohne Berechtigung.
        val haptics = RotaskatHaptics(
            RotaskatHaptics.Output {
                attempts++
                throw SecurityException("vibrate: Neither user nor current process has android.permission.VIBRATE.")
            },
        )

        haptics.select()
        haptics.commit()
        haptics.failure()

        assertEquals(3, attempts)
    }

    @Test
    fun `der echte Vibrator des Kontexts laesst sich ansprechen`() {
        val haptics = RotaskatHaptics.forContext(ApplicationProvider.getApplicationContext())
        haptics.select()
        haptics.commit()
        haptics.failure()
    }
}
