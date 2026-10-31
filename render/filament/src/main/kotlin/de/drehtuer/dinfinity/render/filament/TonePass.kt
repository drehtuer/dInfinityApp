package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.google.android.filament.Renderer as FilamentFrameRenderer

/**
 * The last pass of every frame anybody looks at: the graded frame, bent past
 * white by [ToneCurve] and written to the screen (`docs/architecture.md`,
 * decision 108).
 *
 * The tray's own view keeps everything it had — the multisampling, the
 * shadows, Filament's grading — and is pointed at a half-float frame of this
 * pass's instead of at the screen ([takeOver]). A second view then draws one
 * triangle over the whole screen with [material], which reads that frame
 * pixel for pixel. The tray's view does not dither: the frame is half floats,
 * which do not band, and this pass dithers once, on the way to eight bits.
 *
 * What it costs is one full-screen pass: a read of the frame and a write of
 * the screen, about the work Filament's own grading pass does.
 *
 * @param material [FilamentEngine.toneMaterial].
 * @param blank what it reads in place of a glow mask until there is one
 *   ([haloFrom]); read at a strength of nought, so its colour never shows.
 */
internal class TonePass(
  private val engine: Engine,
  material: Material,
  private val width: Int,
  private val height: Int,
  blank: Texture,
) : AutoCloseable {
  private val frame: Texture =
    Texture
      .Builder()
      .width(width)
      .height(height)
      .levels(1)
      .format(Texture.InternalFormat.RGBA16F)
      .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE or Texture.Usage.BLIT_SRC)
      .build(engine)

  private val target: RenderTarget =
    RenderTarget
      .Builder()
      .texture(RenderTarget.AttachmentPoint.COLOR, frame)
      .build(engine)

  private val instance: MaterialInstance =
    material.createInstance().apply {
      setParameter(
        "frame",
        frame,
        com.google.android.filament
          .TextureSampler(),
      )
      setParameter("halo", 0.0f)
      setParameter(
        "glow",
        blank,
        com.google.android.filament
          .TextureSampler(),
      )
    }

  private val vertices: VertexBuffer =
    VertexBuffer
      .Builder()
      .vertexCount(CORNERS)
      .bufferCount(1)
      .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT4, 0, POSITION_BYTES)
      .build(engine)
      .also { it.setBufferAt(engine, 0, floats(COVERING_TRIANGLE)) }

  private val triangle: IndexBuffer =
    IndexBuffer
      .Builder()
      .indexCount(CORNERS)
      .bufferType(IndexBuffer.Builder.IndexType.USHORT)
      .build(engine)
      .also { it.setBuffer(engine, shorts(shortArrayOf(0, 1, 2))) }

  private val entity: Int = EntityManager.get().create()
  private val cameraEntity: Int = EntityManager.get().create()
  private val camera: Camera = engine.createCamera(cameraEntity)
  private val scene: Scene = engine.createScene()
  private val view: View = engine.createView()

  init {
    RenderableManager
      .Builder(1)
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, triangle)
      .material(0, instance)
      .culling(false)
      .castShadows(false)
      .receiveShadows(false)
      .build(engine, entity)
    scene.addEntity(entity)
    view.scene = scene
    view.camera = camera
    view.viewport = Viewport(0, 0, width, height)
    // Nothing of Filament's post pass: this view's output is the screen's
    // picture already, encoded and dithered by [ToneCurve.SOURCE].
    view.isPostProcessingEnabled = false
    view.setShadowingEnabled(false)
    view.antiAliasing = View.AntiAliasing.NONE
    view.dithering = View.Dithering.NONE
  }

  /** Points the tray's [tray] view at this pass's frame instead of the screen. */
  fun takeOver(tray: View) {
    tray.renderTarget = target
    tray.dithering = View.Dithering.NONE
  }

  /**
   * Lays [glow]'s blurred mask over the frame from now on, at
   * [Glow.HALO_STRENGTH] while [on], and not at all while not
   * (`docs/physics-and-rendering.md`, "A die that glows").
   */
  fun haloFrom(
    glow: GlowPass,
    on: Boolean,
  ) {
    instance.setParameter("glow", glow.mask, glow.sampler)
    instance.setParameter("halo", if (on) Glow.HALO_STRENGTH.toFloat() else 0.0f)
  }

  /** Draws the screen's picture from the frame the tray's view has just drawn. */
  fun render(frames: FilamentFrameRenderer) {
    frames.render(view)
  }

  /**
   * The frame the tray's view drew, as light: linear, one being white, up to
   * [ToneCurve.HEADROOM]. Read during the frame, after [render]'s view has
   * been queued; the caller flushes before it looks.
   */
  fun readLight(frames: FilamentFrameRenderer): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(width * height * CHANNELS * HALF_BYTES).order(ByteOrder.nativeOrder())
    frames.readPixels(
      target,
      0,
      0,
      width,
      height,
      Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.HALF),
    )
    return buffer
  }

  override fun close() {
    engine.destroyEntity(entity)
    EntityManager.get().destroy(entity)
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyCameraComponent(cameraEntity)
    EntityManager.get().destroy(cameraEntity)
    engine.destroyMaterialInstance(instance)
    engine.destroyVertexBuffer(vertices)
    engine.destroyIndexBuffer(triangle)
    engine.destroyRenderTarget(target)
    engine.destroyTexture(frame)
  }

  companion object {
    /**
     * One triangle that covers the screen, in device coordinates: corners
     * at (−1, −1), (3, −1) and (−1, 3), so the screen's square is inside it
     * and no seam runs across it the way two triangles' diagonal would.
     * Half way in depth, which every backend's clip space contains.
     */
    private val COVERING_TRIANGLE =
      floatArrayOf(
        -1f,
        -1f,
        HALF_DEPTH,
        1f,
        3f,
        -1f,
        HALF_DEPTH,
        1f,
        -1f,
        3f,
        HALF_DEPTH,
        1f,
      )

    /**
     * What a frame read back by [readLight] holds, pixel by pixel, as light:
     * each half float decoded, taken out of the sRGB the grading wrote it
     * in, and brought back up by [ToneCurve.HEADROOM]. Red, green and blue
     * per pixel, rows as Filament hands them over.
     */
    fun lightOf(
      halves: ByteBuffer,
      pixels: Int,
    ): FloatArray {
      val light = FloatArray(pixels * RGB)
      val shorts = halves.order(ByteOrder.nativeOrder()).asShortBuffer()
      for (pixel in 0 until pixels) {
        for (channel in 0 until RGB) {
          val stored = halfToFloat(shorts.get(pixel * CHANNELS + channel)).toDouble()
          light[pixel * RGB + channel] = (ToneCurve.decoded(stored) * ToneCurve.HEADROOM).toFloat()
        }
      }
      return light
    }

    /** An IEEE half float, as the float it is. */
    fun halfToFloat(half: Short): Float {
      val bits = half.toInt() and HALF_MASK
      val sign = if (bits and HALF_SIGN != 0) -1f else 1f
      val exponent = (bits shr HALF_MANTISSA_BITS) and HALF_EXPONENT_MASK
      val mantissa = bits and HALF_MANTISSA_MASK
      return when (exponent) {
        0 -> sign * mantissa * SUBNORMAL_SCALE
        HALF_EXPONENT_MASK -> if (mantissa == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
        else -> sign * (1f + mantissa / MANTISSA_SCALE) * Math.scalb(1f, exponent - HALF_BIAS)
      }
    }

    private fun floats(values: FloatArray): ByteBuffer =
      ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(values)
      }

    private fun shorts(values: ShortArray): ByteBuffer =
      ByteBuffer.allocateDirect(values.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
        asShortBuffer().put(values)
      }

    private const val CORNERS = 3
    private const val HALF_DEPTH = 0.5f
    private const val CHANNELS = 4
    private const val RGB = 3
    private const val HALF_BYTES = 2
    private const val POSITION_BYTES = 16

    private const val HALF_MASK = 0xFFFF
    private const val HALF_SIGN = 0x8000
    private const val HALF_MANTISSA_BITS = 10
    private const val HALF_MANTISSA_MASK = 0x3FF
    private const val HALF_EXPONENT_MASK = 0x1F
    private const val HALF_BIAS = 15
    private const val MANTISSA_SCALE = 1024f

    /** A subnormal half is its mantissa times 2^−24. */
    private const val SUBNORMAL_SCALE = 1f / 16_777_216f
  }
}
