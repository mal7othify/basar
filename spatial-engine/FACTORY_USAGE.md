# SpatialEngine Factory Usage Guide

## Overview

The `SpatialEngineFactory` provides a cross-platform way to create and detect AR/VR engines. The `SpatialRuntime` wrapper simplifies lifecycle management.

---

## Quick Start

### Web (Kotlin/JS)

```kotlin
import com.spatialkit.engine.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    val runtime = SpatialRuntime()

    if (!runtime.isSupported) {
        console.log("WebXR not supported on this browser")
        return
    }

    GlobalScope.launch {
        runtime.start(
            config = EngineConfig(
                preferredMode = SessionMode.AR,
                enablePlaneDetection = true
            ),
            onFrame = { frameData ->
                // Update your scene based on frame data
                val scene = buildScene(frameData)
                runtime.submitScene(scene)
            },
            onError = { error ->
                console.error("AR Error: $error")
            }
        )
    }
}

fun buildScene(frameData: FrameData): SceneData {
    return SceneData(
        nodes = listOf(
            SceneNode.Mesh(
                id = "cube",
                transform = Transform(
                    position = Vec3(0f, 0f, -1f)
                ),
                geometry = GeometryData(type = GeometryType.CUBE),
                material = MaterialData(
                    type = MaterialType.PBR,
                    baseColor = Vec4(1f, 0f, 0f, 1f)
                )
            )
        ),
        anchors = emptyList()
    )
}
```

---

### Android (ARCore)

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.spatialkit.engine.*
import com.spatialkit.engine.arcore.SpatialRuntimeAndroid
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var runtime: SpatialRuntimeAndroid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        runtime = SpatialRuntimeAndroid(this)

        // Check if ARCore is supported
        if (!runtime.isSupported) {
            showError("ARCore not supported on this device")
            return
        }

        // Request camera permission first (not shown)
        // ...

        lifecycleScope.launch {
            runtime.start(
                config = EngineConfig(
                    preferredMode = SessionMode.AR,
                    enablePlaneDetection = true,
                    enableLightEstimation = true
                ),
                onFrame = { frameData ->
                    // Update scene
                    val scene = buildScene(frameData)
                    runtime.submitScene(scene)
                },
                onError = { error ->
                    showError("AR Error: $error")
                }
            )
        }
    }

    override fun onDestroy() {
        runtime.dispose()
        super.onDestroy()
    }

    private fun buildScene(frameData: FrameData): SceneData {
        val nodes = mutableListOf<SceneNode>()

        // Add a cube 1 meter in front of camera
        nodes.add(
            SceneNode.Mesh(
                id = "test-cube",
                transform = Transform(
                    position = Vec3(0f, 0f, -1f),
                    scale = Vec3(0.2f, 0.2f, 0.2f)
                ),
                geometry = GeometryData(type = GeometryType.CUBE),
                material = MaterialData(
                    type = MaterialType.PBR,
                    baseColor = Vec4(0f, 1f, 0f, 1f),
                    metallic = 0.5f,
                    roughness = 0.3f
                )
            )
        )

        // Place objects on detected planes
        frameData.detectedPlanes.forEach { plane ->
            nodes.add(
                SceneNode.Mesh(
                    id = "plane-${plane.id}",
                    transform = Transform(
                        position = plane.pose.position,
                        rotation = plane.pose.rotation
                    ),
                    geometry = GeometryData(type = GeometryType.PLANE),
                    material = MaterialData(
                        type = MaterialType.UNLIT,
                        baseColor = Vec4(1f, 1f, 1f, 0.5f)
                    )
                )
            )
        }

        return SceneData(nodes = nodes, anchors = emptyList())
    }

    private fun showError(message: String) {
        // Show error to user
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
```

---

### iOS (ARKit - Future)

```kotlin
import com.spatialkit.engine.*
import platform.Foundation.NSLog

fun startAR() {
    val runtime = SpatialRuntime()

    if (!runtime.isSupported) {
        NSLog("ARKit not supported")
        return
    }

    // TODO: Implementation when ARKit support is added
}
```

---

## Advanced Usage

### Check Specific Session Mode Support

```kotlin
// Web only - async check
import com.spatialkit.engine.SpatialEngineFactory
import kotlinx.coroutines.launch

GlobalScope.launch {
    val supportsAR = SpatialEngineFactory.isSessionModeSupported(SessionMode.AR)
    val supportsVR = SpatialEngineFactory.isSessionModeSupported(SessionMode.VR)

    console.log("AR support: $supportsAR")
    console.log("VR support: $supportsVR")
}
```

### Manual Engine Creation (Low-Level)

```kotlin
// Create engine manually without SpatialRuntime wrapper

// Web
val engine: SpatialEngine = SpatialEngineFactory.create()

// Android
val engine: SpatialEngine = SpatialEngineFactory.createWithContext(context)

// Then manually manage lifecycle:
engine.initialize(config)
engine.onFrame { frameData -> /* ... */ }
engine.startSession(SessionMode.AR)
// ...
engine.stopSession()
engine.dispose()
```

### Handle ARCore Installation

```kotlin
import com.spatialkit.engine.arcore.SpatialRuntimeAndroid
import com.google.ar.core.ArCoreApk

class MainActivity : AppCompatActivity() {
    private lateinit var runtime: SpatialRuntimeAndroid
    private var requestedInstall = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtime = SpatialRuntimeAndroid(this)

        // Check and request installation
        when (runtime.requestARCoreInstallation(this, !requestedInstall)) {
            ArCoreApk.InstallStatus.INSTALLED -> {
                // ARCore is installed, start AR
                startAR()
            }
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                // Installation in progress, will restart activity
                requestedInstall = true
            }
        }
    }

    private fun startAR() {
        lifecycleScope.launch {
            runtime.start(
                config = EngineConfig(preferredMode = SessionMode.AR),
                onFrame = { frameData -> /* ... */ },
                onError = { error -> /* ... */ }
            )
        }
    }
}
```

### Graceful Degradation

```kotlin
// Your app can still run without AR/VR

