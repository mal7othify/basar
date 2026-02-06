package com.spatialkit.engine

/**
 * Errors that can occur during engine operations.
 *
 * This sealed interface defines all possible error states that the engine
 * can report. Platform implementations should map native errors to these types.
 */
sealed interface EngineError {
    /**
     * The requested feature or session mode is not supported on this platform.
     */
    data object NotSupported : EngineError

    /**
     * Required permissions (camera, location, etc.) were denied by the user.
     */
    data object PermissionDenied : EngineError

    /**
     * Engine initialization failed.
     *
     * @property reason Human-readable description of the failure
     */
    data class InitializationFailed(
        val reason: String,
    ) : EngineError

    /**
     * Session start or management failed.
     *
     * @property reason Human-readable description of the failure
     */
    data class SessionFailed(
        val reason: String,
    ) : EngineError

    /**
     * Tracking was lost or degraded.
     *
     * @property reason Human-readable description of the failure
     */
    data class TrackingFailed(
        val reason: String,
    ) : EngineError
}
