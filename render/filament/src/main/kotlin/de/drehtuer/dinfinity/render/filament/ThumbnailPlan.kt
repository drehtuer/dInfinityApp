package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.roundToInt

/**
 * The die a thumbnail stands on the table, and which package it came from.
 *
 * The set id is not decoration: a die's `texture` is a path inside *its own*
 * set's folder, so a die drawn without one wears nobody's picture or somebody
 * else's (`docs/architecture.md`, decision 58).
 */
data class ThumbnailDie(
  val die: Die,
  val setId: String,
) {
  companion object {
    /** What the picker's preview says it is: "roll a d20 here". */
    const val FACES: Int = 20

    /**
     * The d20 a thumbnail shows, out of [sets], or null when nothing installed
     * has one.
     *
     * The bundled package first, because it is the one every install has and
     * the one the picker's preview was drawn against; then any other package's
     * twenty-sided die, so that a phone whose bundled package has stopped
     * validating still gets a picture. Nothing else stands in — a d6 is not
     * what the row says, and a table with nothing on it is a truer picture of
     * the look than a die the caption does not describe.
     */
    fun from(sets: List<DiceSet>): ThumbnailDie? {
      val ordered = sets.sortedBy { if (it.id == DiceSet.BUILTIN_ID) 0 else 1 }
      return ordered.firstNotNullOfOrNull { set ->
        set.dice.firstOrNull { it.shape.faceCount == FACES }?.let { ThumbnailDie(it, set.id) }
      }
    }
  }
}

/**
 * What a picture of a table look is a picture of
 * (`docs/tables.md`, "Thumbnails"; `design/dInfinity.dc.html`, option `1u`).
 *
 * Every decision a thumbnail needs is here, in plain Kotlin, for the reason
 * every decision about a roll is: how big it is, what tray it is a tray of,
 * where the camera stands, which face of the die is up and how high that
 * leaves the die sitting can all be wrong, and none of them needs a GPU to be
 * wrong in. What is left on the far side of [Stage] is a swap chain, a draw
 * call and a buffer of pixels ([TrayThumbnails], `docs/architecture.md`,
 * decisions 40 and 47).
 *
 * **The tray is built for the thumbnail rather than for the phone.** A table's
 * long side is always 240 mm and its short side follows the shape of the
 * screen it is drawn on (`docs/tables.md`, "Geometry"), and a thumbnail is a
 * very small screen: it gets a table of its own proportions, which is the same
 * rule applied rather than an exception to it. Nothing about a *roll* is
 * decided here — this tray never holds a physics world.
 *
 * **The camera stands as close as a player may ever stand**, in the far corner
 * of the tray. Framing the whole 240 mm of it would put a 16 mm die across
 * about a twentieth of the picture, which at this size is four pixels of grey:
 * true, and a picture of nothing. At [TrayView.CLOSEST] the die is a quarter
 * of the frame, two walls and the rounded corner between them are in shot, and
 * a floor texture is at a size somebody can see repeat. The view is the same
 * [TrayView] a pinch produces, framed by the same arithmetic, so the camera
 * cannot be asked for somewhere the tray is not.
 *
 * @param widthPx how wide the picture is, in pixels of the screen it will be
 *   drawn into.
 * @param heightPx and how tall. Taller than wide, because the tray's long side
 *   runs up the screen.
 */
