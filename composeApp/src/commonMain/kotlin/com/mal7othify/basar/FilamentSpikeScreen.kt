package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mal7othify.basar.resources.Res
import com.spatialkit.render.AnimationState
import com.spatialkit.render.SceneRenderer
import kotlinx.coroutines.delay

/**
 * v2 R0 spike: proves the Filament renderer stack end-to-end — GLB bytes read
 * from common resources, loaded through the common [SceneRenderer] contract,
 * skinned animation cycling through the asset's clips. Non-AR by design; the
 * AR camera feed arrives with the R1/R2 tracking adapters.
 *
 * All renderer interaction here is COMMON code; the platform actuals only
 * create the renderer and host its native view.
 */
@Composable
fun FilamentSpikeScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf("Loading CesiumMan.glb…") }
    val renderer = rememberFilamentRenderer()

    DemoScaffold(screen = DemoScreen.FILAMENT_SPIKE, status = status, onBack = onBack) {
        FilamentSurface(renderer, Modifier.fillMaxSize())
    }

    LaunchedEffect(renderer) {
        // CesiumMan rather than the classic Fox: Fox.glb ships without NORMAL
        // attributes, which a PBR renderer (correctly) lights to black.
        val glb = Res.readBytes("files/CesiumMan.glb")
        val model = renderer.loadModel(glb)
        if (model.animations.isEmpty()) {
            status = "Loaded ${glb.size / 1024} KB — no animation clips"
            return@LaunchedEffect
        }
        // Cycle through the clips to show live animation control through the
        // common contract (CesiumMan has one walk clip; multi-clip assets cycle).
        var index = 0
        while (true) {
            val clip = model.animations[index]
            renderer.setAnimation(model, AnimationState(clip = clip.index))
            status = "Filament · clip \"${clip.name}\" (${index + 1}/${model.animations.size})"
            delay(4_000)
            index = (index + 1) % model.animations.size
        }
    }
}

/**
 * Creates the platform Filament renderer, disposed when the composable
 * leaves. The returned renderer is only touched from the main thread.
 */
@Composable
expect fun rememberFilamentRenderer(): SceneRenderer

/** Hosts the renderer's native surface (SurfaceView / CAMetalLayer view). */
@Composable
expect fun FilamentSurface(
    renderer: SceneRenderer,
    modifier: Modifier,
)
