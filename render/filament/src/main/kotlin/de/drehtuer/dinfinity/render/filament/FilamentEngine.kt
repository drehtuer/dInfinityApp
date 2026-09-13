package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Material
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.filamat.MaterialBuilder
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
 */
class FilamentEngine : AutoCloseable {
  init {
    // Safe to call more than once, and nothing below works before it has been.
    Filament.init()
  }

  /** The engine every stage made from this draws with. */
  val engine: Engine = Engine.create()

  /** The dice material, compiled once for this device's driver. */
  val material: Material = compileMaterial(engine)

  /**
   * A single white pixel, for every surface that has no atlas.
   *
   * Filament will not draw a material whose sampler is unbound, and the
   * material has one because most dice do carry artwork. A die that does not
   * is drawn through this, which multiplies its colour by one.
   */
  val blank: Texture = whitePixel(engine)

  /** How an atlas is sampled. Stateless, so one is enough. */
  val sampler: TextureSampler =
    TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT)

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
    atlases: (String) -> Texture? = { null },
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
    engine.destroyTexture(blank)
    engine.destroyMaterial(material)
    engine.destroy()
  }

  private companion object {
    /** Red, green, blue and alpha, a byte each. */
    const val PIXEL_BYTES = 4

    /** Every channel of the blank texture, which multiplies a colour by one. */
    const val OPAQUE_WHITE = 0xFF.toByte()

    fun compileMaterial(engine: Engine): Material {
      MaterialBuilder.init()
      try {
        val packet =
          MaterialBuilder()
            .name("dinfinity")
            .material(DiceMaterial.SOURCE)
            .shading(MaterialBuilder.Shading.LIT)
            .blending(MaterialBuilder.BlendingMode.OPAQUE)
            .require(MaterialBuilder.VertexAttribute.UV0)
            .require(MaterialBuilder.VertexAttribute.TANGENTS)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "textured")
            .samplerParameter(
              MaterialBuilder.SamplerType.SAMPLER_2D,
              MaterialBuilder.SamplerFormat.FLOAT,
              MaterialBuilder.ParameterPrecision.DEFAULT,
              "atlas",
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