data class ThumbnailPlan(
  val widthPx: Int,
  val heightPx: Int,
) {
  init {
    require(widthPx > 0 && heightPx > 0) { "a thumbnail of $widthPx by $heightPx has no pixels" }
  }

  /** How wide the picture is for its height — what the camera frames for. */
  val aspectRatio: Double get() = widthPx.toDouble() / heightPx

  /** The tray this thumbnail is a picture of, shaped like the thumbnail. */
  val geometry: TableGeometry get() = TableGeometry.forAspect(aspectRatio)

  /**
   * Where the player is standing: the far corner, as close as the camera goes.
   *
   * Asked for by name rather than worked out here — [TrayView.inTheCorner] is
   * the one description of "the frame flush inside the corner" and it is
   * tested there. Two descriptions of it would be two chances to disagree.
   *
   * It used to be asked for as "further than there is tray" and brought back
   * by [TrayView.within], which was the same picture only as long as the pan
   * limit and the corner framing were the same number. They are not any more:
   * the limit now lets a player stand the middle of the screen *on* the
   * corner, which is right for a hand looking for a die and wrong for a
   * thumbnail, where it would spend half the picture on the rim and the void
   * past it. A thumbnail is a picture of a table.
   */
  val view: TrayView get() = TrayView.inTheCorner(geometry)

  /**
   * The throw this picture is of — which is not a throw at all.
   *
   * A [ThrowSpec] because that is what [FilamentDiceRenderer] builds a scene
   * from, and building the scene a second way is how a thumbnail would end up
   * being a picture of a tray the roll screen does not draw. Nothing runs it:
   * no world is opened, no step is taken, and the die is put where [standing]
   * says rather than where a solver left it. **The physics result is still the
   * only thing that decides a die's value** — this decides nothing, because
   * nobody reads a face off a thumbnail (`docs/architecture.md`, goal 1).
   */
  fun spec(
    look: TableLook,
    die: ThumbnailDie,
  ): ThrowSpec =
    ThrowSpec(
      dice =
        listOf(
          DieInstance(index = 0, groupId = 0, setId = die.setId, requestedSetId = die.setId, die = die.die),
        ),
      geometry = geometry,
      table = look,
      seed = SEED,
    )

  /**
   * The die, sitting still in the middle of what the camera can see.
   *
   * A [RenderFrame] with both ends the same, which is what a renderer is
   * handed for dice that are not moving. It is the middle of the *view* rather
   * than of the tray, because the camera is in a corner of it.
   */
  fun standing(die: ThumbnailDie): RenderFrame {
    val turn = bestFaceUp(die.die)
    val held = view
    return RenderFrame.still(
      listOf(
        BodyTransform(
          index = 0,
          position = Vector3(held.panAlongMm, held.panAcrossMm, restingHeightMm(die.die, turn)),
          orientation = turn,
        ),
      ),
    )
  }

  companion object {
    /**
     * The longest a thumbnail is drawn, in pixels.
     *
     * Not a guess about screens: a picture this size is 256 kB read back off
     * the GPU and held as a bitmap, and [ThumbnailCache] keeps a handful of
     * them. A phone dense enough to want more than this gets a picture scaled
     * up by a few per cent, which nobody can see at the size a row is, and
     * everybody's memory is bounded by a number in this file rather than by
     * how many packages they installed.
     */
    const val MAX_SIDE: Int = 256

    /** A seed for a throw that is never thrown, so that it is the same one every time. */
    const val SEED: Long = 0L

    /**
     * A plan for a box this big on the screen, brought under [MAX_SIDE].
     *
     * The shape is kept: a picture scaled to a different aspect ratio than the
     * tray was framed for is a tray that does not touch the edges, and the
     * whole point of the tray's own rule is that it does.
     */
    fun of(
      widthPx: Int,
      heightPx: Int,
    ): ThumbnailPlan {
      require(widthPx > 0 && heightPx > 0) { "a thumbnail of $widthPx by $heightPx has no pixels" }
      val longest = maxOf(widthPx, heightPx)
      if (longest <= MAX_SIDE) return ThumbnailPlan(widthPx, heightPx)
      val shrink = MAX_SIDE.toDouble() / longest
      return ThumbnailPlan(
        widthPx = (widthPx * shrink).roundToInt().coerceAtLeast(1),
        heightPx = (heightPx * shrink).roundToInt().coerceAtLeast(1),
      )
    }

    /**
     * The turn that puts [die]'s best face up.
     *
     * A die showing its `1` is a picture nobody wants of a table they are
     * about to play on, and the design's preview shows a `20`
     * (`design/dInfinity.dc.html`, option `1u`). Which readable position that
     * is comes from the die's own faces rather than from its face count, so a
     * set whose d20 is numbered 0–19 or 5–100 gets its own best face rather
     * than the twentieth one.
     *
     * It is the *readable direction* that is turned upwards, not a normal
     * guessed back from the mesh: for a d4 those are corners, so a d4 turned
     * this way stands on a face with its number at the top, which is how a d4
     * is read (`docs/dice-sets.md`, "The d4").
     */
    fun bestFaceUp(die: Die): Quaternion {
      val best = die.faces.indexOfFirst { it.value == die.maxValue }
      return Quaternion.taking(ShapeGeometry.directionsOf(die.shape)[best], Vector3.Up)
    }

    /**
     * How high the middle of [die] sits when it is turned like [turn] and
     * standing on the floor.
     *
     * Solved rather than tabulated: the lowest corner of the turned hull is
     * the one touching the table, so the middle is exactly that far above it.
     * The alternative is an inradius per shape, which is a fourth description
     * of a solid the catalogue already describes once
     * (`docs/architecture.md`, decision 35) — and the failure it buys is a die
     * floating above its own shadow, which is the bug a phone found in the
     * tray's corners.
     */
    fun restingHeightMm(
      die: Die,
      turn: Quaternion,
    ): Double = -ShapeGeometry.hullOf(die).minOf { turn.rotate(it).z }
  }
}
