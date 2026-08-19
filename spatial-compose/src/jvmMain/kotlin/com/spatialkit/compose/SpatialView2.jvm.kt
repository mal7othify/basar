package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.scene.SpatialSession2

/** Desktop has no AR runtime; [SpatialView2] renders nothing on the JVM. */
@Composable
internal actual fun rememberSpatialStack2(): SpatialStack2 = remember { UnsupportedStack2 }

private object UnsupportedStack2 : SpatialStack2 {
    override val session: SpatialSession2? = null

    override fun dispose() {}
}

@Composable
internal actual fun SpatialSurface2(
    session: SpatialSession2,
    modifier: Modifier,
) {
}
