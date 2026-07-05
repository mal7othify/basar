# SpatialEngine Factory Usage Guide

## Overview

The `SpatialEngineFactory` provides a cross-platform way to create and detect AR/VR engines. The `SpatialRuntime` wrapper simplifies lifecycle management.

---

## Quick Start

### Web (Kotlin/JS)

The common `SpatialEngineFactory.create()` returns the fallback engine — real
engines live in the platform modules. On the web, create the WebXR engine from
`spatial-engine-webxr` and hand it to the runtime:

```kotlin
import com.spatialkit.engine.*
import com.spatialkit.engine.webxr.createWebXREngine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    val runtime = SpatialRuntime()
    runtime.setEngine(createWebXREngine())

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
                        position = plane.centerPose.position,
                        rotation = plane.centerPose.rotation
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

### iOS (ARKit)

```kotlin
import com.spatialkit.engine.*
import com.spatialkit.engine.arkit.createARKitEngine
import platform.Foundation.NSLog

suspend fun startAR() {
    val runtime = SpatialRuntime()
    runtime.setEngine(createARKitEngine())

    if (!runtime.isSupported) {
        NSLog("ARKit not supported")
        return
    }

    runtime.start(
        config = EngineConfig(preferredMode = SessionMode.AR),
        onFrame = { frameData -> /* ... */ },
        onError = { error -> NSLog("AR error: $error") }
    )
}
```

---

## Advanced Usage

### Check Session Mode Support

```kotlin
import com.spatialkit.engine.SpatialEngineFactory

val modes = SpatialEngineFactory.getSupportedModes()
val supportsAR = SessionMode.AR in modes
val supportsVR = SessionMode.VR in modes
```

### Manual Engine Creation (Low-Level)

Each platform engine comes from its own module — the common
`SpatialEngineFactory.create()` returns the `FallbackEngine`:

```kotlin
// Web (spatial-engine-webxr)
val engine: SpatialEngine = createWebXREngine()

// Android (spatial-engine-arcore; needs a Context)
val engine: SpatialEngine = createARCoreEngine(context)

// iOS (spatial-engine-arkit)
val engine: SpatialEngine = createARKitEngine()

// Then manually manage lifecycle (initialize/startSession are suspend):
scope.launch {
    engine.initialize(config)
    engine.onFrame { frameData -> /* ... */ }
    engine.startSession(SessionMode.AR)
}
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
        when (runtime.requestInstallation(this, !requestedInstall)) {
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
            EngineError.PermissionDenied -> {
                showMessage("Camera permission required")
            }
            is EngineError.InitializationFailed -> {
                showMessage("Failed to start AR: ${error.reason}")
            }
            is EngineError.SessionFailed -> {
                showMessage("AR session error: ${error.reason}")
            }
            is EngineError.TrackingFailed -> {
                showMessage("Tracking problem: ${error.reason}")
            }
            is EngineError.RuntimeError -> {
                showMessage("Runtime error: ${error.reason}")
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
    fun create(): SpatialEngine   // returns FallbackEngine on every platform
    fun getEngineName(): String
}
```

Platform engines come from their modules:

```kotlin
// spatial-engine-arcore (Android)
fun isARCoreAvailable(context: Context): Boolean
fun isARCoreSupported(context: Context): Boolean
fun requestARCoreInstallation(activity: Activity, userRequestedInstall: Boolean): InstallStatus
fun createARCoreEngine(context: Context): SpatialEngine

// spatial-engine-webxr (JS)
fun isWebXRAvailable(): Boolean
fun createWebXREngine(): SpatialEngine

// spatial-engine-arkit (iOS)
fun isARKitAvailable(): Boolean
fun createARKitEngine(): SpatialEngine
```

### SpatialRuntime

```kotlin
class SpatialRuntime {
    val isSupported: Boolean
    val supportedModes: List<SessionMode>
    val state: EngineState?
    val stateFlow: StateFlow<EngineState>?
    val capabilities: EngineCapabilities?
    val engineName: String
    val isRunning: Boolean

    fun getEngine(): SpatialEngine?
    fun setEngine(customEngine: SpatialEngine)

    suspend fun start(
        config: EngineConfig,
        onFrame: (FrameData) -> Unit,
        onError: (EngineError) -> Unit
    )

    fun submitScene(scene: SceneData)
    fun performHitTest(screenX: Float, screenY: Float): HitTestResult?  // normalized 0–1
    fun performRaycast(origin: Vec3, direction: Vec3): RaycastResult?
    fun stop()
    fun dispose()
}
```

### SpatialRuntimeAndroid (spatial-engine-arcore)

```kotlin
class SpatialRuntimeAndroid(context: Context) {
    // Same API as SpatialRuntime, plus:

    fun requestInstallation(
        activity: Activity,
        userRequested: Boolean = true
    ): InstallStatus
}
```

---

## FAQ

**Q: Why does Android need different wiring?**
A: Android requires a `Context` to check ARCore availability and create the engine. Use `createARCoreEngine(context)` from `spatial-engine-arcore`, or `SpatialRuntimeAndroid(context)` which does it for you.

**Q: Can I use multiple engines at once?**
A: No, most platforms only support one active AR/VR session at a time.

**Q: What happens on unsupported platforms?**
A: The `FallbackEngine` is used, which reports `NotSupported` and returns empty data. Your app won't crash.

**Q: How do I know which engine I'm using?**
A: Read `runtime.engineName` — the fallback identifies itself and points at the platform module to depend on.

**Q: Can I switch between AR and VR at runtime?**
A: Call `stop()`, then `start()` with a different mode. Not all platforms support all modes.
