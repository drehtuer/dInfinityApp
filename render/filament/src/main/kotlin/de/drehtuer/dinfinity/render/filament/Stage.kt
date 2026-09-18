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
   *
   * @param casts whether this surface throws a shadow of its own. True for a
   *   die, because a shadow under a die is what says it is on the table
   *   rather than over it. **False for the tray**, whose rim and wall cast a
   *   band across their own felt that reads as a smear rather than as a rim
   *   (`docs/physics-and-rendering.md`, "What is drawn over the table"). The
   *   two are one decision per renderable rather than one for the scene,
   *   because the promise the app makes is about the dice and not about the
   *   furniture they land on.
   */
  fun add(
    mesh: GpuMesh,
    parameters: DiceMaterial.Parameters,
    casts: Boolean = true,
  ): Int

  /** Moves an entity already in the scene. [matrix] is what [Transform] built. */
  fun place(
    entity: Int,
    matrix: FloatArray,
  )

  /**
   * Takes an entity out of the scene. It is not drawn again.
   *
   * What a counted die gets: it has been read and lifted off the table, and
   * the floor it stood on is free for the dice still to be thrown — so a later
   * die may land exactly there, and leaving this one drawn would be two dice
   * in one place (`docs/physics-and-rendering.md`).
   *
   * The entity is not destroyed, because the mesh and its material are the
   * expensive half and the same die may be drawn again by the next roll.
   */
  fun take(entity: Int)

  /** Points the camera where [shot] says. */
  fun aim(shot: CameraShot)

  /** Draws one frame. False when the renderer asked to skip it. */
  fun draw(): Boolean

  /**
   * Draws one frame and hands back its pixels, or null where this stage cannot
   * give them.
   *
   * Reading a frame back means waiting for the GPU, which nothing watching a
   * roll may ever do — it is how a still picture drawn off screen becomes
   * something a screen that is not the tray can show ([TrayThumbnails],
   * `docs/tables.md`, "Thumbnails"), and how a device test can ask whether
   * anything was drawn at all.
   *
   * Null rather than an exception for a stage that has none: a driver that
   * renders correctly and returns an empty buffer is a thing that exists, and
   * what a caller does about it is show something else.
   */
  fun capture(): Snapshot? = null

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