val runtime = SpatialRuntime()

if (runtime.isSupported) {
    // Start AR experience
    startARExperience()
} else {
    // Fall back to 2D experience
    start2DExperience()
}
```

---

## Platform Detection at Compile Time

```kotlin
expect fun getPlatformName(): String

// jsMain
actual fun getPlatformName() = "Web"

// androidMain
actual fun getPlatformName() = "Android"

// iosMain
actual fun getPlatformName() = "iOS"

// Usage:
fun main() {
    println("Running on: ${getPlatformName()}")
    println("Engine: ${SpatialEngineFactory.getEngineName()}")
}
```

---

## Error Handling

```kotlin
runtime.start(
    config = EngineConfig(preferredMode = SessionMode.AR),
    onFrame = { frameData ->
        runtime.submitScene(myScene)
    },
    onError = { error ->
        when (error) {
            EngineError.NotSupported -> {
                showMessage("AR not supported on this device")
            }
            is EngineError.PermissionDenied -> {
                showMessage("Camera permission required: ${error.message}")
            }
            is EngineError.InitializationFailed -> {
                showMessage("Failed to start AR: ${error.message}")
            }
            is EngineError.SessionFailed -> {
                showMessage("AR session error: ${error.message}")
            }
        }
    }
)
```

---

## Best Practices

### 1. Always Check Support Before Starting

```kotlin
✅ GOOD:
if (runtime.isSupported) {
    runtime.start(...)
}

❌ BAD:
runtime.start(...)  // May fail silently or crash
```

### 2. Clean Up Resources

```kotlin
✅ GOOD:
override fun onDestroy() {
    runtime.dispose()
    super.onDestroy()
}

❌ BAD:
// Forgetting to dispose causes memory leaks
```

### 3. Handle Async Initialization

```kotlin
✅ GOOD:
lifecycleScope.launch {
    runtime.start(...)  // Suspend function
}

❌ BAD:
runtime.start(...)  // Compile error - needs coroutine
```

### 4. Android: Use Activity Context

```kotlin
✅ GOOD:
val runtime = SpatialRuntimeAndroid(this)  // Activity context

⚠️ OK:
val runtime = SpatialRuntimeAndroid(applicationContext)  // Works but less ideal

❌ BAD:
val runtime = SpatialRuntimeAndroid(someRandomContext)  // May break permissions
```

---

## Testing Without Hardware

```kotlin
// The FallbackEngine allows your code to run without AR hardware

val runtime = SpatialRuntime()  // Will use FallbackEngine on desktop

// Your code still works, just won't render anything
runtime.start(
    config = EngineConfig(preferredMode = SessionMode.AR),
    onFrame = { frameData ->
        // frameData will be empty/default
        runtime.submitScene(myScene)
    },
    onError = { error ->
        // Will receive EngineError.NotSupported
        println("Running in fallback mode: $error")
    }
)
```

---

## API Reference

### SpatialEngineFactory

```kotlin
object SpatialEngineFactory {
    fun isSupported(): Boolean
    fun getSupportedModes(): List<SessionMode>
    fun create(): SpatialEngine
    fun getEngineName(): String

    // Platform-specific:
    // Android:
    fun isSupported(context: Context): Boolean
    fun createWithContext(context: Context): SpatialEngine
    fun requestInstallation(activity: Activity, userRequested: Boolean): InstallStatus

    // Web:
    suspend fun isSessionModeSupported(mode: SessionMode): Boolean
}
```

### SpatialRuntime

```kotlin
class SpatialRuntime {
    val isSupported: Boolean
    val supportedModes: List<SessionMode>
    val state: EngineState?
    val capabilities: EngineCapabilities?
    val engineName: String
    val isRunning: Boolean

    suspend fun start(
        config: EngineConfig,
        onFrame: (FrameData) -> Unit,
        onError: (EngineError) -> Unit
    )

    fun submitScene(scene: SceneData)
    fun performHitTest(screenX: Float, screenY: Float): HitTestResult?
    fun performRaycast(origin: Vec3, direction: Vec3): RaycastResult?
    fun stop()
    fun dispose()
}
```

### SpatialRuntimeAndroid (Android-specific)

```kotlin
class SpatialRuntimeAndroid(context: Context) {
    // Same API as SpatialRuntime, plus:

    fun requestARCoreInstallation(
        activity: Activity,
        userRequested: Boolean = true
    ): InstallStatus
}
```

---

## FAQ

**Q: Why does Android need a different factory method?**
A: Android requires Context to check ARCore availability and create the engine. Use `createWithContext(context)` or `SpatialRuntimeAndroid`.

**Q: Can I use multiple engines at once?**
A: No, most platforms only support one active AR/VR session at a time.

**Q: What happens on unsupported platforms?**
A: The `FallbackEngine` is returned, which logs warnings and returns empty data. Your app won't crash.

**Q: How do I know which engine I'm using?**
A: Call `SpatialEngineFactory.getEngineName()` - returns "WebXR", "ARCore", "ARKit", or "Fallback".

**Q: Can I switch between AR and VR at runtime?**
A: Call `stop()`, then `start()` with a different mode. Not all platforms support all modes.

---

**Last Updated**: January 15, 2026
**Engine Version**: spatial-engine v1.0
