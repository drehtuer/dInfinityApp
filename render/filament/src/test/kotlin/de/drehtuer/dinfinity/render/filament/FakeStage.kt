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

  /**
   * Whether [draw] declines the frames it is offered, as Filament's renderer
   * does when it decides one is not worth beginning. A roll draws again a
   * sixtieth of a second later; a picture that is not moving has to notice.
   */
  var refuseFrames: Boolean = false

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

  /** Entities taken out of the scene, in the order they left it. */
  val taken: MutableList<Int> = mutableListOf()

  override fun take(entity: Int) {
    taken += entity
    placed -= entity
  }

  override fun aim(shot: CameraShot) {
    shots += shot
  }

  override fun draw(): Boolean {
    if (refuseFrames) return false
    frames++
    return true
  }

  /**
   * What [capture] hands back once a frame has been drawn, or null for a stage
   * that draws to a screen perfectly well and cannot be read back — which is
   * what some drivers are, and what a thumbnail has to survive.
   */
  var picture: Snapshot? = null

  /**
   * What was in the scene the moment a picture was taken of it.
   *
   * [clear] empties [added], and whoever asked for a picture is usually done
   * with the scene straight afterwards — so a test of what was *drawn* has to
   * read it at the moment it was drawn rather than at the end.
   */
  var whenCaptured: List<Pair<GpuMesh, DiceMaterial.Parameters>> = emptyList()
    private set

  /** And whether the lights were in it. */
  var litWhenCaptured: Boolean = false
    private set

  override fun capture(): Snapshot? {
    whenCaptured = added.toList()
    litWhenCaptured = lit
    if (!draw()) return null
    return picture
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
