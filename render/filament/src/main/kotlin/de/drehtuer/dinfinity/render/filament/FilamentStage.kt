package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.google.android.filament.Renderer as FilamentFrameRenderer

/**
 * Everything Filament owns, and the only file in this module that talks to it.
 *
 * The same split `simulation/jolt` uses (`docs/architecture.md`, decision 40):
 * every decision about how a roll looks — where the camera stands, what shape
 * a die is, which numbers its material takes, where it is between two
 * simulation steps — is made in Kotlin and tested on a JVM. What is left here
 * is buffers, handles and a draw call, and the only question worth asking of
 * it is whether it runs on a real GPU.
 *
 * Filament hands out native handles, not objects the garbage collector knows
 * about, so everything made here is destroyed in [close] in the reverse order
 * it was made. A missed one is a leak the JVM cannot see.
 *
 * **What is here is what a surface owns.** The swap chain is made from the
 * surface and the viewport from its size, so both die with it. The engine, the
 * compiled material and the blank texture do not: they are [FilamentEngine],
 * they cost real time to make, and rebuilding them for every rotation is what
 * used to leave the tray black for a moment (`docs/TODO.md`, Step 4.1). A
 * stage given one shares it and leaves it alone; a stage given none makes one
 * of its own and gives it back in [close], which is what a test that wants a
 * stage and nothing else does.
 *
 * Seventeen small methods rather than eleven larger ones is deliberate and is
 * why the class carries a suppression: this is the file that has to be read
 * against Filament's own documentation, and a method per thing Filament makes
 * is what makes that possible. Folding them together would save a count and
 * cost the one property this file has.
 *
 * @param width the viewport, in pixels.
 * @param height the same.
 * @param surface an Android `Surface` to draw into, or null for a swap chain
 *   with nothing on the other end — which is what a test on a device uses and
 *   what power-saving mode never creates at all.
 * @param postProcessing tone mapping and the rest of Filament's post pass. On
 *   for anything anybody looks at. Off only for reading a headless frame back
 *   on a software backend, where the post-processed result never arrives —
 *   see the device suite, which says why at more length.
 * @param shared the engine and the material to draw with, or null to make a
 *   private one that is destroyed with this stage.
 */
