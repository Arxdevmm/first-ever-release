package com.artesys.netcon

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class BatteryOptimizationHelperTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun reportsPowerManagerBatteryOptimizationExemptionState() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadowPower = Shadows.shadowOf(powerManager)

        shadowPower.setIgnoringBatteryOptimizations(context.packageName, false)
        assertFalse(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))

        shadowPower.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    @Test
    fun buildsRequestIntentWhenNotIgnoredAndSettingsListWhenAlreadyIgnored() {
        val enableIntent = BatteryOptimizationHelper.buildToggleIntent(context, currentlyIgnored = false)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, enableIntent.action)
        assertEquals("package:${context.packageName}", enableIntent.dataString)

        val disableIntent = BatteryOptimizationHelper.buildToggleIntent(context, currentlyIgnored = true)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, disableIntent.action)

        val fallback = BatteryOptimizationHelper.buildFallbackSettingsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fallback.action)
        assertEquals("package:${context.packageName}", fallback.dataString)
    }
}
