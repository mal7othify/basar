package com.spatialkit.engine.arcore

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.spatialkit.engine.SpatialEngine

/**
 * Check if ARCore is installed and ready to use.
 */
fun isARCoreAvailable(context: Context): Boolean {
    val availability = ArCoreApk.getInstance().checkAvailability(context)
    return availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
}

/**
 * Check if ARCore is supported on this device (may require installation).
 */
fun isARCoreSupported(context: Context): Boolean {
    val availability = ArCoreApk.getInstance().checkAvailability(context)
    return when (availability) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED,
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
        -> true

        else -> false
    }
}

/**
 * Request ARCore installation if needed.
 *
 * @param activity Activity context required for installation dialog
 * @param userRequestedInstall true if user explicitly requested AR feature
 * @return Installation status
 */
fun requestARCoreInstallation(
    activity: android.app.Activity,
    userRequestedInstall: Boolean = true,
): ArCoreApk.InstallStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)

/**
 * Create an ARCore-based SpatialEngine instance.
 *
 * @param context Android context (preferably Activity context for permissions)
 * @return SpatialEngine implementation using ARCore
 */
fun createARCoreEngine(context: Context): SpatialEngine = ARCoreEngine(context)
