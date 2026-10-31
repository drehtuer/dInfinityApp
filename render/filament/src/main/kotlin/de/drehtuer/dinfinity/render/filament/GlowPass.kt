package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Box
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
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.Renderer as FilamentFrameRenderer

/**
 * The halo round a glowing die: the light the dice give off, drawn on its own,
 * blurred and laid over the frame by the tone pass (`docs/physics-and-rendering.md`,
 * "A die that glows"; `docs/architecture.md`, decision 111).
 *
 * **Why not Filament's bloom.** Bloom blurs whatever in the frame is bright,
 * and the frame does not know which light is a glow: with its threshold on,
 * too little of a lit glowing face is past white to show, and with it off the
 * felt, the walls and every highlight bloom with the dice. So the glow gets a
 * picture of its own — the *mask* — in which every glowing die is drawn a
 * second time in nothing but its glow ([Glow.MASK_SOURCE]), its ink dark, and
 * everything else is black. Only that is blurred, and only that is added.
 *
 * **The blur is the mask's mip chain.** The mask is drawn at a [Glow.MASK_SCALE]th
 * of the screen each way, its smaller levels are made on the GPU
 * ([Texture.generateMipmaps]) and the tone pass reads several of them,
 * each filtered, and adds them by [Glow.HALO_WEIGHTS]: wide and soft for
 * one draw, a mip generation and a few texture reads a pixel, and nothing
 * at all on a tray with no glowing die, which never makes one of these.
 *
 * **Every surface has a twin, and only the glowing ones are lit.** Each
 * surface of the tray and every die is drawn a second time in this pass's
 * scene, on its own vertices, parented to it in Filament's transforms so that
 * [FilamentStage.place] moves both. A surface that does not glow is black
 * here, which is what makes it hide a glowing die behind it: with the
 * glowing dice alone, a die half behind the near wall glowed through it.
 *
 * Built by [FilamentStage] the first time a glowing die is added, with a twin
 * for every surface already on the stage, and held until the stage closes; a
 * throw's twins go with the throw.
 */
