package com.spatialkit.render.filament

import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.utils.IBLPrefilterContext
import com.spatialkit.tracking.EnvironmentCubemap
import com.spatialkit.tracking.LightEstimate
import java.nio.ByteBuffer

/**
 * Feeds ARCore's HDR light estimate into Filament (R4 — Realism I):
 *
 * - **Main light**: direction / color / illuminance applied to the sun
 *   entity every frame.
 * - **Ambient**: the 9 RGB spherical-harmonics coefficients become the
 *   scene's [IndirectLight] irradiance (rebuilt only when they change —
 *   ARCore quantizes them, so identical frames are cheap).
 * - **Reflections**: the ~1 Hz HDR environment cubemap is uploaded,
 *   mipmapped, and run through filament-utils' specular prefilter into the
 *   IndirectLight's reflections cubemap.
 *
 * Frame-thread only, owned by [FilamentRenderer].
 */
internal class IblManager(
    private val engine: Engine,
    private val scene: Scene,
    private val sunEntity: Int,
) {
    private val prefilterContext by lazy { IBLPrefilterContext(engine) }
    private val specularFilter by lazy { IBLPrefilterContext.SpecularFilter(prefilterContext) }
    private var specularFilterUsed = false

    private var indirectLight: IndirectLight? = null
    private var reflections: Texture? = null
    private var lastSh: FloatArray? = null
    private val scaledSh = FloatArray(27)

    fun update(estimate: LightEstimate) {
        updateSun(estimate)

        var rebuild = false
        estimate.sphericalHarmonics?.let { sh ->
            if (sh.size == 27 && !(lastSh?.contentEquals(sh) ?: false)) {
                lastSh = sh.copyOf()
                for (i in 0 until 27) {
                    scaledSh[i] = sh[i] * IRRADIANCE_FACTORS[i / 3]
                }
                rebuild = true
            }
        }
        estimate.environmentCubemap?.let { cubemap ->
            updateReflections(cubemap)
            rebuild = true
        }

        if (rebuild && lastSh != null) {
            val builder =
                IndirectLight
                    .Builder()
                    .irradiance(3, scaledSh)
            reflections?.let { builder.reflections(it) }
            val newLight = builder.build(engine)
            scene.indirectLight = newLight
            indirectLight?.let(engine::destroyIndirectLight)
            indirectLight = newLight
        }
    }

    fun dispose() {
        scene.indirectLight = null
        indirectLight?.let(engine::destroyIndirectLight)
        indirectLight = null
        reflections?.let(engine::destroyTexture)
        reflections = null
        if (specularFilterUsed) {
            specularFilter.destroy()
            prefilterContext.destroy()
        }
    }

    private fun updateSun(estimate: LightEstimate) {
        if (estimate.mainLightIntensityLux <= 0f) return
        val lightManager = engine.lightManager
        val instance = lightManager.getInstance(sunEntity)
        lightManager.setIntensity(instance, estimate.mainLightIntensityLux)
        lightManager.setColor(
            instance,
            estimate.mainLightColor.x,
            estimate.mainLightColor.y,
            estimate.mainLightColor.z,
        )
        val d = estimate.mainLightDirection
        lightManager.setDirection(instance, d.x, d.y, d.z)
    }

    private fun updateReflections(cubemap: EnvironmentCubemap) {
        val size = cubemap.size
        if (size <= 0 || cubemap.faces.size != 6) return

        val source =
            Texture
                .Builder()
                .width(size)
                .height(size)
                .levels(0xff)
                .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
                .format(Texture.InternalFormat.R11F_G11F_B10F)
                .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
                .build(engine)

        cubemap.faces.forEachIndexed { face, buffer ->
            source.setImage(
                engine,
                0,
                0,
                0,
                face,
                size,
                size,
                1,
                Texture.PixelBufferDescriptor(
                    buffer.duplicate() as ByteBuffer,
                    Texture.Format.RGBA,
                    Texture.Type.HALF,
                ),
            )
        }
        source.generateMipmaps(engine)

        specularFilterUsed = true
        val filtered = specularFilter.run(source)
        engine.destroyTexture(source)
        reflections?.let(engine::destroyTexture)
        reflections = filtered
    }

    private companion object {
        /**
         * Filament irradiance normalization for ARCore's environmental-HDR
         * SH output order (one factor per SH band coefficient, applied to
         * each RGB channel) — the SceneView-validated table.
         */
        val IRRADIANCE_FACTORS =
            floatArrayOf(
                0.282095f,
                -0.325735f,
                0.325735f,
                -0.325735f,
                0.273137f,
                -0.273137f,
                0.078848f,
                -0.273137f,
                0.136569f,
            )
    }
}
