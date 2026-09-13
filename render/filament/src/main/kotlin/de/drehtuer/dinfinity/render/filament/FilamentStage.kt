package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
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
import com.google.android.filament.filamat.MaterialBuilder
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
 */
@Suppress("TooManyFunctions")
class FilamentStage(
  val width: Int,
  val height: Int,
  surface: Any? = null,
  postProcessing: Boolean = true,
) : AutoCloseable {
  private val engine: Engine = Engine.create()
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

  private val material: Material = compileMaterial(engine)

  /**
   * A single white pixel, for every surface that has no atlas.
   *
   * Filament will not draw a material whose sampler is unbound, and the
   * material has one because most dice do carry artwork. A die that does not
   * is drawn through this, which multiplies its colour by one.
   */
  private val blank: Texture = whitePixel(engine)

  private val sampler =
    TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT)

  private val instances = mutableListOf<MaterialInstance>()
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
  fun aim(shot: CameraShot) {
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
   * The key light and the fill, which is the whole of the lighting.
   *
   * One directional light throws the shadows that tell a player a die is
   * sitting on the table rather than floating above it; a dimmer one from the
   * other side keeps the shadowed faces from going to black, where a number
   * cannot be read (`docs/physics-and-rendering.md`).
   */
  fun light() {
    addLight(intensity = KEY_LUX, direction = KEY_DIRECTION, shadows = true)
    addLight(intensity = FILL_LUX, direction = FILL_DIRECTION, shadows = false)
  }

  /** Puts one mesh in the scene and hands back the entity it was given. */
  fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
    atlas: Texture? = null,
  ): Int {
    // Nought is Filament's word for "no entity", and a mesh with nothing in it
    // is not worth one.
    if (mesh.triangleCount == 0 || mesh.vertexCount == 0) return 0
    val vertices = verticesOf(mesh)
    val triangles = indicesOf(mesh)
    val instance = instanceOf(parameters, atlas)
    val entity = EntityManager.get().create()

    RenderableManager
      .Builder(1)
      .boundingBox(boundsOf(mesh))
      .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertices, triangles)
      .material(0, instance)
      .castShadows(true)
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
  fun place(
    entity: Int,
    matrix: FloatArray,
  ) {
    val transforms = engine.transformManager
    transforms.setTransform(transforms.getInstance(entity), matrix)
  }

  /**
   * Draws one frame. False when Filament asked to skip it.
   *
   * @param capture where to copy the pixels that were drawn, or null. Reading
   *   them back means waiting for the GPU, which no real frame should ever do
   *   — it is how a test on a device can ask whether anything was drawn at
   *   all, and nothing else uses it.
   */
  fun draw(capture: ByteBuffer? = null): Boolean {
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
  fun clear() {
    entities.forEach {
      scene.removeEntity(it)
      engine.destroyEntity(it)
      EntityManager.get().destroy(it)
    }
    instances.forEach(engine::destroyMaterialInstance)
    buffers.forEach(engine::destroyVertexBuffer)
    indices.forEach(engine::destroyIndexBuffer)
    entities.clear()
    instances.clear()
    buffers.clear()
    indices.clear()
  }

  override fun close() {
    clear()
    engine.destroyTexture(blank)
    engine.destroyMaterial(material)
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderer(frames)
    engine.destroyCameraComponent(cameraEntity)
    engine.destroyEntity(cameraEntity)
    EntityManager.get().destroy(cameraEntity)
    engine.destroySwapChain(swapChain)
    engine.destroy()
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
      .build(engine, entity)
    scene.addEntity(entity)
    entities += entity
  }

  private fun instanceOf(
    parameters: DiceMaterial.Parameters,
    atlas: Texture?,
  ): MaterialInstance =
    material.createInstance().apply {
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

    /** Every channel of the blank texture, which multiplies a colour by one. */
    private const val OPAQUE_WHITE = 0xFF.toByte()

    /**
     * Down, and from over the player's shoulder — the direction a lamp is in
     * when somebody rolls dice on a table in front of them.
     */
    private val KEY_DIRECTION = Vector3(-0.4, -0.3, -1.0)

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

    private fun compileMaterial(engine: Engine): Material {
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

    private fun whitePixel(engine: Engine): Texture {
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
