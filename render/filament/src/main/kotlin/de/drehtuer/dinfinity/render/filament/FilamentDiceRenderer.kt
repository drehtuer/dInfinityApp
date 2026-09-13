package de.drehtuer.dinfinity.render.filament

import com.google.android.filament.Texture
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.render.headless.Renderer
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
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
 * @param atlases where a die's artwork comes from, by the path its set names.
 *   Nothing supplies one yet — textures are decoded where a package is
 *   installed, which is 4.4 — so dice are drawn in their own colours until it
 *   does, and the seam is here so that arriving is a change of one argument.
 */
class FilamentDiceRenderer(
  private val stage: FilamentStage,
  private val atlases: (String) -> Texture? = { null },
) : Renderer {
  private var dice: List<Int> = emptyList()
  private var geometry: TableGeometry? = null

  /**
   * The biggest die in the throw, which is what the settled camera frames by.
   *
   * Framing die *centres* would centre the group and clip whichever die is at
   * the edge of it — the one a player is most likely to be squinting at.
   */
  private var reachMm: Double = 0.0

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
    reachMm = spec.dice.maxOfOrNull { radiusOf(it.die, spec.dieScale) } ?: 0.0
    stage.aim(TrayCamera.framingTheTray(geometry, aspectRatio()))
  }

  override fun show(frame: RenderFrame) {
    place(frame)
    stage.draw()
  }

  override fun settled(frame: RenderFrame) {
    place(frame)
    // The dice have stopped, so the only thing worth looking at is where they
    // stopped. The move itself is eased by whoever is driving the frames; this
    // is where it ends up (`TrayCamera`).
    geometry?.let { table ->
      stage.aim(
        TrayCamera.framingTheDice(
          positions = frame.current.map { it.position },
          dieRadiusMm = reachMm,
          geometry = table,
          aspectRatio = aspectRatio(),
        ),
      )
    }
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
      dice.getOrNull(body.index)?.takeIf { it != NOTHING }?.let { entity ->
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
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Floor)), floor, look.floorTexturePath?.let(atlases))
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Wall)), wall, look.wallTexturePath?.let(atlases))
    // The rim is the wall seen end-on, so it takes the wall's colour and none
    // of its texture: six millimetres is not where anybody looks.
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Rim)), wall)
  }

  private fun addDie(
    die: de.drehtuer.dinfinity.core.model.Die,
    scale: Double,
  ): Int =
    stage.add(
      mesh = GpuMesh.of(DieMesh.of(die.shape).faces, scale = radiusOf(die, scale)),
      parameters = DiceMaterial.dieOf(die.material, die.texturePath),
      atlas = die.texturePath?.let(atlases),
    )

  /** How far a die of this shape reaches from its middle, at the throw's scale. */
  private fun radiusOf(
    die: de.drehtuer.dinfinity.core.model.Die,
    scale: Double,
  ): Double = ShapeGeometry.boundingRadiusPerSize(die.shape) * die.material.sizeMm * scale

  private fun aspectRatio(): Double = stage.width.toDouble() / stage.height

  private companion object {
    /** Filament's word for "no entity". */
    const val NOTHING = 0
  }
}
