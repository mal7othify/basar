package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.scene.SpatialSession2

/**
 * iOS joins the v2 runtime with the R2 ARKit tracking adapter; until then
 * [SpatialView2] renders nothing here (the v1 [SpatialView] remains the
 * working iOS path).
 */
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
