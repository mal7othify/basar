# Animation System Guide

The `SpatialAnimator` provides a simple but functional animation API for animating spatial node transforms.

For Compose-driven animation of spatial values (springs, tweens), see
`animatedSpatialVec3` / `animatedSpatialRotation` in `spatial-compose` — they
integrate with Compose's animation system, while `SpatialAnimator` is
Compose-free and driven from the frame callback.

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
    to = Quaternion.fromEuler(0f, 180.degrees, 0f),
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
    to = Quaternion.fromEuler(0f, 360.degrees, 0f),
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

## Complete Example (with Compose)

```kotlin
import androidx.compose.runtime.*
import com.spatialkit.compose.SpatialView
import com.spatialkit.scene.*

@Composable
fun BouncingCube() {
    val animator = remember { SpatialAnimator() }
    var cube: CubeNode? = null
    var bounced by remember { mutableStateOf(false) }

    Button(onClick = { bounced = true }) { Text("Bounce") }

    SpatialView(
        onFrame = { frameData ->
            // Node state is applied on the render thread
            if (bounced) {
                bounced = false
                cube?.let {
                    animator.animatePosition(
                        node = it,
                        from = it.position,
                        to = it.position + Vec3(0f, 0.5f, 0f),
                        duration = 0.5f,
                        easing = Easing.EaseOutBounce
                    )
                }
            }
            animator.update(frameData.deltaTime)
        },
    ) {
        cube = cube(size = 10.cm, color = Color.Red) {
            position = vec3(0f, 0f, -1f)
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

Two options in `spatial-compose`:

1. **Compose-native animated values** — `animatedSpatialVec3` /
   `animatedSpatialRotation` animate `Vec3`/`Quaternion` with Compose animation
   specs (springs, tweens); apply the value in `onFrame`. See the README example.
2. **`SpatialAnimator` in `onFrame`** — as in the complete example above: create
   it with `remember`, drive `animator.update(frameData.deltaTime)` from
   `SpatialView`'s `onFrame`, and call `animator.cancelAll()` from a
   `DisposableEffect`'s `onDispose` when the composable leaves.