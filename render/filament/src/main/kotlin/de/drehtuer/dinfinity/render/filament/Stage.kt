package de.drehtuer.dinfinity.render.filament

/**
 * A scene that can be drawn, and nothing that decides what is in it.
 *
 * The same line `simulation/jolt` draws between deciding a roll and running it
 * (`docs/architecture.md`, decision 40), for the same reason. Which meshes a
 * throw needs, how big each die is, which numbers its material takes, when the
 * camera stops framing the tray and starts framing the dice — all of that is
 * judgement, and judgement belongs where a test can reach it. What is on the
 * far side of this interface is buffers, handles and a draw call, and needs a
 * GPU to say anything about at all.
 *
 * [FilamentStage] is the one implementation that ships; a test drives the same
 * renderer against a stage it can ask questions of.
 */
interface Stage : AutoCloseable {
  /** The viewport, in pixels. The camera frames for its shape. */
  val width: Int

  /** The same. */
  val height: Int

  /** Puts the key light and the fill in the scene. */
  fun light()

  /**
   * Adds one mesh, drawn with [parameters], and hands back the entity it was
   * given — or [NOTHING] when there was nothing to draw.
   *
   * The atlas a surface samples is named in [parameters] rather than passed
   * as a texture, so that deciding *which* artwork a die wears stays on this
   * side of the line and loading it stays on the other.
   */
  fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
  ): Int

  /** Moves an entity already in the scene. [matrix] is what [Transform] built. */
  fun place(
    entity: Int,
    matrix: FloatArray,
  )

  /** Points the camera where [shot] says. */
  fun aim(shot: CameraShot)

  /** Draws one frame. False when the renderer asked to skip it. */
  fun draw(): Boolean

  /** Throws away everything one roll put in the scene, and nothing else. */
  fun clear()

  /**
   * Gives up the engine, the surface and everything on it.
   *
   * Part of the interface rather than of the one implementation that needs it,
   * because whoever holds a stage is the one who has to let it go: a surface
   * is withdrawn while a roll is still running, and the code that notices has
   * no business knowing whether a GPU was involved.
   */
  override fun close()

  companion object {
    /** What [add] returns for a mesh with nothing in it. */
    const val NOTHING: Int = 0
  }
}
