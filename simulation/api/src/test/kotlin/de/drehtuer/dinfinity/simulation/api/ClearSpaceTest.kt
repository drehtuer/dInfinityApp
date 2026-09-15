package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the die an explosion or a reroll adds is dropped.
 *
 * The rule under every one of these is the app's oldest: **nothing touches a
 * die that has come to rest.** The added die is thrown in a world of its own,
 * so it could not shove a settled die whatever this answered — what these
 * assert is the other half, that the picture is honest too, and that a chain
 * with nowhere left to land stops rather than dropping a die on the pile
 * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
 */
class ClearSpaceTest {
  private val tray = TableGeometry.referenceDevice()

  @Test
  fun `a die added to an empty tray goes in the middle of it`() {
    // Every point is as clear as every other, so the tie is broken on how
    // central it is — which is where somebody would drop a die onto an empty
    // table.
    val point = ClearSpace.clearestPoint(tray, RADIUS_MM, taken = emptyList())

    assertEquals(0.0, requireNotNull(point).x, absoluteTolerance = 0.0)
    assertEquals(0.0, point.y, absoluteTolerance = 0.0)
    assertEquals(0.0, point.z, absoluteTolerance = 0.0, message = "a spawn point is on the floor")
  }

  @Test
  fun `a die is dropped clear of every die already down`() {
    val down = listOf(Vector3(-60.0, 0.0, 8.0), Vector3(0.0, 0.0, 8.0), Vector3(60.0, 0.0, 8.0))

    val point = requireNotNull(ClearSpace.clearestPoint(tray, RADIUS_MM, down))

    down.forEach { other ->
      val gap = sqrt((point.x - other.x) * (point.x - other.x) + (point.y - other.y) * (point.y - other.y))
      assertTrue(
        gap >= 2 * RADIUS_MM + ClearSpace.CLEARANCE_MM,
        "a die would have been dropped $gap mm from one already down",
      )
    }
  }

  @Test
  fun `a die is never dropped through a wall`() {
    val point = requireNotNull(ClearSpace.clearestPoint(tray, RADIUS_MM, listOf(Vector3.Zero)))

    val margin = RADIUS_MM + ClearSpace.CLEARANCE_MM
    assertTrue(abs(point.x) <= tray.longSideMm / 2 - margin, "dropped past the long wall")
    assertTrue(abs(point.y) <= tray.shortSideMm / 2 - margin, "dropped past the short wall")
  }

  @Test
  fun `the same tray and the same dice always give the same point`() {
    // A grid rather than a search, for exactly this: a roll replays to itself,
    // and an added die is part of the roll (`docs/physics-and-rendering.md`,
    // "Timestep and determinism").
    val down = listOf(Vector3(-30.0, 10.0, 8.0), Vector3(40.0, -20.0, 8.0))

    val first = ClearSpace.clearestPoint(tray, RADIUS_MM, down)
    val second = ClearSpace.clearestPoint(tray, RADIUS_MM, down)

    assertEquals(first, second)
  }

  @Test
  fun `a tray with no clear floor left has nowhere to drop one`() {
    // The honest end of a chain of explosions. The alternative is a die
    // dropped onto a settled pile, and there is no version of this app where
    // that is the better answer.
    val packed = tiledOver(tray, RADIUS_MM)

    assertNull(ClearSpace.clearestPoint(tray, RADIUS_MM, packed))
    assertFalse(ClearSpace.roomForAnother(tray, RADIUS_MM, packed))
  }

  @Test
  fun `a tray with floor to spare has room for another`() {
    assertTrue(ClearSpace.roomForAnother(tray, RADIUS_MM, listOf(Vector3.Zero)))
  }

  @Test
  fun `the engine's cap on bodies ends a chain even with floor to spare`() {
    // Tiny dice leave plenty of room and the engine still will not take a
    // hundred and first body (`docs/tables.md`, "Capacity rule").
    val many = List(TableCapacity.MAX_DICE) { Vector3(0.0, 0.0, 1.0) }

    assertFalse(ClearSpace.roomForAnother(tray, TINY_RADIUS_MM, many))
    assertTrue(ClearSpace.roomForAnother(tray, TINY_RADIUS_MM, many.dropLast(1)))
  }

  @Test
  fun `a die wider than the tray has nowhere to go at all`() {
    assertNull(ClearSpace.clearestPoint(TableGeometry(20.0), dieRadiusMm = 40.0, taken = emptyList()))
  }

  @Test
  fun `a tray that only just holds the die still offers its middle`() {
    val snug = TableGeometry(2 * (RADIUS_MM + ClearSpace.CLEARANCE_MM))

    assertNotNull(ClearSpace.clearestPoint(snug, RADIUS_MM, taken = emptyList()))
  }

  @Test
  fun `a die of no size is not a die`() {
    assertFailsWith<IllegalArgumentException> {
      ClearSpace.clearestPoint(tray, dieRadiusMm = 0.0, taken = emptyList())
    }
  }

  @Test
  fun `how much room a die needs is its size at the throw's scale`() {
    val die = d6(sizeMm = 16.0)

    assertEquals(8.0, ClearSpace.radiusOf(die, dieScale = 1.0), absoluteTolerance = 1e-9)
    assertEquals(4.0, ClearSpace.radiusOf(die, dieScale = 0.5), absoluteTolerance = 1e-9)
  }

  /** Dice packed across the whole floor, so that nothing else fits between them. */
  private fun tiledOver(
    geometry: TableGeometry,
    radiusMm: Double,
  ): List<Vector3> {
    val step = radiusMm
    val points = mutableListOf<Vector3>()
    var x = -geometry.longSideMm / 2
    while (x <= geometry.longSideMm / 2) {
      var y = -geometry.shortSideMm / 2
      while (y <= geometry.shortSideMm / 2) {
        points += Vector3(x, y, radiusMm)
        y += step
      }
      x += step
    }
    return points
  }

  private fun d6(sizeMm: Double): Die =
    Die.standard(id = "d6", shape = DieShape.Cube, material = DieMaterial(sizeMm = sizeMm))

  private companion object {
    /** A 16 mm d6, the size every worked number in `docs/tables.md` is for. */
    const val RADIUS_MM = 8.0

    /** Small enough that the floor never runs out before the body cap does. */
    const val TINY_RADIUS_MM = 1.0
  }
}
