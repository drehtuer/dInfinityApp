package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.FallingIn
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Rung 1 of the correction ladder: the dice are spread and staggered so they
 * do not land on each other in the first place
 * (`docs/physics-and-rendering.md`).
 *
 * This is the rung the physics document says the work goes into, and it is the
 * only one that can be checked exhaustively without a device — a device shows
 * that dice thrown like this rarely stack, but that no two of them start above
 * the same spot is arithmetic.
 */
class SpawnLayoutTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `every die starts inside the tray, whatever the count`() {
    for (count in COUNTS) {
      val layout = SpawnLayout(geometry, RADIUS_MM, seed = 3L)
      repeat(count) { index ->
        val position = layout.placementOf(index, count).position
        assertTrue(
          "die $index of $count started outside a ${geometry.longSideMm} mm tray at $position",
          abs(position.x) + RADIUS_MM <= geometry.longSideMm / 2,
        )
        assertTrue(
          "die $index of $count started outside a ${geometry.shortSideMm} mm tray at $position",
          abs(position.y) + RADIUS_MM <= geometry.shortSideMm / 2,
        )
      }
    }
  }

  @Test
  fun `every die starts above the floor and under the ceiling`() {
    val count = TableCapacity.MAX_DICE
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 3L)
    repeat(count) { index ->
      val z = layout.placementOf(index, count).position.z
      assertTrue("a die was spawned in the floor at $z", z - RADIUS_MM > 0.0)
      assertTrue("a die was spawned through the lid at $z", z + RADIUS_MM < geometry.ceilingHeightMm)
    }
  }

  @Test
  fun `no two dice start on top of each other`() {
    val count = 20
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 11L)
    val placements = List(count) { layout.placementOf(it, count) }

    for (first in 0 until count) {
      for (second in first + 1 until count) {
        val a = placements[first].position
        val b = placements[second].position
        val apart = Vector3(a.x - b.x, a.y - b.y, 0.0).length
        val clear = apart >= 2 * RADIUS_MM
        val staggered = abs(a.z - b.z) >= 2 * RADIUS_MM
        assertTrue(
          "dice $first and $second start ${apart}mm apart at heights ${a.z} and ${b.z}",
          clear || staggered,
        )
      }
    }
  }

  @Test
  fun `the same seed throws the same throw`() {
    val count = 12
    val one = SpawnLayout(geometry, RADIUS_MM, seed = 99L)
    val again = SpawnLayout(geometry, RADIUS_MM, seed = 99L)

    repeat(count) { index ->
      assertEquals(one.placementOf(index, count), again.placementOf(index, count))
    }
  }

  @Test
  fun `another seed throws another throw`() {
    val count = 12
    val one = SpawnLayout(geometry, RADIUS_MM, seed = 99L)
    val other = SpawnLayout(geometry, RADIUS_MM, seed = 100L)

    assertNotEquals(one.placementOf(0, count), other.placementOf(0, count))
  }

  @Test
  fun `a die's throw does not depend on how many dice come after it`() {
    // Two dice share a grid only if the grid is the same, and it is not — but
    // the *randomness* must not be shared either, or adding a die to a formula
    // would silently re-roll the ones before it.
    val small = SpawnLayout(geometry, RADIUS_MM, seed = 5L).placementOf(0, 4)
    val large = SpawnLayout(geometry, RADIUS_MM, seed = 5L).placementOf(0, 40)

    assertEquals(small.rotation, large.rotation)
    assertEquals(small.angularVelocity, large.angularVelocity)
  }

  @Test
  fun `a die is let go with enough spin that where it started tells you nothing`() {
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 5L)
    repeat(20) { index ->
      val placement = layout.placementOf(index, 20)
      assertTrue(
        "a die was dropped with almost no spin: ${placement.angularVelocity}",
        placement.angularVelocity.length >=
          SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND * SpawnLayout.SPIN_FLOOR_SHARE,
      )
      assertTrue("a die was thrown upwards", placement.linearVelocity.z < 0.0)
    }
  }

  @Test
  fun `every spawn rotation is a rotation`() {
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 8L)
    repeat(40) { index ->
      val rotation = layout.placementOf(index, 40).rotation
      val length =
        rotation.w * rotation.w + rotation.x * rotation.x +
          rotation.y * rotation.y + rotation.z * rotation.z
      assertEquals("a spawn quaternion was not a unit quaternion", 1.0, length, 1e-9)
    }
  }

  @Test
  fun `a re-thrown die is dropped low, inside the tray, and differently each time`() {
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 5L)
    val first = layout.rethrowPlacement(index = 2, attempt = 0)
    val second = layout.rethrowPlacement(index = 2, attempt = 1)

    assertNotEquals("a die thrown twice the same way is a die placed twice", first, second)
    listOf(first, second).forEach { placement ->
      assertTrue(placement.position.z < SpawnLayout.DROP_HEIGHT_MM)
      assertTrue(abs(placement.position.x) + RADIUS_MM <= geometry.longSideMm / 2)
      assertTrue(abs(placement.position.y) + RADIUS_MM <= geometry.shortSideMm / 2)
      assertTrue("a re-throw is a drop, not a hurl", placement.linearVelocity.length < 400.0)
    }
  }

  @Test
  fun `a die an explosion adds is dropped into the floor the others left clear`() {
    // The one rule that cannot bend: nothing touches a die that has come to
    // rest. The added die is thrown in a world of its own, so it could not
    // reach them whatever this did — and it is dropped clear of them so that
    // the picture says the same thing
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val down =
      listOf(
        Vector3(-80.0, 0.0, ADDED_RADIUS_MM),
        Vector3(0.0, 0.0, ADDED_RADIUS_MM),
        Vector3(80.0, 0.0, ADDED_RADIUS_MM),
      )
    val layout = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = down)

    val placement = layout.placementOf(index = 0, count = 1)

    down.forEach { other ->
      val gap = hypotenuse(placement.position.x - other.x, placement.position.y - other.y)
      assertTrue(
        "an added die was dropped $gap mm from one already down",
        gap >= 2 * ADDED_RADIUS_MM + ClearSpace.CLEARANCE_MM,
      )
    }
  }

  @Test
  fun `a die thrown again is not dropped on top of a die that is already down`() {
    // Rung 3 drops a die back on the table, and the dice already down from
    // earlier throws of the same chain are drawn there with **no bodies** —
    // so nothing can push them apart and nothing will. A drop point picked
    // without looking at them is a die falling through one, which is the
    // picture claiming the physics did something it did not
    // (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll
    // adds").
    val down =
      listOf(
        Vector3(-60.0, 0.0, ADDED_RADIUS_MM),
        Vector3(0.0, 0.0, ADDED_RADIUS_MM),
        Vector3(60.0, 0.0, ADDED_RADIUS_MM),
      )
    val layout = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 21L, among = down)

    repeat(ATTEMPTS) { attempt ->
      val at = layout.rethrowPlacement(index = 0, attempt = attempt).position
      down.forEach { other ->
        val gap = hypotenuse(at.x - other.x, at.y - other.y)
        assertTrue(
          "attempt $attempt dropped a die thrown again $gap mm from one already down",
          gap >= 2 * ADDED_RADIUS_MM + ClearSpace.CLEARANCE_MM,
        )
      }
    }
  }

  @Test
  fun `two dice thrown again in the same pass are not dropped on the same spot`() {
    // A pass can throw several dice again at once, and those dice *do* have
    // bodies — two dropped on one patch of floor start inside each other and
    // the solver spends the throw shoving them apart, which is where the
    // die-into-die overlap comes from (`docs/TODO.md`, Step 5.4). Each one
    // makes room for the ones after it, exactly as a round an explosion owes
    // does ([SpawnLayout.rethrowPlacement]).
    val layout = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 34L)
    val placed = mutableListOf<Vector3>()

    repeat(A_PASS) { index ->
      val at = layout.rethrowPlacement(index = index, attempt = 0, clearOf = placed).position
      placed.forEach { other ->
        val gap = hypotenuse(at.x - other.x, at.y - other.y)
        assertTrue(
          "die $index of one pass was thrown again $gap mm from another of the same pass",
          gap >= 2 * ADDED_RADIUS_MM + ClearSpace.CLEARANCE_MM,
        )
      }
      placed += at
    }
  }

  @Test
  fun `a die an explosion adds is dropped, not hurled across the tray`() {
    // The same throw a re-thrown die gets, for the same reason: a die that
    // travels is a die that arrives somewhere nobody made room for.
    val layout = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = listOf(Vector3(0.0, 0.0, ADDED_RADIUS_MM)))

    val placement = layout.placementOf(index = 0, count = 1)

    assertEquals("an added die was thrown sideways", 0.0, placement.linearVelocity.x, 0.0)
    assertEquals(0.0, placement.linearVelocity.y, 0.0)
    assertTrue("an added die was hurled", placement.linearVelocity.length < 400.0)
    assertTrue("an added die was dropped from the clouds", placement.position.z < SpawnLayout.DROP_HEIGHT_MM)
    assertTrue("an added die was placed rather than thrown", placement.angularVelocity.length > 0.0)
  }

  @Test
  fun `the same tray and the same dice drop an added die in the same place twice`() {
    val down = listOf(Vector3(30.0, -20.0, ADDED_RADIUS_MM))

    val first = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = down).placementOf(0, 1)
    val second = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = down).placementOf(0, 1)

    assertEquals("a roll with an explosion in it did not replay to itself", first, second)
  }

  private fun hypotenuse(
    x: Double,
    y: Double,
  ): Double = Vector3(x, y, 0.0).length

  @Test
  fun `asking for a die that is not in the throw is a bug`() {
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 1L)
    try {
      layout.placementOf(index = 5, count = 5)
      throw AssertionError("a die outside the throw was placed anyway")
    } catch (expected: IllegalArgumentException) {
      assertTrue(expected.message!!.contains("5"))
    }
  }

  @Test
  fun `a round of added dice is not dropped on one spot`() {
    // Each die of a round takes floor the next one cannot have. Asking
    // `ClearSpace` the same question three times gives the same answer three
    // times, and all three dice would be dropped in one place — which is what
    // the throw spec used to forbid by allowing only one added die at a time
    // (`docs/dice-notation.md`, "Evaluation").
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 77L, among = listOf(Vector3(0.0, 0.0, RADIUS_MM)))

    val places = (0 until 3).map { layout.placementOf(it, 3).position }

    assertEquals("two dice of one round were dropped on the same spot", 3, places.distinct().size)
  }

  @Test
  fun `where a die of a round goes does not depend on which order it was asked for`() {
    val among = listOf(Vector3(0.0, 0.0, RADIUS_MM))
    val inOrder = SpawnLayout(geometry, RADIUS_MM, seed = 77L, among = among)
    val backwards = SpawnLayout(geometry, RADIUS_MM, seed = 77L, among = among)

    val forwards = (0 until 3).map { inOrder.placementOf(it, 3).position }
    val reversed = (2 downTo 0).map { backwards.placementOf(it, 3).position }.reversed()

    assertEquals(forwards, reversed)
  }

  @Test
  fun `the board before a throw falls at the same gravity the throw does`() {
    // `FallingIn` cannot reach this constant — it is upstream of the solver
    // and the shake driver is not — so it carries its own copy, and a drop
    // that fell at some other rate than the throw that follows it would be
    // two tables in one tray.
    assertEquals(ShakeDriver.GRAVITY_MM_PER_SECOND2, FallingIn.GRAVITY_MM_PER_SECOND2, 0.0)
  }

  @Test
  fun `a die put on the board is dropped from higher than one an explosion adds`() {
    // Deliberately, and this is where it is written down. An added die is
    // dropped into a roll among dice whose faces are being read, and its job
    // is not to upstage them; a die the player has just put on the board *is*
    // the thing they are looking at.
    assertTrue(
      "a board's drop of ${FallingIn.DROP_HEIGHT_MM} mm is no higher than an added die's",
      FallingIn.DROP_HEIGHT_MM > SpawnLayout.RETHROW_HEIGHT_MM,
    )
  }

  private companion object {
    /** A 16 mm d6's bounding radius, which is what the capacity table is built on. */
    const val RADIUS_MM = 13.86

    /** A 16 mm d6's, which is what an exploding `8d6!` actually adds. */
    const val ADDED_RADIUS_MM = 8.0

    /** Enough re-throws that a drop point chosen blind would land on something. */
    const val ATTEMPTS = 50

    /** A pass that throws a tray's worth of dice again at once. */
    const val A_PASS = 20

    val COUNTS = listOf(1, 2, 5, 8, 20, 40, 60, TableCapacity.MAX_DICE)
  }
}
