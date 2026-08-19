package com.spatialkit.engine

/**
 * The scene payload types moved to `:spatial-render` in R3 (the v2 rewrite):
 * they describe what a renderer draws and are shared by the v1 engines and
 * the v2 [com.spatialkit.render.SceneRenderer]. These typealiases keep v1
 * source compatibility; new code should import `com.spatialkit.render.*`
 * directly.
 */
public typealias SceneData = com.spatialkit.render.SceneData

public typealias SceneNode = com.spatialkit.render.SceneNode

public typealias GeometryData = com.spatialkit.render.GeometryData

public typealias GeometryType = com.spatialkit.render.GeometryType

public typealias MaterialData = com.spatialkit.render.MaterialData

public typealias MaterialType = com.spatialkit.render.MaterialType

public typealias TextureData = com.spatialkit.render.TextureData

public typealias TextureSampler = com.spatialkit.render.TextureSampler

public typealias TextureWrap = com.spatialkit.render.TextureWrap

public typealias TextureFilter = com.spatialkit.render.TextureFilter
