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
 * What the glass variant adds: the picture of the dice seen from under the
 * floor, and how much of it the table shows ([DiceMaterial.GLASS_SOURCE]).
 *
 * Its own picture rather than Filament's screen-space reflections, whose
 * material switch (`reflectionMode`) is left at its default: those reflect
 * the walls as well as the dice (`Reflection`).
 */
internal fun MaterialBuilder.glass(): MaterialBuilder =
  sampler("reflected")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "reflectionStrength")

/**
 * What a die's surface reads beyond colour, roughness and metalness: its
 * artwork, its printed numbers and its lacquer. Declared in the order they
 * always were, so the dice compile to the packet they always did. The glass
 * floor declares them too: it is the opaque surface with the dice added.
 */
internal fun MaterialBuilder.diceParameters(): MaterialBuilder =
  uniformParameter(MaterialBuilder.UniformType.FLOAT, "textured")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "numbered")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "inkColor")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoat")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoatRoughness")
    .sampler("atlas")
    .sampler("glyphs")

/** And a table's: its three pictures, and whether it has the two maps. */
internal fun MaterialBuilder.tableParameters(): MaterialBuilder =
  uniformParameter(MaterialBuilder.UniformType.FLOAT, "hasNormal")
    .uniformParameter(MaterialBuilder.UniformType.FLOAT, "hasRoughness")
    .sampler("albedo")
    .sampler("normalMap")
    .sampler("roughnessMap")

/** A two-dimensional texture the material samples, named [name] in its source. */
internal fun MaterialBuilder.sampler(name: String): MaterialBuilder =
  samplerParameter(
    MaterialBuilder.SamplerType.SAMPLER_2D,
    MaterialBuilder.SamplerFormat.FLOAT,
    MaterialBuilder.ParameterPrecision.DEFAULT,
    name,
  )
