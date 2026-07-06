package com.spatialkit.engine

/**
 * Errors that can occur during engine operations.
 *
 * This sealed interface defines all possible error states that the engine
 * can report. Platform implementations should map native errors to these types.
 */
public sealed interface EngineError {
    /**
     * The requested feature or session mode is not supported on this platform.
     */
    public data object NotSupported : EngineError

    /**
     * Required permissions (camera, location, etc.) were denied by the user.
     */
    public data object PermissionDenied : EngineError

    /**
     * Engine initialization failed.
     *
     * @property reason Human-readable description of the failure
     */
    public data class InitializationFailed(
        val reason: String,
    ) : EngineError

    /**
     * Session start or management failed.
     *
     * @property reason Human-readable description of the failure
     */
    public data class SessionFailed(
        val reason: String,
    ) : EngineError

    /**
     * Tracking was lost or degraded.
     *
     * @property reason Human-readable description of the failure
     */
    public data class TrackingFailed(
        val reason: String,
    ) : EngineError

    /**
     * An error occurred while the session was running — typically an exception
     * thrown by an application frame callback.
     *
     * @property reason Human-readable description of the failure
     * @property cause The original exception, if one was caught
     */
    public data class RuntimeError(
        val reason: String,
        val cause: Throwable? = null,
    ) : EngineError

    /**
     * An asset (e.g. a glTF model) could not be loaded or parsed.
     *
     * @property reason Human-readable description of what is wrong with the asset
     * @property cause The original exception, if one was caught
     */
    public data class AssetError(
        val reason: String,
        val cause: Throwable? = null,
    ) : EngineError
}
