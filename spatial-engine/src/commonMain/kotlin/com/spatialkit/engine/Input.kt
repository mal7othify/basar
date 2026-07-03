package com.spatialkit.engine

/**
 * Unified input abstraction across all platforms.
 *
 * Different platforms provide different input methods:
 * - AR phones: Screen (touch)
 * - VR headsets: Controllers, Hand tracking, Gaze
 * - AR glasses: Gaze, Hand tracking, Controllers
 */
public sealed interface InputSource {
    public val id: String

    /**
     * VR/AR controller input (e.g., Quest controllers, Vive wands).
     */
    public data class Controller(
        override val id: String,
        val hand: com.spatialkit.engine.Hand,
        val pose: Pose,
        val buttons: Map<Button, ButtonState>,
        val axes: Map<Axis, Float>,
    ) : InputSource

    /**
     * Hand tracking input (finger/hand pose detection).
     */
    public data class Hand(
        override val id: String,
        val handedness: Handedness,
        val joints: Map<HandJoint, Pose>,
        val gesture: HandGesture?,
    ) : InputSource

    /**
     * Touch screen input (for AR on phones/tablets).
     */
    public data class Screen(
        override val id: String,
        val touches: List<Touch>,
    ) : InputSource

    /**
     * Gaze/head tracking input (for devices without controllers).
     */
    public data class Gaze(
        override val id: String,
        val origin: Vec3,
        val direction: Vec3,
    ) : InputSource
}

/**
 * Which hand a controller or hand tracking is for.
 */
public enum class Handedness { LEFT, RIGHT }

/**
 * Which hand a controller is held in.
 */
public enum class Hand { LEFT, RIGHT }

/**
 * Controller buttons that may be available.
 */
public enum class Button {
    TRIGGER,
    GRIP,
    PRIMARY,
    SECONDARY,
    THUMBSTICK,
}

/**
 * State of a controller button.
 */
public enum class ButtonState {
    RELEASED,
    PRESSED,
    TOUCHED,
}

/**
 * Controller axes (analog inputs).
 */
public enum class Axis {
    THUMBSTICK_X,
    THUMBSTICK_Y,
    TRIGGER,
}

/**
 * Hand joint positions for hand tracking.
 */
public enum class HandJoint {
    WRIST,
    THUMB_TIP,
    THUMB_IP,
    THUMB_MCP,
    THUMB_CMC,
    INDEX_TIP,
    INDEX_DIP,
    INDEX_PIP,
    INDEX_MCP,
    MIDDLE_TIP,
    MIDDLE_DIP,
    MIDDLE_PIP,
    MIDDLE_MCP,
    RING_TIP,
    RING_DIP,
    RING_PIP,
    RING_MCP,
    PINKY_TIP,
    PINKY_DIP,
    PINKY_PIP,
    PINKY_MCP,
}

/**
 * Recognized hand gestures (platform-specific).
 */
public enum class HandGesture {
    PINCH,
    GRAB,
    POINT,
    OPEN,
}

/**
 * Touch input on screen (for AR).
 */
public data class Touch(
    val id: Int,
    val screenX: Float,
    val screenY: Float,
    val phase: TouchPhase,
)

/**
 * Touch lifecycle phase.
 */
public enum class TouchPhase {
    BEGAN,
    MOVED,
    ENDED,
    CANCELLED,
}
