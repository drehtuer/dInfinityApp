package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.filamat.MaterialBuilder

// What each [DiceMaterial.Variant] declares to Filament beyond its source,
// read by [FilamentEngine] when it compiles one. Every function here is a step
// of that one `MaterialBuilder` chain, kept apart from the engine so the
// engine's file is about the engine.

/**
 * What the resin variant adds: refraction, and the numbers that describe
 * the resin ([Resin]).
 *
 * **Screen space, not the cubemap.** A cubemap refraction looks through
 * the die into the *room* — the lighting environment — and a die sits on
 * felt; looking down through one should show the felt under it, its
 * shadow and the dice beside it. Screen space does, at the price of one
 * copy of the opaque scene with its mip chain, made only on frames that
 * have a refracting die in them. What it cannot show is one translucent
 * die through another: the picture it looks into holds the opaque scene
 * only (`docs/physics-and-rendering.md`, "A die you can see into").
 *
 * **Solid, not thin.** A die is a lump, not a soap bubble: a ray goes in
 * at one face and out at another, displaced, and loses colour all the way.
 */
internal fun MaterialBuilder.resin(): MaterialBuilder =
  refractionMode(MaterialBuilder.RefractionMode.SCREEN_SPACE)
    .refractionType(MaterialBuilder.RefractionType.SOLID)
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "transmission")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "scatter")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "ior")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "thickness")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "tint")

/**
 * Filament's geometric specular anti-aliasing, at [DiceMaterial]'s settings,
 * for every variant whose [DiceMaterial.Variant.specularAntiAliasing] is on.
 */
internal fun MaterialBuilder.spreadThinGlints(): MaterialBuilder =
  specularAntiAliasing(true)
    .specularAntiAliasingVariance(DiceMaterial.SPECULAR_AA_VARIANCE)
    .specularAntiAliasingThreshold(DiceMaterial.SPECULAR_AA_THRESHOLD)

/**
 * What a die's surface reads beyond colour, roughness and metalness: its
 * artwork, its printed numbers, its lacquer and its glow ([Glow]).
 */
internal fun MaterialBuilder.diceParameters(): MaterialBuilder =
  uniformParameter(MaterialBuilder.UniformType.FLOAT, "textured")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "numbered")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "inkColor")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoat")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoatRoughness")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "glow")
    .sampler("atlas")
    .sampler("glyphs")

/**
 * And a table's: its three pictures, whether it has the two maps, and how far
 * its roughness map is moved ([TableTint.roughnessShift]).
 */
internal fun MaterialBuilder.tableParameters(): MaterialBuilder =
  uniformParameter(MaterialBuilder.UniformType.FLOAT, "hasNormal")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "hasRoughness")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughnessShift")
    .sampler("albedo")
    .sampler("normalMap")
    .sampler("roughnessMap")

/**
 * The whole of the tone pass ([DiceMaterial.Variant.TONE]): unlit, because it
 * writes the colour it works out and nothing else; drawn as a triangle given
 * in device coordinates that covers the screen, with no depth to test or
 * write and no face to cull; and reading one frame, at full precision,
 * because it holds light up to four times white ([ToneCurve.HEADROOM]), and
 * the glow mask its halo is blurred from, with how strong the halo is
 * ([GlowPass]).
 */
internal fun MaterialBuilder.toneMaterial(): MaterialBuilder =
  name("dinfinity-tone")
    .material(ToneCurve.SOURCE)
    .shading(MaterialBuilder.Shading.UNLIT)
    .vertexDomain(MaterialBuilder.VertexDomain.DEVICE)
    .depthWrite(false)
    .depthCulling(false)
    .culling(MaterialBuilder.CullingMode.NONE)
    .samplerParameter(
      MaterialBuilder.SamplerType.SAMPLER_2D,
      MaterialBuilder.SamplerFormat.FLOAT,
      MaterialBuilder.ParameterPrecision.HIGH,
      "frame",
    ).uniformParameter(MaterialBuilder.UniformType.FLOAT, "halo")
    .sampler("glow")
    .platform(MaterialBuilder.Platform.MOBILE)
    .optimization(MaterialBuilder.Optimization.PERFORMANCE)

/** A two-dimensional texture the material samples, named [name] in its source. */
internal fun MaterialBuilder.sampler(name: String): MaterialBuilder =
  samplerParameter(
    MaterialBuilder.SamplerType.SAMPLER_2D,
    MaterialBuilder.SamplerFormat.FLOAT,
    MaterialBuilder.ParameterPrecision.DEFAULT,
    name,
  )

/**
 * The glow mask ([DiceMaterial.Variant.GLOW]): unlit, because it writes a
 * surface's glow and no light at all, and reading the die's own number field
 * and artwork, so that what is printed gives off nothing.
 */
internal fun MaterialBuilder.glowMaskMaterial(): MaterialBuilder =
  name("dinfinity-glow")
    .material(Glow.MASK_SOURCE)
    .shading(MaterialBuilder.Shading.UNLIT)
    .flipUV(false)
    .require(MaterialBuilder.VertexAttribute.UV0)
    .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "glow")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "numbered")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "textured")
    .sampler("glyphs")
    .sampler("atlas")
    .platform(MaterialBuilder.Platform.MOBILE)
    .optimization(MaterialBuilder.Optimization.PERFORMANCE)