internal class GlowPass(
  private val engine: Engine,
  private val material: Material,
  camera: Camera,
  width: Int,
  height: Int,
) : AutoCloseable {
  private val maskWidth = maxOf(1, width / Glow.MASK_SCALE)
  private val maskHeight = maxOf(1, height / Glow.MASK_SCALE)

  /** The mask, with room for every blur [Glow.HALO_WEIGHTS] reads. */
  val mask: Texture =
    Texture
      .Builder()
      .width(maskWidth)
      .height(maskHeight)
      .levels(levelsFor(maskWidth, maskHeight))
      .format(Texture.InternalFormat.RGBA16F)
      .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE or Texture.Usage.GEN_MIPMAPPABLE)
      .build(engine)

  /** How the tone pass reads [mask]: between levels as well as between texels, so the blur has no steps. */
  val sampler: TextureSampler =
    TextureSampler(
      TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
      TextureSampler.MagFilter.LINEAR,
      TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

  private val depth: Texture =
    Texture
      .Builder()
      .width(maskWidth)
      .height(maskHeight)
      .levels(1)
      .format(Texture.InternalFormat.DEPTH24)
      .usage(Texture.Usage.DEPTH_ATTACHMENT)
      .build(engine)

  private val target: RenderTarget =
    RenderTarget
      .Builder()
      .texture(RenderTarget.AttachmentPoint.COLOR, mask)
      .texture(RenderTarget.AttachmentPoint.DEPTH, depth)
      .build(engine)

  private val scene: Scene = engine.createScene()
  private val view: View = engine.createView()

  /** Each surface's twin, by the surface. */
  private val twins = mutableMapOf<Int, Int>()

  /** How many of them glow. */
  private var glowing = 0
  private val instances = mutableListOf<MaterialInstance>()

  init {
    view.scene = scene
    view.camera = camera
    view.viewport = Viewport(0, 0, maskWidth, maskHeight)
    view.renderTarget = target
    // A mask, not a picture: nothing of Filament's post pass, its shadows or
    // its smoothing, and black wherever no glowing die is.
    view.isPostProcessingEnabled = false
    view.setShadowingEnabled(false)
    view.antiAliasing = View.AntiAliasing.NONE
    view.dithering = View.Dithering.NONE
  }

  /** Whether any glowing die is in this throw, which is whether the halo is drawn at all. */
  val active: Boolean get() = glowing > 0

  /**
   * Draws [surface] into the mask as well — in its glow, or black — as a twin
   * on its own shape, following it wherever it is placed.
   */
  fun add(surface: Surface) {
    val parameters = surface.parameters
    val textures = surface.textures
    val instance =
      material.createInstance().apply {
        setParameter(
          "glow",
          parameters.glow.red.toFloat(),
          parameters.glow.green.toFloat(),
          parameters.glow.blue.toFloat(),
        )
        setParameter("textured", if (textures.atlas != null) 1.0f else 0.0f)
        setParameter("atlas", textures.atlas ?: textures.blank, textures.sampler)
        setParameter("numbered", if (textures.glyphs != null) 1.0f else 0.0f)
        setParameter("glyphs", textures.glyphs ?: textures.blank, textures.glyphSampler)
      }
    val twin = EntityManager.get().create()
    RenderableManager
      .Builder(1)
      .boundingBox(surface.shape.bounds)
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, surface.shape.vertices, surface.shape.triangles)
      .material(0, instance)
      .castShadows(false)
      .receiveShadows(false)
      .build(engine, twin)
    val transforms = engine.transformManager
    transforms.setParent(transforms.getInstance(twin), transforms.getInstance(surface.entity))
    scene.addEntity(twin)
    twins[surface.entity] = twin
    instances += instance
    if (Glow.glows(parameters)) glowing++
  }

  /** Takes [die]'s twin out of the mask with it ([Stage.take]). */
  fun take(die: Int) {
    twins[die]?.let(scene::removeEntity)
  }

  /** And puts it back ([Stage.put]). */
  fun put(die: Int) {
    val twin = twins[die] ?: return
    if (!scene.hasEntity(twin)) scene.addEntity(twin)
  }

  /** Draws the mask and blurs it, for the tone pass to read; nothing when no die glows. */
  fun render(frames: FilamentFrameRenderer) {
    if (!active) return
    frames.render(view)
    mask.generateMipmaps(engine)
  }

  /** Gives back every twin of this throw, before the surfaces they follow are destroyed. */
  fun clear() {
    twins.values.forEach {
      scene.removeEntity(it)
      engine.destroyEntity(it)
      EntityManager.get().destroy(it)
    }
    instances.forEach(engine::destroyMaterialInstance)
    twins.clear()
    instances.clear()
    glowing = 0
  }

  override fun close() {
    clear()
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderTarget(target)
    engine.destroyTexture(depth)
    engine.destroyTexture(mask)
  }

  /** One surface of the stage, as its twin needs it: which entity, its shape, its material and its textures. */
  data class Surface(
    val entity: Int,
    val shape: Shape,
    val parameters: DiceMaterial.Parameters,
    val textures: Textures,
  )

  /** The surface's own buffers, which its twin is drawn from rather than copying. */
  class Shape(
    val vertices: VertexBuffer,
    val triangles: IndexBuffer,
    val bounds: Box,
  )

  /**
   * What a twin samples, as the die it follows samples it: its artwork and
   * its printed numbers, or [blank] where it has none.
   */
  class Textures(
    val atlas: Texture?,
    val glyphs: Texture?,
    val blank: Texture,
    val sampler: TextureSampler,
    val glyphSampler: TextureSampler,
  )

  companion object {
    /**
     * How many levels the mask needs: the full size and one more for each
     * blur [Glow.HALO_WEIGHTS] reads, and never more than halving can make.
     */
    fun levelsFor(
      width: Int,
      height: Int,
    ): Int {
      var halvings = 0
      var side = maxOf(width, height)
      while (side > 1) {
        side /= 2
        halvings++
      }
      return minOf(Glow.HALO_WEIGHTS.size + 1, halvings + 1)
    }
  }
}
