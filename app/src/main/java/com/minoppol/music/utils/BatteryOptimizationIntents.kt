package com.minoppol.music.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import timber.log.Timber

/** Whether the system already exempts this app from battery optimization. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Asks the user to exempt the app from battery optimization with the one-tap system dialog
 * (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, backed by the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 * manifest permission). Falls back to the system battery-optimization list when the dialog is
 * unavailable on the device.
 *
 * @return false when no settings screen could be opened.
 */
@SuppressLint("BatteryLife")
fun openBatteryOptimizationSettings(context: Context): Boolean {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
    }
    runCatching { context.startActivity(requestIntent) }
        .onSuccess { return true }
        .onFailure { Timber.w(it, "Battery optimization request dialog unavailable") }
    return runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }.onFailure {
        Timber.w(it, "Battery optimization settings unavailable")
    }.isSuccess
}
