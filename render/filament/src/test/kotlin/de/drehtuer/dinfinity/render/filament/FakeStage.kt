package de.drehtuer.dinfinity.render.filament

/**
 * A stage that draws nothing and remembers everything.
 *
 * The renderer's own decisions — which meshes a throw needs, how big each die
 * is, which numbers its material takes, when the camera stops framing the tray
 * and starts framing the dice — are all made against this interface, so this is
 * where they can be asked about (`docs/architecture.md`, decision 40).
 */
class FakeStage(
  override val width: Int = 320,
  override val height: Int = 640,
) : Stage {
  /** Every mesh added since the last [clear], with what it is drawn with. */
  val added = mutableListOf<Pair<GpuMesh, DiceMaterial.Parameters>>()

  /** Where each entity was last put. */
  val placed = mutableMapOf<Int, FloatArray>()

  /** Every shot the camera has been pointed at, in order. */
  val shots = mutableListOf<CameraShot>()

  /** How many frames were asked for. */
  var frames: Int = 0
    private set

  /** How many times the scene has been thrown away. */
  var clears: Int = 0
    private set

  /** True once the stage has been given up. */
  var closed: Boolean = false
    private set

  /** Whether the lights are in the scene. */
  var lit: Boolean = false
    private set

  /** A mesh to hand back [Stage.NOTHING] for, standing in for one with nothing in it. */
  var refuse: GpuMesh? = null

  override fun light() {
    lit = true
  }

  override fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
  ): Int {
    if (mesh == refuse) return Stage.NOTHING
    added += mesh to parameters
    return added.size
  }

  override fun place(
    entity: Int,
    matrix: FloatArray,
  ) {
    placed[entity] = matrix
  }

  override fun aim(shot: CameraShot) {
    shots += shot
  }

  override fun draw(): Boolean {
    frames++
    return true
  }

  override fun clear() {
    clears++
    added.clear()
    placed.clear()
    lit = false
  }

  override fun close() {
    closed = true
  }
}
