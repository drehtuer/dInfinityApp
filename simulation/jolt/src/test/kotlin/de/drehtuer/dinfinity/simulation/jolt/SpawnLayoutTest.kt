package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.BoardDrops
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Release
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
  fun `a die an explosion adds is dropped, not hurled across the tray`() {
    // Dropped rather than thrown, because a die that
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
  fun `a die put on the board is dropped from higher than one an explosion adds`() {
    // Deliberately, and this is where it is written down. An added die is
    // dropped into a roll among dice whose faces are being read, and its job
    // is not to upstage them; a die the player has just put on the board *is*
    // the thing they are looking at. The board's drop falls at the throw's
    // gravity because it is simulated in the same engine (`settleOn`).
    assertTrue(
      "a board's drop of ${BoardDrops.DROP_HEIGHT_MM} mm is no higher than an added die's",
      BoardDrops.DROP_HEIGHT_MM > SpawnLayout.ADDED_DROP_HEIGHT_MM,
    )
  }

  @Test
  fun `dice let go by a hand start together, on the side the throw comes from`() {
    val count = 4
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 3L, release = ALONG)
    val positions = List(count) { layout.placementOf(it, count).position }

    positions.forEach { assertTrue("a die started on the side the throw goes to, at $it", it.x < 0.0) }
    val spread = positions.maxOf { it.x } - positions.minOf { it.x }
    assertTrue("a hand of four is $spread mm wide", spread < 4 * RADIUS_MM)
  }

  @Test
  fun `dice let go by a hand leave it high, and every die is under the lid`() {
    val hand = SpawnLayout(geometry, RADIUS_MM, seed = 3L, release = ALONG)
    repeat(4) { index ->
      val z = hand.placementOf(index, 4).position.z
      assertTrue("die $index of a hand let go at $z", z >= SpawnLayout.RELEASE_HEIGHT_MM + RADIUS_MM)
    }
    for (count in COUNTS) {
      val layout = SpawnLayout(geometry, RADIUS_MM, seed = 3L, release = ALONG)
      repeat(count) { index ->
        val z = layout.placementOf(index, count).position.z
        assertTrue("die $index of $count let go in the floor at $z", z - RADIUS_MM > 0.0)
        assertTrue("die $index of $count let go through the lid at $z", z + RADIUS_MM < geometry.ceilingHeightMm)
      }
    }
  }

  @Test
  fun `dice let go by a hand are all inside the tray and none on another, whatever the count`() {
    for (count in COUNTS) {
      for (release in listOf(ALONG, ACROSS, AIMLESS)) {
        val layout = SpawnLayout(geometry, RADIUS_MM, seed = 11L, release = release)
        val positions = List(count) { layout.placementOf(it, count).position }
        assertInsideTheTray(positions, "$count dice, $release")
        // Bounding spheres apart up to the twenty the grid throw promises
        // that for; past it the grid relies on its height bands, as the
        // grid throw does ("no two dice start on top of each other").
        if (count <= NO_TOUCHING_UP_TO) assertApart(positions, "$count dice, $release")
      }
    }
  }

  private fun flat(velocity: Vector3): Vector3 = Vector3(velocity.x, velocity.y, 0.0)

  private fun assertInsideTheTray(
    positions: List<Vector3>,
    what: String,
  ) {
    positions.forEach {
      assertTrue("$what: $it is outside the tray", abs(it.x) + RADIUS_MM <= geometry.longSideMm / 2)
      assertTrue("$what: $it is outside the tray", abs(it.y) + RADIUS_MM <= geometry.shortSideMm / 2)
    }
  }

  private fun assertApart(
    positions: List<Vector3>,
    what: String,
  ) {
    for (one in positions.indices) {
      for (other in one + 1 until positions.size) {
        val gap = (positions[one] - positions[other]).length
        assertTrue("$what: two started $gap mm apart", gap >= 2 * RADIUS_MM)
      }
    }
  }

  @Test
  fun `dice let go by a hand are thrown the way it went, at about its speed, and down`() {
    val count = 5
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 7L, release = ALONG)
    repeat(count) { index ->
      val velocity = layout.placementOf(index, count).linearVelocity
      val along = Vector3(velocity.x, velocity.y, 0.0)
      val speed = along.length
      val taper = 1.0 - (1.0 - SpawnLayout.CROWDED_SHARE) * count / TableCapacity.MAX_DICE
      val expected = ALONG.speedMmPerSecond * taper
      assertTrue("die $index thrown at $speed mm/s", speed in expected * (1 - SPREAD)..expected * (1 + SPREAD))
      assertTrue(
        "die $index thrown ${Math.toDegrees(kotlin.math.acos(velocity.x / speed))}° off the hand",
        velocity.x / speed >= kotlin.math.cos(SpawnLayout.AIM_SPREAD_RADIANS) - 1e-9,
      )
      assertTrue("die $index thrown up", velocity.z < 0.0)
    }
  }

  @Test
  fun `a hand that went no way in particular still throws its dice together`() {
    val count = 6
    val layout = SpawnLayout(geometry, RADIUS_MM, seed = 21L, release = AIMLESS)
    val headings = List(count) { flat(layout.placementOf(it, count).linearVelocity).normalised() }

    for (one in headings) {
      for (other in headings) {
        assertTrue(
          "two dice of one hand thrown apart",
          (one dot other) >= kotlin.math.cos(2 * SpawnLayout.AIM_SPREAD_RADIANS) - 1e-9,
        )
      }
    }
  }

  @Test
  fun `the same hand and seed let go the same way twice`() {
    val one = SpawnLayout(geometry, RADIUS_MM, seed = 99L, release = ACROSS)
    val again = SpawnLayout(geometry, RADIUS_MM, seed = 99L, release = ACROSS)

    assertEquals(List(8) { one.placementOf(it, 8) }, List(8) { again.placementOf(it, 8) })
  }

  @Test
  fun `a die an explosion adds is dropped among the others, not thrown from a hand`() {
    val among = listOf(Vector3(0.0, 0.0, ADDED_RADIUS_MM))
    val dropped = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = among).placementOf(0, 1)
    val released = SpawnLayout(geometry, ADDED_RADIUS_MM, seed = 9L, among = among, release = ALONG).placementOf(0, 1)

    assertEquals(dropped, released)
  }

  private companion object {
    val ALONG = Release(direction = Vector3(1.0, 0.0, 0.0), speedMmPerSecond = 1_200.0)
    val ACROSS = Release(direction = Vector3(0.0, -1.0, 0.0), speedMmPerSecond = 900.0)
    val AIMLESS = Release(direction = null, speedMmPerSecond = Release.LEAST_MM_PER_SECOND)

    /** The most dice the grid promises start a whole bounding sphere apart. */
    const val NO_TOUCHING_UP_TO = 20

    /** How far a released die's speed may be from the hand's, with float slack. */
    const val SPREAD = SpawnLayout.SPEED_SPREAD + 1e-9

    /** A 16 mm d6's bounding radius, which is what the capacity table is built on. */
    const val RADIUS_MM = 13.86

    /** A 16 mm d6's, which is what an exploding `8d6!` actually adds. */
    const val ADDED_RADIUS_MM = 8.0

    val COUNTS = listOf(1, 2, 5, 8, 20, 40, 60, TableCapacity.MAX_DICE)
  }
}
