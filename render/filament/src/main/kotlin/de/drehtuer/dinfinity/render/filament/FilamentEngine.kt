package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Material
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
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
 */
class FilamentEngine(
  artwork: (String) -> AtlasImage? = { null },
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
  val material: Material = compileMaterial(engine, blended = false)

  /**
   * And the same material again, blended.
   *
   * Whether a surface is blended is fixed when a material is *compiled* —
   * Filament bakes the blending mode into the shader, because a blended
   * surface is drawn in a different pass, in a different order, against a
   * depth buffer it does not write to. So a translucent die cannot be the
   * opaque material with a different parameter; it is a second material, from
   * the same source, and [materialFor] is what picks.
   *
   * The source is shared rather than copied because the two must agree about
   * every other thing they draw: the same body, the same printed numbers, the
   * same artwork over them. Only the blending differs, and only Filament's
   * builder knows it does.
   */
  val blendedMaterial: Material = compileMaterial(engine, blended = true)

  /** Which of the two [parameters] is to be drawn with. */
  fun materialFor(parameters: DiceMaterial.Parameters): Material = if (parameters.blended) blendedMaterial else material

  /**
   * Every package's artwork that has been asked for, uploaded once.
   *
   * Held here rather than on a stage because a `Texture` is a native handle
   * and a surface comes and goes: an atlas re-uploaded on every rotation is a
   * leak the JVM cannot see. It is given back in [close], before the engine
   * that owns the handles ([AtlasCache]).
   */
  val atlases: AtlasCache<Texture> =
    AtlasCache(artwork = artwork, upload = { uploadAtlas(engine, it) }, destroy = engine::destroyTexture)

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
    atlases.close()
    engine.destroyTexture(blank)
    engine.destroyMaterial(material)
    engine.destroyMaterial(blendedMaterial)
    engine.destroy()
  }

  private companion object {
    /** Red, green, blue and alpha, a byte each. */
    const val PIXEL_BYTES = 4

    /** Every channel of the blank texture, which multiplies a colour by one. */
    const val OPAQUE_WHITE = 0xFF.toByte()

    fun compileMaterial(
      engine: Engine,
      blended: Boolean,
    ): Material {
      MaterialBuilder.init()
      try {
        val packet =
          MaterialBuilder()
            .name(if (blended) "dinfinity-blended" else "dinfinity")
            .material(DiceMaterial.SOURCE)
            .shading(MaterialBuilder.Shading.LIT)
            // `TRANSPARENT` rather than `FADE`: a die you can see into is a
            // solid object made of clear stuff, so its own lighting — the
            // sheen down one edge, the shadowed side — is *there* and belongs
            // in the picture. `FADE` would take it out in proportion to how
            // clear the die is, which is what a ghost looks like.
            //
            // It also means the shader hands over a colour already multiplied
            // by its coverage, which `DiceMaterial.SOURCE` does.
            .blending(
              if (blended) MaterialBuilder.BlendingMode.TRANSPARENT else MaterialBuilder.BlendingMode.OPAQUE,
            )
            // **Off, and the numbers are upside down without it.**
            //
            // `MaterialBuilder` defaults this to true, which makes `getUV0()`
            // hand the shader `1 - v` instead of the `v` the mesh supplied.
            // That is a kindness to assets authored for a bottom-left origin,
            // and this app has none: every image in it counts rows from the
            // top — a die's printed numbers, a package's atlas, a table's
            // floor ([Snapshot.fromBottomUp] says so, and it is the file that
            // had to put the *readback* the other way round for the same
            // reason).
            //
            // So there were three conventions and the code stated two.
            // `NumberField` is top-down, `setImage` uploads it as it stands so
            // buffer row 0 is `v = 0`, and `SolidFace.cellOf` computes `v`
            // growing down the image to match. The builder then turned that
            // over a second time, and every glyph on every die came out
            // reflected — which a screenshot cannot pin down, because a
            // reflection in `v` and a reflection in `u` differ by a half-turn
            // and a die lands at an arbitrary orientation.
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
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "opacity")
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
            // Every backend this app can meet: compiling on the device is only
            // worth its size if it answers for the driver that is actually
            // here (`docs/architecture.md`, decision 46).
            .targetApi(MaterialBuilder.TargetApi.ALL)
            .optimization(MaterialBuilder.Optimization.PERFORMANCE)
            .build()
        check(packet.isValid) { "the dice material did not compile on this device" }
        return Material.Builder().payload(packet.buffer, packet.buffer.remaining()).build(engine)
      } finally {
        MaterialBuilder.shutdown()
      }
    }

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
