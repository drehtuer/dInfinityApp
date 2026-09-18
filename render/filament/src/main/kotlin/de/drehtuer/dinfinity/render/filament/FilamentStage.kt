package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.SwapChainFlags
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import de.drehtuer.dinfinity.simulation.api.Vector3
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
 * Fifteen small methods rather than eleven larger ones is deliberate and is
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
  private val atlases: (String) -> Texture? = { null },
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

  /** The room the tray sits in. Built with the lights, given back with them. */
  private var ambient: IndirectLight? = null

  /** What that room looks like to a polished surface. Given back with it. */
  private var room: Texture? = null

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

  init {
    view.scene = scene
    view.camera = camera
    view.viewport = Viewport(0, 0, width, height)
    view.isPostProcessingEnabled = postProcessing
  }

  /** Points the camera where [shot] says, for this viewport. */
  override fun aim(shot: CameraShot) {
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
   * The key light, the fill and the ambient — the whole of the lighting.
   *
   * One directional light throws the shadows that tell a player a die is
   * sitting on the table rather than floating above it; a dimmer one from the
   * other side keeps the shadowed faces from going to black, where a number
   * cannot be read (`docs/physics-and-rendering.md`).
   *
   * The ambient is not a nicety. Two directional lights and nothing else means
   * every surface facing away from both is *exactly* black, and the surfaces
   * that face away from both are the inner walls: a player saw the lit top of
   * the wall, a shadow cast across the floor, and nothing in between casting
   * it. A tray is lit by a room, not by two lamps in a void.
   */
  override fun light() {
    addLight(intensity = KEY_LUX, direction = KEY_DIRECTION, shadows = true)
    addLight(intensity = FILL_LUX, direction = FILL_DIRECTION, shadows = false)
    val sky = environment(engine).also { room = it }
    scene.indirectLight = ambient(engine, sky).also { ambient = it }
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

  override fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
    casts: Boolean,
  ): Int {
    // Nought is Filament's word for "no entity", and a mesh with nothing in it
    // is not worth one.
    if (mesh.triangleCount == 0 || mesh.vertexCount == 0) return Stage.NOTHING
    val atlas = parameters.texturePath?.let(atlases)
    val vertices = verticesOf(mesh)
    val triangles = indicesOf(mesh)
    val instance = instanceOf(parameters, atlas, parameters.numbers?.let(::glyphsOf))
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
      .build(engine, entity)

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
   * The rows arrive the way a graphics driver counts them — from the bottom —
   * and are turned over by [Snapshot.fromBottomUp], which is plain Kotlin
   * because "is the picture upside down" is not a question worth needing a
   * phone for.
   */
  override fun capture(): Snapshot? {
    val buffer = pixelBuffer()
    if (!draw(buffer)) return null
    val bytes = ByteArray(buffer.capacity())
    buffer.rewind()
    buffer.get(bytes)
    return Snapshot.fromBottomUp(width, height, bytes)
  }

  /** The engine, for the two callers that have to reach it: textures and transforms. */
  fun engine(): Engine = engine

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
    ambient?.let(engine::destroyIndirectLight)
    ambient = null
    room?.let(engine::destroyTexture)
    room = null
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderer(frames)
    engine.destroyCameraComponent(cameraEntity)
    engine.destroyEntity(cameraEntity)
    EntityManager.get().destroy(cameraEntity)
    engine.destroySwapChain(swapChain)
    own?.close()
  }

  private fun addLight(
    intensity: Float,
    direction: Vector3,
    shadows: Boolean,
  ) {
    val entity = EntityManager.get().create()
    LightManager
      .Builder(LightManager.Type.DIRECTIONAL)
      .color(Colors.cct(DAYLIGHT_KELVIN)[0], Colors.cct(DAYLIGHT_KELVIN)[1], Colors.cct(DAYLIGHT_KELVIN)[2])
      .intensity(intensity)
      .direction(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
      .castShadows(shadows)
      .apply { if (shadows) shadowOptions(trayShadows()) }
      .build(engine, entity)
    scene.addEntity(entity)
    entities += entity
  }

  private fun instanceOf(
    parameters: DiceMaterial.Parameters,
    atlas: Texture?,
    glyphs: Texture?,
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
      setParameter("opacity", parameters.opacity.toFloat())
      setParameter("clearCoat", parameters.clearCoat.toFloat())
      setParameter("clearCoatRoughness", parameters.clearCoatRoughness.toFloat())
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

    /** A key light bright enough to read a die by, in lux. */
    private const val KEY_LUX = 80_000.0f

    /** And a fill that keeps the shadowed faces off black, where a number cannot be read. */
    private const val FILL_LUX = 25_000.0f

    /** Neutral daylight, so a table look's own colour is the colour you see. */
    private const val DAYLIGHT_KELVIN = 6_500.0f

    /**
     * Down, and from over the player's shoulder — the direction a lamp is in
     * when somebody rolls dice on a table in front of them.
     */
    private val KEY_DIRECTION = Vector3(-0.4, -0.3, -1.0)

    /**
     * How bright the room is: about a seventh of the key light.
     *
     * Enough that a wall facing away from both lamps reads as a wall rather
     * than as a hole, and low enough that the key still casts the shadow that
     * puts a die on the table. Tuned against the Pixel 10a, which is the only
     * place it can be judged (`docs/TODO.md`, Step 5.6).
     *
     * It is the *average* brightness, not the brightness in any one
     * direction. [RoomLight] says light comes down from a bright sky and up
     * off a dim floor, and Filament's intensity multiplies both, so this is
     * divided by the room's own average to keep the tray exactly as bright as
     * it was when the ambient was flat. What changed is where the light comes
     * from, which is the point.
     */
    private const val AMBIENT_LUX = 12_000.0f

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
     * tray instead of at the camera's far plane. That is about a twentieth of
     * a millimetre per texel, and the biases come down with it — they are in
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

    /** And back the other way, across the tray, to lift the shadowed faces. */
    private val FILL_DIRECTION = Vector3(0.6, 0.5, -0.7)

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

    private fun ambient(
      engine: Engine,
      environment: Texture,
    ): IndirectLight =
      IndirectLight
        .Builder()
        .irradiance(RoomLight.BANDS, RoomLight.irradiance())
        .reflections(environment)
        .intensity((AMBIENT_LUX / RoomLight.averageBrightness()).toFloat())
        .build(engine)

    /**
     * The room as a small cubemap, which is what a polished surface reflects.
     *
     * One level, six faces, uploaded in a single call — which is what a
     * cubemap is: an image six deep. [RoomLight.LEVELS] says why there is only
     * one of them.
     */
    private fun environment(engine: Engine): Texture {
      val texture =
        Texture
          .Builder()
          .width(RoomLight.SIZE)
          .height(RoomLight.SIZE)
          .depth(RoomLight.FACES)
          .levels(RoomLight.LEVELS)
          .format(Texture.InternalFormat.RGBA8)
          .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
          .build(engine)
      for (level in 0 until RoomLight.LEVELS) {
        val side = (RoomLight.SIZE shr level).coerceAtLeast(1)
        val pixels = RoomLight.level(level = level)
        val buffer = ByteBuffer.allocateDirect(pixels.size).order(ByteOrder.nativeOrder())
        buffer.put(pixels)
        buffer.rewind()
        // All six faces in one go: a cubemap is an image six deep, and one
        // face with a depth of one is a buffer Filament refuses.
        texture.setImage(
          engine,
          level,
          0,
          0,
          0,
          side,
          side,
          RoomLight.FACES,
          Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE),
        )
      }
      return texture
    }
  }
}
