package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.Material
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.filamat.MaterialBuilder
import de.drehtuer.dinfinity.core.model.AtlasImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The half of Filament that outlives a surface.
 *
 * A `SurfaceView`'s surface comes and goes — a rotation, a resize, the lock
 * screen — and Filament fixes its swap chain and viewport when those are made,
 * so each of those is a new [FilamentStage]. What is in this file is
 * everything that has no business being rebuilt when that happens: the engine,
 * the compiled material and the one white pixel every untextured surface
 * samples.
 *
 * The material is the reason this class exists. It is compiled *on the device*,
 * for the driver that is actually there (`docs/architecture.md`, decision 46),
 * and that costs real time — long enough that rebuilding it for every rotation
 * left the tray visibly black while it happened. The engine is not free either.
 * Neither depends on which surface is being drawn to, so neither is given up
 * with one (`docs/TODO.md`, Step 4.1).
 *
 * **One thread owns this**, the same one that owns the stages made from it and
 * the physics world they draw: Filament is not thread-safe and a graphics
 * context belongs where it was made (`docs/architecture.md`, "Threading").
 *
 * Filament hands out native handles rather than objects the garbage collector
 * knows about, so what is made here is destroyed in [close]. A stage made from
 * this must be closed *before* it is.
 *
 * @param artwork where a die's decoded atlas comes from, keyed by [AtlasKey] —
 *   the package it belongs to and the path inside it. Given to the engine
 *   rather than to a stage because artwork belongs to a *package*: it is
 *   decoded once and drawn on every throw of every visit, so it is kept where
 *   the compiled material is kept (`docs/dice-sets.md`, "Textures"). The
 *   default draws nothing, which is what a device test with no packages on
 *   disk wants.
 * @param artworkStamp which version of a key's atlas is on disk, so that a
 *   package rewritten under the same name is decoded again ([AtlasCache]).
 *   The default never changes.
 * @param materials where the compiled material is kept between launches
 *   ([MaterialCache]). The default keeps nothing and compiles every time,
 *   which is what a device test wants: it measures the compiler, not a file.
 * @param environment the photographed room the tray is lit by, as the bytes
 *   of a Radiance `.hdr` panorama ([StudioLight]); the default is the one this
 *   module ships. Null — or a file [Radiance] cannot decode — lights the tray
 *   with the generated gradient instead ([RoomLight]), which is also what a
 *   device test asks for when it wants the room the studio was calibrated
 *   against.
 * @param studio where the studio's folded cube is kept between launches
 *   ([StudioCache]). The default keeps nothing and folds every time, which is
 *   what a device test that measures the fold wants.
 */