@Suppress("TooManyFunctions")
class FilamentStage(
  override val width: Int,
  override val height: Int,
  surface: Any? = null,
  postProcessing: Boolean = true,
  private val atlases: (String) -> DieArtwork<Texture>? = { null },
  shared: FilamentEngine? = null,
) : Stage,
  AutoCloseable {
  /** Made here only when nobody handed one in, and then given back in [close]. */
  private val own: FilamentEngine? = if (shared == null) FilamentEngine() else null

  private val parts: FilamentEngine = shared ?: requireNotNull(own)
  private val engine: Engine = parts.engine
  private val frames: FilamentFrameRenderer = engine.createRenderer()
  private val scene: Scene = engine.createScene()
  private val view: View = engine.createView()
  private val cameraEntity: Int = EntityManager.get().create()
  private val swapChain: SwapChain =
    if (surface == null) {
      // Readable on purpose: a swap chain with nothing on the other end exists
      // to be read back, and one that is not flagged for it returns a blank
      // frame. A real driver may hand the pixels over anyway — the Pixel 10a's
      // does — which is exactly how this would have shipped unnoticed.
      engine.createSwapChain(width, height, SwapChainFlags.CONFIG_READABLE)
    } else {
      engine.createSwapChain(surface, SwapChainFlags.CONFIG_DEFAULT)
    }

  private val camera: com.google.android.filament.Camera = engine.createCamera(cameraEntity)

  /** The white pixel every surface with no atlas samples. Shared, like the material. */
  private val blank: Texture get() = parts.blank

  private val sampler: TextureSampler get() = parts.sampler

  private val glyphSampler: TextureSampler get() = parts.glyphSampler

  /**
   * How the floor reads the picture of the dice in it: smoothly, which is the
   * blur ([Reflection.SHRINK]), and clamped, so the edge of the screen does
   * not reflect the opposite edge.
   */
  private val mirrorSampler =
    TextureSampler(
      TextureSampler.MinFilter.LINEAR,
      TextureSampler.MagFilter.LINEAR,
      TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

  private val instances = mutableListOf<MaterialInstance>()

  /**
   * The printed-number fields this scene uploaded, which it also destroys.
   *
   * Unlike an author's atlas — which is decoded once for the package it came
   * from and outlives any one throw — a die's printed numbers are made for the
   * die in this throw, so they go when the throw does.
   */
  private val textures = mutableListOf<Texture>()
  private val buffers = mutableListOf<VertexBuffer>()
  private val indices = mutableListOf<IndexBuffer>()
  private val entities = mutableListOf<Int>()

  /**
   * The dice as a glossy table sees them, made the first time one is shown
   * and kept until this surface goes ([Reflection]).
   */
  private var mirror: Mirror? = null

  /** Whether the table in the scene now is one that shows the dice. */
  private var reflecting = false

  /** Where the camera was last aimed, for a [mirror] made after it was. */
  private var shot: CameraShot? = null

  init {
    view.scene = scene
    view.camera = camera
    view.viewport = Viewport(0, 0, width, height)
    view.isPostProcessingEnabled = postProcessing
    photograph()
    // Both layers: the tray is on one of its own only so that the picture a
    // glossy table reflects can leave it out (`Reflection`).
    view.setVisibleLayers(LAYERS, DICE or TRAY)
  }

  /**
   * How a frame is turned into a picture: the tone mapper, the exposure, the
   * anti-aliasing and the shape of the key light's shadow
   * (`docs/physics-and-rendering.md`, "Rendering (normal mode)";
   * `docs/architecture.md`, decision 89). Every number is
   * [TrayLighting]'s, where the reasons for it are and a JVM can check the
   * ones that are arithmetic; this only hands them to Filament.
   */
  private fun photograph() {
    view.colorGrading = parts.colorGrading
    // A real aperture, shutter and ISO, not the one-number overload: that one
    // means something else by "exposure" and blew every frame out to white
    // ([TrayLighting.exposure]).
    camera.setExposure(TrayLighting.APERTURE, TrayLighting.SHUTTER_SECONDS, TrayLighting.sensitivity().toFloat())
    // Multisampling instead of FXAA, not on top of it: the edges that alias are
    // geometry, and the full-screen pass FXAA costs is given back
    // ([TrayLighting.MSAA_SAMPLES]).
    view.antiAliasing = View.AntiAliasing.NONE
    view.multiSampleAntiAliasingOptions =
      View.MultiSampleAntiAliasingOptions().apply {
        enabled = true
        sampleCount = TrayLighting.MSAA_SAMPLES
      }
    // **PCF, not PCSS.** Filament 1.76's PCSS is not percentage-closer at
    // all: its shader samples a mip-mapped exponential *variance* shadow map
    // (`ShadowSample_EVSSM`), and with a variance map the tray's own
    // geometry came back into the shadow — the rim threw a hard dark band
    // across the felt 25 to 40 mm in from the top and left walls, exactly the
    // band `FilamentDiceRenderer.addTray` had removed by stopping the tray
    // casting. Filament's own notes on variance shadows ask for every receiver
    // to be a caster too, and this tray is the one thing that must not cast.
    // PCF reads a plain depth map that holds only what casts — the dice — and
    // is the shadow the tray had before (`docs/physics-and-rendering.md`,
    // "Rendering (normal mode)"; `StudioLightDeviceTest` measures the felt by
    // the walls).
    view.setShadowType(View.ShadowType.PCF)
  }

  /**
   * The exposure the camera actually has, worked out from what Filament kept
   * of its aperture, shutter and ISO — after its clamps, which is the point:
   * a device test compares this with [TrayLighting.exposure].
   */
  internal fun exposure(): Double =
    TrayLighting.exposureOf(camera.aperture.toDouble(), camera.shutterSpeed.toDouble(), camera.sensitivity.toDouble())

  /** Points the camera where [shot] says, for this viewport. */
  override fun aim(shot: CameraShot) {
    this.shot = shot
    point(camera, shot)
    mirror?.let { point(it.camera, Reflection.mirrored(shot)) }
  }

  private fun point(
    camera: com.google.android.filament.Camera,
    shot: CameraShot,
  ) {
    camera.setProjection(
      shot.verticalFieldOfViewDegrees,
      width.toDouble() / height,
      NEAR_MM,
      FAR_MM,
      com.google.android.filament.Camera.Fov.VERTICAL,
    )
    camera.lookAt(
      shot.position.x,
      shot.position.y,
      shot.position.z,
      shot.target.x,
      shot.target.y,
      shot.target.z,
      shot.up.x,
      shot.up.y,
      shot.up.z,
    )
  }

  /**
   * The key light and the room — the whole of the lighting.
   *
   * One directional light throws the shadows that tell a player a die is
   * sitting on the table rather than floating above it. The fill that keeps
   * the shadowed faces from going to black, where a number cannot be read,
   * is part of the room's irradiance rather than a second lamp: Filament
   * draws one directional light per scene, the brightest, so a second one is
   * dropped without a word — and was, on every version of this tray until
   * the device measured it ([FillLight]).
   *
   * The room is not a nicety. A lamp and nothing else means every surface
   * facing away from it is *exactly* black, and the surfaces that face away
   * are the inner walls: a player saw the lit top of the wall, a shadow cast
   * across the floor, and nothing in between casting it. A tray is lit by a
   * room, not by a lamp in a void.
   */
  override fun light() {
    addKeyLight()
    // The room is the engine's, made once and shared by every stage: a studio
    // decoded and prefiltered per rotation would be the black tray decision 50
    // exists to prevent (`FilamentEngine.room`).
    scene.indirectLight = parts.room
    // **No ambient occlusion, because the table must not shade itself.**
    //
    // It was here for the darkening where a die meets the felt: a cast shadow
    // says a die is *over* the table, and contact occlusion is what says it is
    // *on* it. That argument is sound and it is not what this scene needed.
    //
    // Occlusion darkens every concave corner it can see, and the biggest one
    // in the tray is the tray — the join where the wall meets its own floor,
    // which runs the whole way round. So the felt wore a soft dark band
    // hugging the wall, and a band along the rim reads as the rim throwing a
    // shadow. Stopping the tray *casting* (`FilamentDiceRenderer.addTray`)
    // did not touch it, because it was never a cast shadow; the second
    // device session reported it still there, correctly.
    //
    // Filament's occlusion is a property of the view, not of a renderable, so
    // there is no way to ask for it on the dice and not on the tray. What
    // settled it was rendering the tray both ways on the Pixel 10a and
    // looking: with occlusion the felt carries the band, without it the felt
    // is clean **and the die keeps the cast shadow it always had**, which is
    // the thing that was doing the work all along
    // (`docs/physics-and-rendering.md`, "Rendering").
    view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply { enabled = false }
  }

  override fun take(entity: Int) {
    if (entity == Stage.NOTHING) return
    scene.removeEntity(entity)
  }

  override fun drawnCells(
    atlas: String,
    faces: Int,
  ): Set<Int> = DieArtwork.drawnBy(atlases(atlas), faces)

  override fun put(entity: Int) {
    if (entity == Stage.NOTHING || scene.hasEntity(entity)) return
    scene.addEntity(entity)
  }

  override fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
    casts: Boolean,
  ): Int {
    // Nought is Filament's word for "no entity", and a mesh with nothing in it
    // is not worth one.
    if (mesh.triangleCount == 0 || mesh.vertexCount == 0) return Stage.NOTHING
    val vertices = verticesOf(mesh)
    val triangles = indicesOf(mesh)
    // Only a surface drawn with the glass material samples the picture of the
    // dice; a glossy look drawn from pictures sets its reflection aside
    // (`DiceMaterial.variantOf`) and costs no reflection pass.
    val reflected = if (DiceMaterial.reflects(parameters)) mirrorOf().colour else null
    val maps = parameters.maps
    val instance =
      if (maps != null) {
        tableInstanceOf(parameters, maps)
      } else {
        instanceOf(
          parameters,
          parameters.texturePath?.let(atlases)?.texture,
          parameters.numbers?.let(::glyphsOf),
          reflected,
        )
      }
    val entity = EntityManager.get().create()

    RenderableManager
      .Builder(1)
      .boundingBox(boundsOf(mesh))
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, triangles)
      .material(0, instance)
      // The dice cast; the tray does not. Everything receives, including the
      // tray — a die's shadow on the felt is the whole point of having one
      // (`docs/physics-and-rendering.md`, "What is drawn over the table").
      .castShadows(casts)
      .receiveShadows(true)
      // And the same line again for what a glossy table shows: the dice, and
      // none of the tray — the picture is taken from under the floor, which
      // would hide every die, and a wall in it is a dark band along the foot
      // of the wall (`Reflection`).
      .layerMask(LAYERS, if (casts) DICE else TRAY)
      .build(engine, entity)

    if (reflected != null) reflecting = true
    scene.addEntity(entity)
    entities += entity
    buffers += vertices
    indices += triangles
    instances += instance
    return entity
  }

  /**
   * Moves an entity already in the scene.
   *
   * [matrix] is the column-major 4×4 [Transform.of] built, which is where the
   * arithmetic of turning a die's position and orientation into a matrix
   * lives — on the JVM, where it is tested.
   */
  override fun place(
    entity: Int,
    matrix: FloatArray,
  ) {
    val transforms = engine.transformManager
    transforms.setTransform(transforms.getInstance(entity), matrix)
  }

  /** Draws one frame. False when Filament asked to skip it. */
  override fun draw(): Boolean = draw(capture = null)

  /**
   * The same, copying the pixels that were drawn into [capture].
   *
   * Reading them back means waiting for the GPU, which no real frame should
   * ever do — it is how a test on a device can ask whether anything was drawn
   * at all, and nothing else uses it.
   */
  fun draw(capture: ByteBuffer?): Boolean {
    if (!frames.beginFrame(swapChain, 0)) return false
    mirror?.takeIf { reflecting }?.let(::reflect)
    frames.render(view)
    capture?.let {
      frames.readPixels(0, 0, width, height, Texture.PixelBufferDescriptor(it, Texture.Format.RGBA, Texture.Type.UBYTE))
    }
    frames.endFrame()
    if (capture != null) engine.flushAndWait()
    return true
  }

  /** A buffer the right size for [draw] to copy a frame into. */
  fun pixelBuffer(): ByteBuffer = ByteBuffer.allocateDirect(width * height * PIXEL_BYTES).order(ByteOrder.nativeOrder())

  /**
   * One frame, drawn and read back off the GPU.
   *
   * The rows arrive from the top, which is how Filament hands them over, and
   * are kept as they come. They used to be turned over on the assumption that
   * they arrived the way OpenGL counts them, and every thumbnail came out
   * upside down; which way up a frame is turned out to be a question only a
   * device could answer, so the device suite asks it with nothing but
   * geometry (`PrintedNumbersDeviceTest`).
   */
  override fun capture(): Snapshot? {
    val buffer = pixelBuffer()
    if (!draw(buffer)) return null
    val bytes = ByteArray(buffer.capacity())
    buffer.rewind()
    buffer.get(bytes)
    return Snapshot(width, height, bytes)
  }

  /** The engine, for the two callers that have to reach it: textures and transforms. */
  fun engine(): Engine = engine

  /**
   * Filament's own record of the last few frames: hands [each] frame's id and
   * how long the GPU took over it, in nanoseconds.
   *
   * For the rendered harness and nothing else (`docs/build-setup.md`, "The
   * physics harness"). It reads a history Filament keeps anyway and changes
   * nothing about a frame. The window is short and is read whole, so a frame
   * arrives more than once, and a duration the GPU has not finished — or that
   * a driver with no timer queries can never give — arrives as Filament's
   * negative `PENDING` or `INVALID`; sorting that out is `RenderedFrames`'s
   * job, on a JVM, not this file's.
   */
  fun gpuFrames(each: (frameId: Int, gpuNanos: Long) -> Unit) {
    val history = frameHistory
    val count = frames.getFrameInfoHistory(history).coerceAtMost(history.size)
    for (index in 0 until count) {
      each(history[index].frameId, history[index].gpuFrameDuration)
    }
  }

  /** Where [gpuFrames] reads into, made on first use so a stage nobody times allocates none. */
  private val frameHistory: Array<FilamentFrameRenderer.FrameInfo> by lazy {
    Array(frames.maxFrameHistorySize) { FilamentFrameRenderer.FrameInfo() }
  }

  /**
   * Throws away everything a single roll put in the scene.
   *
   * The engine, the compiled material and the blank texture stay: they cost
   * real time to make and nothing about them is per-roll. Everything else is,
   * and a roll that left its dice behind would add eighty more to the next
   * one.
   */
  override fun clear() {
    entities.forEach {
      scene.removeEntity(it)
      engine.destroyEntity(it)
      EntityManager.get().destroy(it)
    }
    instances.forEach(engine::destroyMaterialInstance)
    buffers.forEach(engine::destroyVertexBuffer)
    indices.forEach(engine::destroyIndexBuffer)
    textures.forEach(engine::destroyTexture)
    entities.clear()
    instances.clear()
    buffers.clear()
    indices.clear()
    textures.clear()
    // The floor went with the rest, and the next one says again whether it
    // shows the dice. The picture's target is kept: it is a surface's, not a
    // roll's, and a felt table never draws into it.
    reflecting = false
  }

  /**
   * Gives back everything this surface owned, and nothing it merely borrowed.
   *
   * The engine, the material and the blank texture belong to [FilamentEngine]
   * and outlive any one surface — unless this stage made its own, in which case
   * it is the one that has to give it back, and does so last of all.
   */
  override fun close() {
    clear()
    mirror?.close(engine)
    mirror = null
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderer(frames)
    engine.destroyCameraComponent(cameraEntity)
    engine.destroyEntity(cameraEntity)
    EntityManager.get().destroy(cameraEntity)
    engine.destroySwapChain(swapChain)
    own?.close()
  }

  private fun addKeyLight() {
    val entity = EntityManager.get().create()
    // Scaled to a luminance of one, so [TrayLighting.KEY_LUX] is what lands
    // and the exposure worked out from it is right.
    val daylight = TrayLighting.unitLuminance(Colors.cct(TrayLighting.DAYLIGHT_KELVIN))
    val direction = TrayLighting.KEY_DIRECTION
    LightManager
      .Builder(LightManager.Type.DIRECTIONAL)
      .color(daylight[0], daylight[1], daylight[2])
      .intensity(TrayLighting.KEY_LUX.toFloat())
      .direction(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
      .castShadows(true)
      .shadowOptions(trayShadows())
      .build(engine, entity)
    scene.addEntity(entity)
    entities += entity
  }

  /**
   * The dice as the glass sees them, drawn before the frame that samples it.
   *
   * The camera under the floor is exposed as the real one is, whatever that
   * is now, because the floor adds this picture to its own light as it
   * stands ([DiceMaterial.GLASS_SOURCE]). The target is cleared to nothing
   * first — a transparent pixel is a pixel with no die in it, which the floor
   * reads as "change nothing" — and the renderer's own clearing is put back
   * for the frame itself, so a felt table and this one start the frame alike.
   */
  private fun reflect(mirror: Mirror) {
    mirror.camera.setExposure(camera.aperture, camera.shutterSpeed, camera.sensitivity)
    val kept = frames.clearOptions
    frames.clearOptions = CLEAR_TO_NOTHING
    frames.render(mirror.view)
    frames.clearOptions = kept
  }

  /** The [mirror], made now if this is the first glossy table this surface has shown. */
  private fun mirrorOf(): Mirror =
    mirror ?: Mirror(engine, scene, Reflection.sizeOf(width, height)).also { made ->
      mirror = made
      shot?.let { point(made.camera, Reflection.mirrored(it)) }
    }

  /**
   * Everything the picture of the dice in the glass is made with: a small
   * target to draw into, a camera under the floor and a view that sees only
   * the dice ([Reflection]).
   *
   * No shadows and no post-processing: the picture is a quarter of the size
   * and is stretched back over the floor at a few per cent, where a shadow
   * map of its own would cost a pass and show nothing, and tone mapping it
   * would map the light twice — the frame that samples it does that once,
   * for everything. Its colour keeps the frame's range (`RGBA16F`), and its
   * alpha says where a die is.
   */
  private class Mirror(
    engine: Engine,
    scene: Scene,
    size: Pair<Int, Int>,
  ) {
    val colour: Texture =
      Texture
        .Builder()
        .width(size.first)
        .height(size.second)
        .levels(1)
        .format(Texture.InternalFormat.RGBA16F)
        .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE)
        .build(engine)
    private val depth: Texture =
      Texture
        .Builder()
        .width(size.first)
        .height(size.second)
        .levels(1)
        .format(Texture.InternalFormat.DEPTH24)
        .usage(Texture.Usage.DEPTH_ATTACHMENT)
        .build(engine)
    private val target: RenderTarget =
      RenderTarget
        .Builder()
        .texture(RenderTarget.AttachmentPoint.COLOR, colour)
        .texture(RenderTarget.AttachmentPoint.DEPTH, depth)
        .build(engine)
    private val entity: Int = EntityManager.get().create()
    val camera: com.google.android.filament.Camera = engine.createCamera(entity)
    val view: View =
      engine.createView().apply {
        this.scene = scene
        this.camera = this@Mirror.camera
        viewport = Viewport(0, 0, size.first, size.second)
        renderTarget = target
        isPostProcessingEnabled = false
        setShadowingEnabled(false)
        setVisibleLayers(LAYERS, DICE)
      }

    fun close(engine: Engine) {
      engine.destroyView(view)
      engine.destroyCameraComponent(entity)
      engine.destroyEntity(entity)
      EntityManager.get().destroy(entity)
      engine.destroyRenderTarget(target)
      engine.destroyTexture(depth)
      engine.destroyTexture(colour)
    }
  }

  private fun instanceOf(
    parameters: DiceMaterial.Parameters,
    atlas: Texture?,
    glyphs: Texture?,
    reflected: Texture?,
  ): MaterialInstance =
    parts.materialFor(parameters).createInstance().apply {
      setParameter(
        "baseColor",
        parameters.colour.red.toFloat(),
        parameters.colour.green.toFloat(),
        parameters.colour.blue.toFloat(),
        parameters.colour.alpha.toFloat(),
      )
      setParameter("roughness", parameters.roughness.toFloat())
      setParameter("metallic", parameters.metallic.toFloat())
      setParameter("textured", if (atlas != null) 1.0f else 0.0f)
      setParameter("atlas", atlas ?: blank, sampler)
      setParameter("numbered", if (glyphs != null) 1.0f else 0.0f)
      setParameter(
        "inkColor",
        parameters.ink.red.toFloat(),
        parameters.ink.green.toFloat(),
        parameters.ink.blue.toFloat(),
        parameters.ink.alpha.toFloat(),
      )
      setParameter("glyphs", glyphs ?: blank, glyphSampler)
      setParameter("clearCoat", parameters.clearCoat.toFloat())
      setParameter("clearCoatRoughness", parameters.clearCoatRoughness.toFloat())
      // Only the resin material has these, and Filament refuses a parameter
      // a material does not declare (`DiceMaterial.RESIN_SOURCE`).
      parameters.resin?.let { resin ->
        setParameter("transmission", resin.transmission.toFloat())
        setParameter("scatter", resin.scatter.toFloat())
        setParameter("ior", resin.ior.toFloat())
        setParameter("thickness", resin.thicknessMm.toFloat())
        setParameter(
          "tint",
          resin.tint.red.toFloat(),
          resin.tint.green.toFloat(),
          resin.tint.blue.toFloat(),
        )
      }
      // And only the glass floor has these (`DiceMaterial.GLASS_SOURCE`).
      if (reflected != null) {
        setParameter("reflected", reflected, mirrorSampler)
        setParameter("reflectionStrength", requireNotNull(parameters.reflection).strength.toFloat())
      }
    }

  /**
   * A surface of the tray drawn from pictures ([DiceMaterial.TABLE_SOURCE]).
   *
   * A picture that is named and does not come — a package removed, a file
   * that will not decode — is the white pixel, and its map's flag is off: the
   * surface is then its colour, flat where the normal map would have been,
   * as rough as the look says. A table loses its grain, never its tray.
   */
  private fun tableInstanceOf(
    parameters: DiceMaterial.Parameters,
    maps: DiceMaterial.SurfaceMaps,
  ): MaterialInstance {
    // From the engine's own caches, like the material: a table's pictures
    // belong to its package and outlive any one surface ([FilamentEngine]).
    val albedo = maps.albedo?.let { parts.tablePicture(it, SurfaceMap.ALBEDO) }
    val normal = maps.normal?.let { parts.tablePicture(it, SurfaceMap.NORMAL) }?.texture
    val roughness = maps.roughness?.let { parts.tablePicture(it, SurfaceMap.ROUGHNESS) }
    val colour =
      TableTint.colourFor(
        parameters.colour,
        albedo?.mean,
        parameters.averaged,
        SurfaceLight.facingUp(parameters.roughness),
      )
    val shift = TableTint.roughnessShift(parameters.roughness, roughness?.level, parameters.averaged)
    return parts.materialFor(parameters).createInstance().apply {
      setParameter(
        "baseColor",
        colour.red.toFloat(),
        colour.green.toFloat(),
        colour.blue.toFloat(),
        colour.alpha.toFloat(),
      )
      setParameter("roughness", parameters.roughness.toFloat())
      setParameter("metallic", parameters.metallic.toFloat())
      setParameter("hasNormal", if (normal != null) 1.0f else 0.0f)
      setParameter("hasRoughness", if (roughness != null) 1.0f else 0.0f)
      setParameter("roughnessShift", shift.toFloat())
      setParameter("albedo", albedo?.texture ?: blank, parts.tableSampler)
      setParameter("normalMap", normal ?: blank, parts.tableSampler)
      setParameter("roughnessMap", roughness?.texture ?: blank, parts.tableSampler)
    }
  }

  /**
   * A die's printed numbers, uploaded.
   *
   * One channel, because a distance field is one measurement per pixel: a
   * d20's atlas is 320 by 256, which is 80 kB as `R8` and four times that as
   * anything else. It is made here rather than shared like the blank pixel
   * because it belongs to a die rather than to a device, and it is destroyed
   * with everything else the roll put in the scene.
   *
   * Uploading takes the buffer as it stands, so the caller may not reuse it —
   * which is why [DieNumbers] hands out a fresh field rather than a view onto
   * a cache.
   */
  private fun glyphsOf(numbers: NumberField): Texture {
    val texture =
      Texture
        .Builder()
        .width(numbers.width)
        .height(numbers.height)
        .levels(1)
        .format(Texture.InternalFormat.R8)
        .build(engine)
    val pixels = ByteBuffer.allocateDirect(numbers.pixels.size).order(ByteOrder.nativeOrder())
    pixels.put(numbers.pixels)
    pixels.flip()
    texture.setImage(engine, 0, Texture.PixelBufferDescriptor(pixels, Texture.Format.R, Texture.Type.UBYTE))
    textures += texture
    return texture
  }

  private fun verticesOf(mesh: GpuMesh): VertexBuffer {
    val vertices =
      VertexBuffer
        .Builder()
        .bufferCount(BUFFERS)
        .vertexCount(mesh.vertexCount)
        .attribute(
          VertexBuffer.VertexAttribute.POSITION,
          0,
          VertexBuffer.AttributeType.FLOAT3,
          0,
          GpuMesh.POSITION_SIZE * FLOAT_BYTES,
        ).attribute(
          VertexBuffer.VertexAttribute.TANGENTS,
          1,
          VertexBuffer.AttributeType.FLOAT4,
          0,
          GpuMesh.TANGENT_SIZE * FLOAT_BYTES,
        ).attribute(
          VertexBuffer.VertexAttribute.UV0,
          2,
          VertexBuffer.AttributeType.FLOAT2,
          0,
          GpuMesh.UV_SIZE * FLOAT_BYTES,
        ).build(engine)
    vertices.setBufferAt(engine, 0, floats(mesh.positions))
    vertices.setBufferAt(engine, 1, floats(mesh.tangents))
    vertices.setBufferAt(engine, 2, floats(mesh.uvs))
    return vertices
  }

  private fun indicesOf(mesh: GpuMesh): IndexBuffer {
    val triangles =
      IndexBuffer
        .Builder()
        .indexCount(mesh.indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.UINT)
        .build(engine)
    val buffer = ByteBuffer.allocateDirect(mesh.indices.size * INT_BYTES).order(ByteOrder.nativeOrder())
    buffer.asIntBuffer().put(mesh.indices)
    triangles.setBuffer(engine, buffer)
    return triangles
  }

  /** The box the renderer culls by: the mesh's own extent, not a guess. */
  private fun boundsOf(mesh: GpuMesh): com.google.android.filament.Box {
    val lowest = FloatArray(GpuMesh.POSITION_SIZE) { Float.MAX_VALUE }
    val highest = FloatArray(GpuMesh.POSITION_SIZE) { -Float.MAX_VALUE }
    for (corner in 0 until mesh.vertexCount) {
      repeat(GpuMesh.POSITION_SIZE) { axis ->
        val value = mesh.positions[corner * GpuMesh.POSITION_SIZE + axis]
        lowest[axis] = minOf(lowest[axis], value)
        highest[axis] = maxOf(highest[axis], value)
      }
    }
    return com.google.android.filament.Box(
      (lowest[0] + highest[0]) / 2,
      (lowest[1] + highest[1]) / 2,
      (lowest[2] + highest[2]) / 2,
      (highest[0] - lowest[0]) / 2,
      (highest[1] - lowest[1]) / 2,
      (highest[2] - lowest[2]) / 2,
    )
  }

  private fun floats(values: FloatArray): ByteBuffer {
    val buffer = ByteBuffer.allocateDirect(values.size * FLOAT_BYTES).order(ByteOrder.nativeOrder())
    buffer.asFloatBuffer().put(values)
    return buffer
  }

  companion object {
    /** Millimetres: the tray is 240 of them long, so these are generous. */
    const val NEAR_MM: Double = 5.0

    /** And the far plane, past anything a tray can hold. */
    const val FAR_MM: Double = 5_000.0

    /** Position, tangent frame and texture coordinate, each in its own buffer. */
    private const val BUFFERS = 3

    private const val FLOAT_BYTES = 4
    private const val INT_BYTES = 4

    /** Red, green, blue and alpha, a byte each. */
    const val PIXEL_BYTES: Int = 4

    /**
     * The layer the dice are on — Filament's default, so a renderable nobody
     * placed is a die — and the one the tray is on. Every view sees both but
     * the one under a glossy floor, which sees the dice alone.
     */
    private const val DICE = 0x1
    private const val TRAY = 0x2
    private const val LAYERS = DICE or TRAY

    /** What the picture of the dice in the glass starts each frame as: nothing at all. */
    private val CLEAR_TO_NOTHING =
      FilamentFrameRenderer.ClearOptions().apply {
        clear = true
        clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
      }

    /**
     * How the key light's shadow is drawn.
     *
     * The defaults are wrong here for one reason: **this scene is measured in
     * millimetres and the camera can see five metres**. A directional
     * shadow map covers the camera's whole frustum, so by default a
     * 1,024-pixel map was spread over 5,000 mm of nothing to hold a 240 mm
     * tray — a texel about 5 mm across, which on a 16 mm die is a third of a
     * face. What that produced on a phone was a wall shadow with a visibly
     * stepped edge, standing a few millimetres clear of the wall that cast it,
     * which is the classic look of a shadow biased away from its own caster.
     *
     * So: four times the map, and a shadow distance that stops just past the
     * tray instead of at the camera's far plane. That is under half a
     * millimetre per texel, and the biases come down with it — they are in
     * world units too, and a normal bias of Filament's default 1.0 is a whole
     * millimetre of push on a die 16 mm across.
     */
    private fun trayShadows(): LightManager.ShadowOptions =
      LightManager.ShadowOptions().apply {
        mapSize = SHADOW_MAP_PIXELS
        // Just past the far end of the largest tray a phone can be, plus the
        // height the camera stands off it. Everything further away is out of
        // shot anyway (`TrayCamera`).
        shadowFar = SHADOW_FAR_MM
        normalBias = SHADOW_NORMAL_BIAS_MM
        constantBias = SHADOW_CONSTANT_BIAS
      }

    /** Four times Filament's default, which a 240 mm tray earns back at once. */
    private const val SHADOW_MAP_PIXELS = 2048

    /** The tray's long side and the room above it, and nothing beyond. */
    private const val SHADOW_FAR_MM = 900.0f

    /**
     * How far along its own normal a surface is pushed before it is measured
     * against the shadow map, in millimetres.
     *
     * Small, because a millimetre is a real distance here: Filament's default
     * of 1.0 is what stood the wall's shadow clear of the wall. Not nought,
     * because a surface measured against a depth map at exactly its own depth
     * shadows itself in stripes.
     */
    private const val SHADOW_NORMAL_BIAS_MM = 0.15f

    /** And the constant part, in the depth buffer's own units rather than in mm. */
    private const val SHADOW_CONSTANT_BIAS = 0.0005f

    /**
     * Loads the native library. Safe to call more than once.
     *
     * Filament will not do anything before this, and the failure if it is
     * missed is a link error from inside a constructor, which says nothing
     * about what was actually forgotten.
     */
    fun ready() {
      Filament.init()
    }
  }
}
