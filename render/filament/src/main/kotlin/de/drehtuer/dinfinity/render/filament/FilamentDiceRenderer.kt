package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

/**
 * The renderer that draws (`docs/physics-and-rendering.md`, "Rendering").
 *
 * A **passive observer**, and the interface it implements is that sentence
 * made enforceable: it is shown a scene and then shown where the bodies are,
 * and it returns nothing anywhere. Turning it off cannot change how a roll is
 * produced, which is what makes power-saving mode honest rather than a second
 * implementation to keep in step (`docs/architecture.md`, goal 1).
 *
 * It is also nearly all glue. Where the camera stands, what shape a die is,
 * how its mesh packs, which numbers its material takes and where it is between
 * two simulation steps are all decided elsewhere and tested on a JVM; what is
 * here is the order those are put together in.
 *
 * Every line of it is a decision and none of it is a GPU, which is why it
 * takes a [Stage] rather than a Filament one: the same seam, and the same
 * reason, as the physics bridge's `PhysicsWorld`.
 */
class FilamentDiceRenderer(
  private val stage: Stage,
) : Renderer {
  private var dice: List<Int> = emptyList()
  private var geometry: TableGeometry? = null

  override fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  ) {
    stage.clear()
    this.geometry = geometry
    stage.light()
    addTray(geometry, look)
    dice = spec.dice.map { instance -> addDie(instance.die, spec.dieScale) }
    stage.aim(TrayCamera.framingTheTray(geometry, aspectRatio()))
  }

  override fun show(frame: RenderFrame) {
    place(frame)
    stage.draw()
  }

  override fun settled(frame: RenderFrame) {
    // The camera does not move. It framed the whole tray when the roll began
    // and it frames the whole tray now: a player watching dice land wants to
    // see where they landed *on the table*, and a camera that closes in on
    // them takes the table away and leaves no way to tell four dice from two
    // (`docs/TODO.md`, Step 4.1 — panning and pinching are the way to look
    // closer, and they are the player's to do).
    place(frame)
    stage.draw()
  }

  override fun end() {
    stage.clear()
    dice = emptyList()
    geometry = null
  }

  private fun place(frame: RenderFrame) {
    frame.blended().forEach { body ->
      // Nought is Filament's word for "no entity", which is what a die with
      // nothing to draw was given.
      dice.getOrNull(body.index)?.takeIf { it != Stage.NOTHING }?.let { entity ->
        stage.place(entity, Transform.of(body.position, body.orientation))
      }
    }
  }

  private fun addTray(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    val tray = TrayMesh.of(geometry, look)
    val floor = DiceMaterial.floorOf(look)
    val wall = DiceMaterial.wallOf(look)
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Floor)), floor)
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Wall)), wall)
    // The rim is the wall seen end-on, so it takes the wall's colour and none
    // of its texture: six millimetres is not where anybody looks.
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Rim)), wall.copy(texturePath = null))
  }

  private fun addDie(
    die: Die,
    scale: Double,
  ): Int =
    stage.add(
      mesh = GpuMesh.of(DieMesh.of(die.shape).faces, scale = radiusOf(die, scale)),
      parameters = DiceMaterial.dieOf(die.material, die.texturePath),
    )

  /** How far a die of this shape reaches from its middle, at the throw's scale. */
  private fun radiusOf(
    die: Die,
    scale: Double,
  ): Double = die.material.boundingRadiusMm * scale

  private fun aspectRatio(): Double = stage.width.toDouble() / stage.height
}
