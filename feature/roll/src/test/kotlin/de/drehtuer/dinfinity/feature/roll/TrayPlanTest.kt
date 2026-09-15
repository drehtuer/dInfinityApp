package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.simulation.api.ContactPoint
import de.drehtuer.dinfinity.simulation.api.DieDiagnostic
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.Struck
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a point in the tray falls on the debug overlay's plan of it
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * Plain arithmetic and therefore a plain JVM test, which is the point of
 * keeping it out of the composable: what the overlay draws is decided here,
 * and only where the pixels go is Compose's.
 */
class TrayPlanTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `the middle of the tray is the middle of the plan`() {
    val spot = TrayPlan.spot(Vector3(0.0, 0.0, 0.0), geometry)

    assertEquals(0.5, spot.across, EPSILON)
    assertEquals(0.5, spot.along, EPSILON)
    assertTrue(spot.onThePlan)
  }

  @Test
  fun `the far end of the tray is the top of the plan, because plus x is up`() {
    // The same convention `TrayCamera` builds its `up` from, so the plan and
    // the picture agree about which end is which (`docs/tables.md`).
    val far = TrayPlan.spot(Vector3(geometry.longSideMm / 2, 0.0, 0.0), geometry)
    val near = TrayPlan.spot(Vector3(-geometry.longSideMm / 2, 0.0, 0.0), geometry)

    assertEquals(0.0, far.along, EPSILON)
    assertEquals(1.0, near.along, EPSILON)
  }

  @Test
  fun `plus y is the right of the plan, which is where the camera puts it`() {
    val right = TrayPlan.spot(Vector3(0.0, geometry.shortSideMm / 2, 0.0), geometry)
    val left = TrayPlan.spot(Vector3(0.0, -geometry.shortSideMm / 2, 0.0), geometry)

    assertEquals(1.0, right.across, EPSILON)
    assertEquals(0.0, left.across, EPSILON)
  }

  @Test
  fun `a die outside the tray is drawn outside it rather than tidied back in`() {
    // A die past the wall is the bug somebody turned the overlay on to find.
    // Clamping it would hide exactly that (`docs/TODO.md`, Step 5.4).
    val escaped = TrayPlan.spot(Vector3(geometry.longSideMm, 0.0, 0.0), geometry)

    assertTrue(escaped.along < 0.0)
    assertFalse(escaped.onThePlan)
  }

  @Test
  fun `a die's footprint is its own size against each side of the tray`() {
    val across = TrayPlan.width(SIXTEEN_MM, geometry)
    val along = TrayPlan.height(SIXTEEN_MM, geometry)

    assertEquals(SIXTEEN_MM / geometry.shortSideMm, across, EPSILON)
    assertEquals(SIXTEEN_MM / geometry.longSideMm, along, EPSILON)
    // The tray is longer than it is wide, so the same die is a larger fraction
    // of the short side. A plan that used one number for both would draw the
    // dice as rectangles on a square tray.
    assertTrue(across > along)
  }

  @Test
  fun `the plan is the tray's own shape, long side vertical`() {
    assertEquals(geometry.shortSideMm / geometry.longSideMm, TrayPlan.aspect(geometry), EPSILON)
    assertTrue("a phone's tray is taller than it is wide", TrayPlan.aspect(geometry) < 1.0)
  }

  @Test
  fun `a die's box is centred on it and as big as the die is`() {
    val mark = TrayPlan.markOf(die(Vector3(0.0, 0.0, 8.0)), geometry)

    assertEquals(0.5, mark.left + mark.width / 2, EPSILON)
    assertEquals(0.5, mark.top + mark.height / 2, EPSILON)
    assertEquals(TrayPlan.width(SIXTEEN_MM, geometry), mark.width, EPSILON)
    assertEquals(TrayPlan.height(SIXTEEN_MM, geometry), mark.height, EPSILON)
  }

  @Test
  fun `a die still going is drawn as one, and fills nothing`() {
    val mark = TrayPlan.markOf(die(still = 0), geometry)

    assertEquals(PlanTint.Moving, mark.tint)
    assertEquals(0.0, mark.fill, EPSILON)
  }

  @Test
  fun `a die that has stopped fills its box`() {
    val mark = TrayPlan.markOf(die(still = SettleRule.REST_STEPS), geometry)

    assertEquals(PlanTint.Still, mark.tint)
    assertEquals(1.0, mark.fill, EPSILON)
  }

  @Test
  fun `a die standing on another is drawn as trouble even once it has stopped`() {
    // The one thing on the plan somebody turned the overlay on to find. A
    // stacked die that is also at rest must not read as an ordinary settled
    // one (`docs/physics-and-rendering.md`, rung 2).
    val mark = TrayPlan.markOf(die(still = SettleRule.REST_STEPS, stacked = true), geometry)

    assertEquals(PlanTint.Trouble, mark.tint)
  }

  @Test
  fun `a contact is a dot where the die was, sized by how hard it was`() {
    val hardest = TrayPlan.markOf(contact(strength = 1.0), geometry)
    val quietest = TrayPlan.markOf(contact(strength = 0.0), geometry)

    assertEquals(0.5, hardest.across, EPSILON)
    assertEquals(0.5, hardest.along, EPSILON)
    assertEquals(1.0, hardest.size, EPSILON)
    // Not nothing: a contact barely worth reporting is still a contact, and a
    // dot of no size is a contact that did not happen.
    assertEquals(TrayPlan.SMALLEST_DOT, quietest.size, EPSILON)
    assertTrue(quietest.size > 0.0)
  }

  @Test
  fun `a die hitting another die is the contact worth picking out`() {
    assertTrue(TrayPlan.markOf(contact(struck = Struck.Die), geometry).onADie)
    assertFalse(TrayPlan.markOf(contact(struck = Struck.Floor), geometry).onADie)
    assertFalse(TrayPlan.markOf(contact(struck = Struck.Wall), geometry).onADie)
  }

  private fun die(
    position: Vector3 = Vector3(0.0, 0.0, 8.0),
    still: Int = 0,
    stacked: Boolean = false,
  ): DieDiagnostic =
    DieDiagnostic(
      index = 0,
      position = position,
      acrossMm = SIXTEEN_MM,
      stillForSteps = still,
      atRest = still >= SettleRule.REST_STEPS,
      supportedByDie = stacked,
    )

  private fun contact(
    strength: Double = 0.5,
    struck: Struck = Struck.Die,
  ): ContactPoint =
    ContactPoint(
      stepIndex = 0,
      dieIndex = 0,
      position = Vector3(0.0, 0.0, 8.0),
      struck = struck,
      strength = strength,
    )

  private companion object {
    const val EPSILON = 1e-9
    const val SIXTEEN_MM = 16.0
  }
}
