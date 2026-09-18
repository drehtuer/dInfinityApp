package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
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
  /**
   * How far the camera leans over the table — the player's **Table view**
   * setting (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
   *
   * Given when this is made and never changed, because it is read when the
   * roll screen opens: a camera that moved under a roll in progress is not a
   * setting taking effect (`docs/architecture.md`, decision 16). A rotation
   * makes a new renderer with the same answer, which is why [TrayRenderer]
   * carries it rather than this.
   *
   * [TableView.Angled] when nobody says, which is the shot this drew before
   * the lean was a setting. What a player who has chosen nothing gets is
   * `AppSettings.tableView`, and that is straight down.
   */
  private val tableView: TableView = TableView.Angled,
) : Renderer {
  /** The lean in degrees, worked out once rather than per frame. */
  private val tilt: Double = TrayCamera.tiltDegreesOf(tableView)
  private var dice: List<Int> = emptyList()

  /** Which dice are in the scene right now, so one leaves it exactly once. */
  private val shown = mutableSetOf<Int>()
  private var geometry: TableGeometry? = null

  /** Each die's printed numbers, built once and kept for as long as this renderer is. */
  private val printed = PrintedDice()

  /**
   * The table, lit and framed, with nothing on it.
   *
   * What a player sees before they have thrown anything and after the result
   * has been put away: a table waiting, rather than the black rectangle a
   * scene that is never built leaves behind (`docs/TODO.md`, Step 4.1).
   *
   * Nothing here is a frame — the caller draws when it is ready to, because an
   * empty table does not move and is worth exactly one draw.
   */
  fun table(
    geometry: TableGeometry,
    look: TableLook,
    view: TrayView = TrayView.Whole,
  ) {
    stage.clear()
    this.geometry = geometry
    dice = emptyList()
    stage.light()
    addTray(geometry, look)
    stage.aim(TrayCamera.framingTheTray(geometry, aspectRatio(), view, tilt))
  }

  /**
   * The player is looking somewhere else, or closer.
   *
   * Only the camera moves. Nothing in the scene is touched, and nothing about
   * the roll is: this is where a player is standing, not what the dice did.
   * Where the view is allowed to go is [TrayView]'s to say; this is told, and
   * does not remember — [TrayRenderer] is the one that does.
   */
  fun look(view: TrayView) {
    val framing = geometry ?: return
    stage.aim(TrayCamera.framingTheTray(framing, aspectRatio(), view, tilt))
  }

  /**
   * A throw, on the whole table.
   *
   * The view is not carried over from before it. The dice can land anywhere in
   * the tray, and a camera left closed in on one corner would hide most of what
   * was just rolled (`docs/physics-and-rendering.md`).
   *
   * A throw an explosion or a reroll added arrives in a tray that already has
   * dice in it, and those dice are put back exactly where the simulation left
   * them ([ThrowSpec.among]). They are placed once and never again: they have
   * stopped, their faces are read, and nothing in this throw can reach them —
   * there is no body for them in its world. What the player sees is the die
   * they set off landing among them, which is what happened.
   */
  override fun begin(
    spec: ThrowSpec,
    geometry: TableGeometry,
    look: TableLook,
  ) {
    table(geometry, look, TrayView.Whole)
    spec.among.forEach { resting ->
      val entity = addDie(resting.die, resting.setId, spec.dieScale)
      if (entity != Stage.NOTHING) {
        stage.place(entity, Transform.of(resting.at.position, resting.at.orientation))
      }
    }
    dice = spec.dice.map { instance -> addDie(instance.die, instance.setId, spec.dieScale) }
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
    shown.clear()
    geometry = null
  }

  private fun place(frame: RenderFrame) {
    val bodies = frame.blended()
    bodies.forEach { body ->
      // Nought is Filament's word for "no entity", which is what a die with
      // nothing to draw was given.
      dice.getOrNull(body.index)?.takeIf { it != Stage.NOTHING }?.let { entity ->
        stage.place(entity, Transform.of(body.position, body.orientation))
        shown += body.index
      }
    }

    // A die the frame has stopped mentioning has been counted and lifted off
    // the table. Leaving it where it was would draw it under whatever lands
    // there next, so it comes out of the scene — once, rather than every frame
    // for the rest of the roll.
    val here = bodies.mapTo(mutableSetOf()) { it.index }
    val left = shown - here
    left.forEach { index ->
      dice.getOrNull(index)?.takeIf { it != Stage.NOTHING }?.let(stage::take)
    }
    shown -= left
  }

  private fun addTray(
    geometry: TableGeometry,
    look: TableLook,
  ) {
    val tray = TrayMesh.of(geometry, look)
    val floor = DiceMaterial.floorOf(look)
    val wall = DiceMaterial.wallOf(look)
    // **No part of the tray casts a shadow.** The one shadow-casting light
    // stands off to one side, so the wall and the six millimetres of rim on
    // top of it threw a band across their own felt — a hard-edged stripe
    // down the inside of the table that reads as a smear rather than as a
    // rim, and the thing the device session asked to be rid of. The dice go
    // on casting theirs, which is the promise the README makes and the only
    // shadow that says anything (`docs/physics-and-rendering.md`).
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Floor)), floor, casts = false)
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Wall)), wall, casts = false)
    // The rim is the wall seen end-on, so it takes the wall's colour and none
    // of its texture: six millimetres is not where anybody looks.
    stage.add(GpuMesh.of(tray.partsOf(TrayPart.Rim)), wall.copy(texturePath = null), casts = false)
  }

  /**
   * One die, drawn with its package's artwork and its own printed labels.
   *
   * [setId] is what makes the artwork findable. A die's `texture` is a path
   * relative to *its own set's folder*, and two sets may both ship
   * `textures/d20.png`, so the path alone names nothing: what the stage is
   * given is an [AtlasKey], and what fills it is on the far side of [Stage]
   * (`docs/dice-sets.md`, "Textures").
   *
   * The labels are built whatever the die wears. An atlas may leave a face's
   * cell clear and that face is then printed, which is a decision the material
   * makes per pixel rather than one this side can make at all ([PrintedDice]).
   */
  private fun addDie(
    die: Die,
    setId: String,
    scale: Double,
  ): Int {
    val mesh = DieMesh.of(die.shape)
    return stage.add(
      // How far this shape reaches from its middle, at the throw's scale.
      mesh = GpuMesh.of(mesh.faces, scale = die.material.boundingRadiusMm * scale),
      parameters =
        DiceMaterial.dieOf(
          material = die.material,
          texturePath = die.texturePath?.let { AtlasKey.of(setId, it) },
          numbers = printed.of(die, mesh),
        ),
    )
  }

  private fun aspectRatio(): Double = stage.width.toDouble() / stage.height
}