class FilamentEngine(
  artwork: (String) -> AtlasImage? = { null },
  artworkStamp: (String) -> Any? = { null },
  private val materials: MaterialCache = MaterialCache.NONE,
  private val environment: () -> ByteArray? = StudioLight::bytes,
  private val studio: StudioCache = StudioCache.NONE,
) : AutoCloseable {
  init {
    // Safe to call more than once, and nothing below works before it has been.
    Filament.init()
  }

  /** The engine every stage made from this draws with. */
  val engine: Engine = Engine.create()

  /**
   * The dice material, compiled once for this device's driver.
   *
   * Everything that hides what is behind it, which is every surface of the
   * tray and every die a set has not called translucent.
   */
  val material: Material = loadMaterial(engine, materials, DiceMaterial.Variant.OPAQUE)

  private val resin = lazy(LazyThreadSafetyMode.NONE) { loadMaterial(engine, materials, DiceMaterial.Variant.RESIN) }

  /**
   * And the material of a die light passes through.
   *
   * Whether a surface refracts is fixed when a material is *compiled* —
   * Filament draws a refracting surface after the opaque scene, in a pass of
   * its own, looking into a picture of what was drawn before it. So a
   * translucent die cannot be the opaque material with a different parameter;
   * it is a second material, and [materialFor] is what picks
   * ([DiceMaterial.variantOf]).
   *
   * The two share the whole of their surface — the same body, the same printed
   * numbers, the same artwork over them ([DiceMaterial.RESIN_SOURCE]) — and
   * differ only in what happens to the light that is not reflected.
   *
   * **Made the first time a die asks for it**, not with the engine. Most rolls
   * have no translucent die at all, the built-in set has none, and compiling a
   * second material took about two of the five seconds the first launch of a
   * new version spent with a black tray. A translucent die on that first
   * launch pays for it instead, once; after that it is read back from
   * [MaterialCache] like the opaque one. Only ever touched on the thread that
   * owns the engine, so it needs no lock.
   */
  val resinMaterial: Material by resin

  private val glass = lazy(LazyThreadSafetyMode.NONE) { loadMaterial(engine, materials, DiceMaterial.Variant.GLASS) }

  /**
   * And the floor of a table glossy enough to show the dice in it
   * ([DiceMaterial.GLASS_SOURCE], [Reflection]).
   *
   * A third material rather than a parameter of the first, so that a table
   * that reflects nothing samples nothing: felt, oak and the plain table are
   * drawn with [material], exactly as before. Made the first time a glossy
   * table is shown, for the reason [resinMaterial] is.
   */
  val glassMaterial: Material by glass

  /** Which of the three [parameters] is to be drawn with. */
  fun materialFor(parameters: DiceMaterial.Parameters): Material =
    when (DiceMaterial.variantOf(parameters)) {
      DiceMaterial.Variant.OPAQUE -> material
      DiceMaterial.Variant.RESIN -> resinMaterial
      DiceMaterial.Variant.GLASS -> glassMaterial
    }

  /**
   * Every package's artwork that has been asked for, uploaded once.
   *
   * Held here rather than on a stage because a `Texture` is a native handle
   * and a surface comes and goes: an atlas re-uploaded on every rotation is a
   * leak the JVM cannot see. It is given back in [close], before the engine
   * that owns the handles ([AtlasCache]).
   */
  val atlases: AtlasCache<Texture> =
    AtlasCache(
      artwork = artwork,
      upload = { uploadAtlas(engine, it) },
      destroy = engine::destroyTexture,
      stamp = artworkStamp,
    )

  /**
   * A single white pixel, for every surface that has no atlas.
   *
   * Filament will not draw a material whose sampler is unbound, and the
   * material has two of them because most dice carry artwork, printed numbers
   * or both. A surface that has neither is drawn through this, which
   * multiplies its colour by one and is never read for its numbers because the
   * material is told there are none.
   */
  val blank: Texture = whitePixel(engine)

  /** How an atlas is sampled. Stateless, so one is enough. */
  val sampler: TextureSampler =
    TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT)

  /**
   * How a die's printed numbers are sampled.
   *
   * Clamped rather than repeated, because a distance field is a *measurement*
   * and wrapping one puts the far edge of the atlas a pixel away from the near
   * one — which draws a sliver of the `1` cell along the edge of the `20`. The
   * table's textures do repeat, which is why this is a second sampler rather
   * than a change to the first (`docs/tables.md`, "Table looks").
   */
  val glyphSampler: TextureSampler =
    TextureSampler(
      TextureSampler.MinFilter.LINEAR,
      TextureSampler.MagFilter.LINEAR,
      TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

  /**
   * How a frame's light becomes a pixel: **linear**, so a colour lit to a
   * level comes out at that level and nothing between the light and the
   * screen bends it.
   *
   * ACES is a film look. It lifts the midtones and turns saturated colours on
   * the way — a green felt drifts towards cyan. AgX desaturates everything
   * towards a grey photograph. **PBR Neutral was tried and crushed the felt:**
   * below 0.08 it subtracts nearly all of a colour's smallest channel, on the
   * promise that every surface carries the four per cent of white a
   * dielectric reflects under an even white room. Under a lamp most of that
   * reflection goes somewhere the camera is not, and Filament grades in
   * Rec. 2020, where a saturated sRGB green's smallest channel is three times
   * what it is in sRGB. The Pixel 10a drew `#1f5e3a` as (0, 70, 22): the red
   * gone, the blue halved.
   *
   * A linear mapper stops dead at one rather than rolling highlights off, so
   * the exposure leaves the room for it ([TrayLighting.WHITE_LEVEL]): only a
   * pure white face turned square to the lamp, and the lamp's own reflection
   * in the lacquer, reach white. A set's colours are part of its design, and
   * this is the mapper that keeps them.
   *
   * One for the engine, made with it: a colour grading is a small lookup
   * table Filament bakes once, not something worth rebuilding per surface.
   */
  val colorGrading: ColorGrading =
    ColorGrading
      .Builder()
      .toneMapper(ToneMapper.Linear())
      .build(engine)

  /** What [room] is lit by, once it is made: true for the studio, false for the gradient. */
  var litByStudio: Boolean = false
    private set

  /** True when the studio's folded cube was read back from [studio] rather than folded. */
  var studioFromDisk: Boolean = false
    private set

  /** The textures [room] samples, given back with it. */
  private val roomTextures = mutableListOf<Texture>()

  private val roomLight = lazy(LazyThreadSafetyMode.NONE) { buildRoom() }

  /**
   * The room the tray is lit by, made the first time a stage is lit and kept
   * for as long as the engine is.
   *
   * Kept here rather than on a stage for the material's reason: decoding a
   * panorama and prefiltering it costs real time, and none of it depends on
   * the surface. A rotation re-lights a new stage with the same room. The
   * work is done on the roll thread, the first time the tray is drawn,
   * rather than with the engine, so a screen that never draws the tray —
   * power saving, the table picker before it needs a picture — pays nothing
   * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
   */
  val room: IndirectLight by roomLight

  /**
   * The studio if [environment] has one that decodes, and the gradient if
   * not. Either way the felt receives the same light from it
   * ([TrayLighting.studioIntensity]), and either way the fill lamp is folded
   * into its irradiance, because Filament draws only one directional light
   * and the key is that one ([FillLight]).
   */
  private fun buildRoom(): IndirectLight {
    val faces = environment()?.let { hdr -> studio.faces(hdr) { Radiance.decode(hdr)?.let(StudioCube::faces) } }
    if (faces != null) {
      val cube = studio(engine, faces.buffer).also { roomTextures += it }
      litByStudio = true
      studioFromDisk = faces.fromDisk
      return IndirectLight
        .Builder()
        .reflections(cube)
        .irradiance(StudioLight.BANDS, FillLight.inStudio())
        .rotation(TrayLighting.studioRotation())
        .intensity(TrayLighting.studioIntensity().toFloat())
        .build(engine)
    }
    val gradient = gradient(engine).also { roomTextures += it }
    return IndirectLight
      .Builder()
      .irradiance(StudioLight.BANDS, FillLight.inGradient())
      .reflections(gradient)
      .intensity(TrayLighting.gradientIntensity().toFloat())
      .build(engine)
  }

  /**
   * Somewhere to draw, this big, sharing everything above.
   *
   * @param surface an Android `Surface`, or null for a swap chain with nothing
   *   on the other end — which is what a test on a device uses.
   */
  fun stage(
    surface: Any?,
    width: Int,
    height: Int,
    postProcessing: Boolean = true,
    atlases: (String) -> Texture? = this.atlases::of,
  ): FilamentStage =
    FilamentStage(
      width = width,
      height = height,
      surface = surface,
      postProcessing = postProcessing,
      atlases = atlases,
      shared = this,
    )

  override fun close() {
    if (roomLight.isInitialized()) engine.destroyIndirectLight(room)
    roomTextures.forEach(engine::destroyTexture)
    roomTextures.clear()
    engine.destroyColorGrading(colorGrading)
    atlases.close()
    engine.destroyTexture(blank)
    engine.destroyMaterial(material)
    if (resin.isInitialized()) engine.destroyMaterial(resinMaterial)
    if (glass.isInitialized()) engine.destroyMaterial(glassMaterial)
    engine.destroy()
  }

  private companion object {
    /** Red, green, blue and alpha, a byte each. */
    const val PIXEL_BYTES = 4

    /** Every channel of the blank texture, which multiplies a colour by one. */
    const val OPAQUE_WHITE = 0xFF.toByte()

    /**
     * The photographed room as a reflection cubemap, prefiltered for every
     * roughness, from its six folded [faces].
     *
     * Decoded and folded into a cube on this thread ([Radiance],
     * [StudioCube.faces]) or read back folded ([StudioCache]), then
     * prefiltered by Filament's own
     * `generatePrefilterMipmap` — the same CPU prefilter the gradient goes
     * through, spread over the engine's worker threads — into
     * `R11F_G11F_B10F`: a third less memory than half floats, and a panorama
     * has no use for an alpha channel or for more than three significant
     * digits of a window's brightness.
     *
     * Mirroring is off: the faces are already in the frame [StudioLight]'s
     * irradiance was projected in, and mirroring them would put the window a
     * die reflects on the other side from the one that lights it.
     */
    fun studio(
      engine: Engine,
      faces: ByteBuffer,
    ): Texture {
      val options =
        Texture.PrefilterOptions().apply {
          sampleCount = StudioCube.PREFILTER_SAMPLES
          mirror = false
        }
      return prefiltered(engine, StudioCube.FACE_SIZE, StudioCube.LEVELS, faces, options)
    }

    /**
     * The generated room as a small cubemap, which is what a polished surface
     * reflects when there is no studio to.
     *
     * Only the sharp level is ours; every coarser one is prefiltered by
     * Filament from it, for the roughness each level stands for
     * ([RoomLight.LEVELS] says why, and why it cannot be uploaded level by
     * level).
     */
    fun gradient(engine: Engine): Texture {
      val faces = RoomLight.faces()
      val buffer = ByteBuffer.allocateDirect(faces.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
      buffer.asFloatBuffer().put(faces)
      return prefiltered(engine, RoomLight.SIZE, RoomLight.LEVELS, buffer, Texture.PrefilterOptions())
    }

    /**
     * A cubemap of [size] pixels a face and [levels] levels, its sharp level
     * [faces] — six faces of linear RGB floats, end to end, in native order —
     * and every coarser one prefiltered from it by Filament.
     */
    private fun prefiltered(
      engine: Engine,
      size: Int,
      levels: Int,
      faces: ByteBuffer,
      options: Texture.PrefilterOptions,
    ): Texture {
      val texture =
        Texture
          .Builder()
          .width(size)
          .height(size)
          .depth(RoomLight.FACES)
          .levels(levels)
          .format(Texture.InternalFormat.R11F_G11F_B10F)
          .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
          .build(engine)
      texture.generatePrefilterMipmap(
        engine,
        Texture.PixelBufferDescriptor(faces, Texture.Format.RGB, Texture.Type.FLOAT),
        RoomLight.faceOffsets(size),
        options,
      )
      return texture
    }

    /**
     * The [variant] of the dice material for [engine], from [materials] if it
     * was compiled on an earlier launch.
     */
    fun loadMaterial(
      engine: Engine,
      materials: MaterialCache,
      variant: DiceMaterial.Variant,
    ): Material {
      val target = targetOf(engine.backend)
      val key = MaterialCache.keyOf(variant.fingerprint, backend = target.name, variant = variant.key)
      val packet = materials.packet(key) { compileMaterial(target, variant) }
      return Material.Builder().payload(packet, packet.remaining()).build(engine)
    }

    /**
     * Only the backend the engine actually runs on.
     *
     * Every other target is shader code compiled to be thrown away, and on
     * the Pixel 10a `ALL` roughly tripled the time `libfilamat` took
     * (`docs/physics-and-rendering.md`, "Rendering (normal mode)"). `ALL` stays for a backend
     * this list does not know, which costs time but never draws nothing.
     */
    fun targetOf(backend: Engine.Backend): MaterialBuilder.TargetApi =
      when (backend) {
        Engine.Backend.OPENGL -> MaterialBuilder.TargetApi.OPENGL
        Engine.Backend.VULKAN -> MaterialBuilder.TargetApi.VULKAN
        else -> MaterialBuilder.TargetApi.ALL
      }

    fun compileMaterial(
      target: MaterialBuilder.TargetApi,
      variant: DiceMaterial.Variant,
    ): ByteBuffer {
      MaterialBuilder.init()
      try {
        val packet =
          MaterialBuilder()
            .name(
              when (variant) {
                DiceMaterial.Variant.OPAQUE -> "dinfinity"
                DiceMaterial.Variant.RESIN -> "dinfinity-resin"
                DiceMaterial.Variant.GLASS -> "dinfinity-glass"
              },
            ).material(variant.source)
            // `LIT` for resin too, not `SUBSURFACE`. Filament's subsurface
            // model is a wrap of the direct lights around the back of a thin
            // object; it cannot refract, has no clear coat, and shows nothing
            // of what is behind the die — which is the whole of what makes
            // resin read as resin (`docs/architecture.md`, decision 90).
            .shading(MaterialBuilder.Shading.LIT)
            // Opaque for both. A refracting surface is *not* blended: Filament
            // draws it after everything opaque, into the same depth buffer,
            // and makes its see-through look by sampling a picture of the
            // opaque scene rather than by letting the blend show it.
            .blending(MaterialBuilder.BlendingMode.OPAQUE)
            .apply { if (variant == DiceMaterial.Variant.RESIN) resin() }
            .apply { if (variant == DiceMaterial.Variant.GLASS) glass() }
            // A rounded edge's glint is thinner than a pixel, and without this
            // it is drawn as a broken white line along the edge rather than
            // spread over the bend ([DiceMaterial.SPECULAR_AA_VARIANCE]). A
            // flat surface's normal does not change, so nothing flat moves.
            .specularAntiAliasing(true)
            .specularAntiAliasingVariance(DiceMaterial.SPECULAR_AA_VARIANCE)
            .specularAntiAliasingThreshold(DiceMaterial.SPECULAR_AA_THRESHOLD)
            // **Off, and the numbers are upside down without it.**
            //
            // `MaterialBuilder` defaults this to true, which makes `getUV0()`
            // hand the shader `1 - v` instead of the `v` the mesh supplied.
            // That is a kindness to assets authored for a bottom-left origin,
            // and this app has none: every image in it counts rows from the
            // top — a die's printed numbers, a package's atlas, a table's
            // floor, and the frames read back off the GPU ([Snapshot]).
            //
            // So there were three conventions and the code stated two.
            // `NumberField` is top-down, `setImage` uploads it as it stands so
            // buffer row 0 is `v = 0`, and `SolidFace.cellOf` computes `v`
            // growing down the image to match. The builder then turned that
            // over a second time, and every glyph on every die came out
            // reflected — which a screenshot cannot pin down, because a
            // reflection in `v` and a reflection in `u` differ by a half-turn
            // and a die lands at an arbitrary orientation. The device suite
            // pins it instead: a face turned square to the camera, the right
            // way up, read back and asked which way its ink leans on each axis
            // (`PrintedNumbersDeviceTest`). Turn this back on and the
            // top-bottom question fails on every die.
            //
            // It governs the artwork atlas and the table's floor as well, and
            // those were reflected too; nothing shipped an asymmetric one, so
            // only the numbers showed it.
            .flipUV(false)
            .require(MaterialBuilder.VertexAttribute.UV0)
            .require(MaterialBuilder.VertexAttribute.TANGENTS)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "textured")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "numbered")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "inkColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoat")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "clearCoatRoughness")
            .samplerParameter(
              MaterialBuilder.SamplerType.SAMPLER_2D,
              MaterialBuilder.SamplerFormat.FLOAT,
              MaterialBuilder.ParameterPrecision.DEFAULT,
              "atlas",
            ).samplerParameter(
              MaterialBuilder.SamplerType.SAMPLER_2D,
              MaterialBuilder.SamplerFormat.FLOAT,
              MaterialBuilder.ParameterPrecision.DEFAULT,
              "glyphs",
            ).platform(MaterialBuilder.Platform.MOBILE)
            // The driver that is actually here (`docs/architecture.md`,
            // decision 46), and no other ([targetOf]).
            .targetApi(target)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build()
        check(packet.isValid) { "the dice material did not compile on this device" }
        return packet.buffer
      } finally {
        MaterialBuilder.shutdown()
      }
    }

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
    fun MaterialBuilder.resin(): MaterialBuilder =
      refractionMode(MaterialBuilder.RefractionMode.SCREEN_SPACE)
        .refractionType(MaterialBuilder.RefractionType.SOLID)
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "transmission")
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "scatter")
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "ior")
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "thickness")
        .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "tint")

    /**
     * What the glass variant adds: the picture of the dice seen from under the
     * floor, and how much of it the table shows ([DiceMaterial.GLASS_SOURCE]).
     *
     * Its own picture rather than Filament's screen-space reflections, whose
     * material switch (`reflectionMode`) is left at its default: those reflect
     * the walls as well as the dice (`Reflection`).
     */
    fun MaterialBuilder.glass(): MaterialBuilder =
      samplerParameter(
        MaterialBuilder.SamplerType.SAMPLER_2D,
        MaterialBuilder.SamplerFormat.FLOAT,
        MaterialBuilder.ParameterPrecision.DEFAULT,
        "reflected",
      ).uniformParameter(MaterialBuilder.UniformType.FLOAT, "reflectionStrength")

    /**
     * A decoded atlas, uploaded as it stands.
     *
     * `RGBA8` and straight alpha, because the material blends the artwork over
     * the die's printed label by the artwork's own alpha and a premultiplied
     * edge would drag every soft pixel towards the body colour
     * ([AtlasImage]). One level: a die is looked at from a hand's distance and
     * the atlas is already the larger of the two sizes involved.
     */
    fun uploadAtlas(
      engine: Engine,
      image: AtlasImage,
    ): Texture {
      val texture =
        Texture
          .Builder()
          .width(image.width)
          .height(image.height)
          .levels(1)
          .format(Texture.InternalFormat.RGBA8)
          .build(engine)
      val pixels = ByteBuffer.allocateDirect(image.pixels.size).order(ByteOrder.nativeOrder())
      pixels.put(image.pixels)
      pixels.flip()
      texture.setImage(engine, 0, Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE))
      return texture
    }

    fun whitePixel(engine: Engine): Texture {
      val texture =
        Texture
          .Builder()
          .width(1)
          .height(1)
          .levels(1)
          .format(Texture.InternalFormat.RGBA8)
          .build(engine)
      val pixel = ByteBuffer.allocateDirect(PIXEL_BYTES).order(ByteOrder.nativeOrder())
      repeat(PIXEL_BYTES) { pixel.put(OPAQUE_WHITE) }
      pixel.flip()
      texture.setImage(
        engine,
        0,
        Texture.PixelBufferDescriptor(pixel, Texture.Format.RGBA, Texture.Type.UBYTE),
      )
      return texture
    }
  }
}
