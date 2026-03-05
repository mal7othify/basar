# Animation System Guide

**Fix for P2 Bug #8: Animation System Stubbed**

The `SpatialAnimator` provides a simple but functional animation API for animating spatial node transforms.

## Features

- ✅ Animate position, rotation, and scale
- ✅ Multiple easing functions (Linear, EaseIn, EaseOut, EaseInOut, Cubic, Bounce, Elastic)
- ✅ Completion callbacks
- ✅ Animation cancellation
- ✅ Multiple simultaneous animations per node

## Basic Usage

```kotlin
import com.spatialkit.scene.*

// Create an animator
val animator = SpatialAnimator()

// Create a node
val cube = CubeNode(size = 0.1f)

// Animate position
animator.animatePosition(
    node = cube,
    from = Vec3(0f, 0f, -1f),
    to = Vec3(0f, 1f, -1f),
    duration = 1.0f,
    easing = Easing.EaseInOut,
    onComplete = {
        println("Animation complete!")
    }
)

// Update in your frame callback
session.start(
    onFrame = { frameData ->
        animator.update(frameData.deltaTime)
    }
)
```

## Animation Types

### Position Animation

```kotlin
animator.animatePosition(
    node = cube,
    from = Vec3(0f, 0f, -1f),
    to = Vec3(0f, 1f, -1f),
    duration = 2.0f,
    easing = Easing.EaseOutBounce
)
```

### Rotation Animation

```kotlin
animator.animateRotation(
    node = cube,
    from = Quaternion.IDENTITY,
    to = Quaternion.fromEuler(0f, 180f, 0f),
    duration = 1.5f,
    easing = Easing.EaseInOut
)
```

### Scale Animation

```kotlin
animator.animateScale(
    node = cube,
    from = Vec3.ONE,
    to = Vec3(2f, 2f, 2f),
    duration = 1.0f,
    easing = Easing.EaseOutElastic
)
```

## Easing Functions

- **Linear** - No easing, constant speed
- **EaseIn** - Slow start, accelerates
- **EaseOut** - Fast start, decelerates
- **EaseInOut** - Slow start and end, fast middle
- **EaseInCubic** - Cubic easing in
- **EaseOutCubic** - Cubic easing out
- **EaseOutBounce** - Bounce effect at the end
- **EaseOutElastic** - Elastic spring effect

## Sequential Animations

Chain animations using completion callbacks:

```kotlin
// Move up
animator.animatePosition(
    node = cube,
    from = Vec3(0f, 0f, -1f),
    to = Vec3(0f, 1f, -1f),
    duration = 1.0f,
    easing = Easing.EaseInOut,
    onComplete = {
        // Then move right
        animator.animatePosition(
            node = cube,
            from = cube.position,
            to = Vec3(1f, 1f, -1f),
            duration = 1.0f,
            easing = Easing.EaseInOut
        )
    }
)
```

## Parallel Animations

Animate multiple properties simultaneously:

```kotlin
// Animate position and rotation at the same time
animator.animatePosition(
    node = cube,
    from = Vec3(0f, 0f, -1f),
    to = Vec3(0f, 1f, -1f),
    duration = 2.0f
)

animator.animateRotation(
    node = cube,
    from = Quaternion.IDENTITY,
    to = Quaternion.fromEuler(0f, 360f, 0f),
    duration = 2.0f
)
```

## Animation Management

### Cancel Specific Animation

```kotlin
val animation = animator.animatePosition(...)
animator.cancel(animation)
```

### Cancel All Animations

```kotlin
animator.cancelAll()
```

### Check Animation Count

```kotlin
val count = animator.animationCount
println("Active animations: $count")
```

## Complete Example

```kotlin
import com.spatialkit.compose.*
import com.spatialkit.scene.*

class MyARApp {
    private val animator = SpatialAnimator()

    @Composable
    fun ARScene() {
        val session = rememberSpatialSession()

        SpatialScene(session) {
            val cube = remember {
                CubeNode(size = 0.1f, color = Color.RED).apply {
                    position = Vec3(0f, 0f, -1f)
                }
            }

            SpatialObject(cube)

            // Animate on tap
            SpatialGestureDetector(
                onTap = { hitResult ->
                    // Bounce animation
                    animator.animatePosition(
                        node = cube,
                        from = cube.position,
                        to = cube.position + Vec3(0f, 0.5f, 0f),
                        duration = 0.5f,
                        easing = Easing.EaseOutBounce
                    )
                }
            )

            // Update animator each frame
            SpatialFrameHandler { frameData ->
                animator.update(frameData.deltaTime)
            }
        }
    }
}
```

## Performance Notes

- Animations are lightweight and run efficiently
- Multiple animations can run simultaneously without performance issues
- Animations automatically clean up when complete
- Use `cancelAll()` when disposing the animator to prevent leaks

## Integration with Compose

The animation system integrates seamlessly with `spatial-compose`:

```kotlin
@Composable
fun AnimatedCube() {
    val animator = remember { SpatialAnimator() }
    val cube = remember { CubeNode() }

    LaunchedEffect(Unit) {
        // Start animation when composable enters
        animator.animatePosition(
            node = cube,
            from = Vec3.ZERO,
            to = Vec3(0f, 1f, 0f),
            duration = 1.0f
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            animator.cancelAll()
        }
    }

    SpatialObject(cube)
}
```