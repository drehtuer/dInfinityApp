package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.simulation.api.ContactPoint
import de.drehtuer.dinfinity.simulation.api.DieDiagnostic
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * Where a point in the tray goes on a **plan** of it — the arithmetic behind
 * the debug overlay (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * A plan, looked at from straight above, and deliberately not the camera's
 * view. The tray is drawn in perspective from a tilted camera
 * ([de.drehtuer.dinfinity.render.filament.TrayCamera]), and an overlay that
 * tried to register with that would have to reproduce the projection, the
 * pinch and the pan, in Compose, in order to draw a wireframe on top of
 * pictures of dice that already show where they are. A plan says the thing the
 * pictures do not — who is stacked on whom, who is against a wall, who has not
 * stopped — and it says it without a single line of GPU code.
 *
 * Fractions rather than pixels, because the panel's size is the screen's
 * business and this is the tray's. `0,0` is the top-left corner of the plan.
 *
 * The axes are the tray's own (`docs/tables.md`): the long side is `+x` and
 * points up the screen, `+y` points to its right, and the floor is `z = 0`.
 * That is the same convention [TrayCamera] builds its `up` and `right` from,
 * so the plan and the picture agree about which end of the tray is which.
 */
object TrayPlan {
  /**
   * Where [position] falls on the plan, `0` to `1` in each direction.
   *
   * Points outside the tray are kept outside rather than clamped: a die beyond
   * the wall is the bug somebody turned the overlay on to see, and an overlay
   * that quietly tidied it back inside would be hiding exactly that
   * (`docs/TODO.md`, Step 5.4 — no tunnelling).
   */
  fun spot(
    position: Vector3,
    geometry: TableGeometry,
  ): PlanSpot =
    PlanSpot(
      across = HALF + position.y / geometry.shortSideMm,
      along = HALF - position.x / geometry.longSideMm,
    )

  /**
   * How wide a die of [acrossMm] is on the plan, as a fraction of the plan's
   * **short** side — which is what the across axis is measured in.
   *
   * The die's nominal size at the scale it was thrown, not the diameter of its
   * bounding sphere: it is a footprint to find a die by, not a picture of the
   * solid (`docs/architecture.md`, decision 36).
   */
  fun width(
    acrossMm: Double,
    geometry: TableGeometry,
  ): Double = acrossMm / geometry.shortSideMm

  /**
   * Everything the plan draws for one die: where its box is, how big, how far
   * its rest timer has filled, and which of the three states it is in.
   *
   * Computed here rather than inside the `Canvas` lambda, so the judgement in
   * it — which is all of it — is a JVM test rather than something only a GPU
   * can answer. What is left in the lambda is a rectangle and a colour.
   */
  fun markOf(
    die: DieDiagnostic,
    geometry: TableGeometry,
  ): DieMark {
    val spot = spot(die.position, geometry)
    val across = width(die.acrossMm, geometry)
    val along = height(die.acrossMm, geometry)
    return DieMark(
      left = spot.across - across / 2,
      top = spot.along - along / 2,
      width = across,
      height = along,
      fill = die.restProgress,
      tint =
        when {
          // Standing on another die first: it is the one thing on the plan
          // somebody turned the overlay on to find, and a stacked die that has
          // also come to rest must not be drawn as an ordinary settled one
          // (`docs/physics-and-rendering.md`, rung 2).
          die.stacked -> PlanTint.Trouble
          die.atRest -> PlanTint.Still
          else -> PlanTint.Moving
        },
    )
  }

  /**
   * The dot the plan draws for one contact.
   *
   * [ContactMark.onADie] rather than a colour, for the reason [DieMark] carries
   * a [PlanTint]: which colour that is belongs to the theme, and the theme
   * belongs to the composable.
   */
  fun markOf(
    contact: ContactPoint,
    geometry: TableGeometry,
  ): ContactMark {
    val spot = spot(contact.position, geometry)
    return ContactMark(
      across = spot.across,
      along = spot.along,
      size = SMALLEST_DOT + (1.0 - SMALLEST_DOT) * contact.strength,
      onADie = contact.struck == Struck.Die,
    )
  }

  /** The same, down the plan's long side. */
  fun height(
    acrossMm: Double,
    geometry: TableGeometry,
  ): Double = acrossMm / geometry.longSideMm

  /**
   * How tall the plan is against its width — the tray's own shape.
   *
   * The long side is vertical, because that is how the tray is on the screen
   * and an overlay turned the other way would take a moment to read every
   * time.
   */
  fun aspect(geometry: TableGeometry): Double = geometry.shortSideMm / geometry.longSideMm

  private const val HALF = 0.5

  /**
   * How small the quietest contact's dot is against the hardest.
   *
   * Not nothing: a contact that was barely worth reporting is still a contact,
   * and a dot of zero size is a contact that did not happen.
   */
  const val SMALLEST_DOT: Double = 0.4
}

/**
 * One die's box on the plan, in fractions of it.
 *
 * @param fill how far the rest timer has filled, `0` to `1`.
 * @param tint which of the three states the die is in.
 */
data class DieMark(
  val left: Double,
  val top: Double,
  val width: Double,
  val height: Double,
  val fill: Double,
  val tint: PlanTint,
)

/**
 * The three things a die on the plan can be, and no more.
 *
 * Three is what somebody watching for stacking needs: still going, stopped, or
 * standing on another die. Which colour each is belongs to the theme.
 */
enum class PlanTint {
  /** Still tumbling. */
  Moving,

  /** Stopped, and therefore out of reach of everything. */
  Still,

  /** Standing on another die — the trouble rung 2 exists for. */
  Trouble,
}

/**
 * One contact's dot on the plan, in fractions of it.
 *
 * @param size how big the dot is against the largest, [TrayPlan.SMALLEST_DOT]
 *   to `1`.
 * @param onADie whether it hit another die, which is the one worth finding.
 */
data class ContactMark(
  val across: Double,
  val along: Double,
  val size: Double,
  val onADie: Boolean,
)

/**
 * A point on the plan of the tray, `0` to `1` from its top-left corner.
 *
 * @param across left to right, which is the tray's `+y`.
 * @param along top to bottom, which is the tray's `-x`.
 */
data class PlanSpot(
  val across: Double,
  val along: Double,
) {
  /** True when this point is on the plan at all rather than past an edge. */
  val onThePlan: Boolean get() = across in 0.0..1.0 && along in 0.0..1.0
}
