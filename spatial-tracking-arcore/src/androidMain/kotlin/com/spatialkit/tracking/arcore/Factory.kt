package com.spatialkit.tracking.arcore

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.spatialkit.tracking.TrackingProvider

/** Check if ARCore is installed and ready to use. */
public fun isARCoreAvailable(context: Context): Boolean =
    ArCoreApk.getInstance().checkAvailability(context) ==
        ArCoreApk.Availability.SUPPORTED_INSTALLED

/** Check if ARCore is supported on this device (may require installation). */
public fun isARCoreSupported(context: Context): Boolean =
    when (ArCoreApk.getInstance().checkAvailability(context)) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED,
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
        -> true

        else -> false
    }

/**
 * Request ARCore installation if needed.
 *
 * @param activity Activity context required for the installation dialog
 * @param userRequestedInstall true if the user explicitly requested AR
 * @return Installation status
 */
public fun requestARCoreInstallation(
    activity: android.app.Activity,
    userRequestedInstall: Boolean = true,
): ArCoreApk.InstallStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)

/**
 * Creates the ARCore [TrackingProvider].
 *
 * @param context Android context (preferably an Activity for permissions)
 */
public fun createARCoreTrackingProvider(context: Context): TrackingProvider = ARCoreTrackingProvider(context)
