package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Release
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

/** How the hand tosses the dice when it lets go (`docs/physics-and-rendering.md`, "The hand lets go"). */
class TossTest {
  @Test
  fun `every die is thrown up, high enough to leave the felt and under the lid`() {
    repeat(COUNT) { index ->
      val (linear, _) = Toss.of(ALONG, SEED, index, COUNT)
      val least = sqrt(2 * ShakeDriver.GRAVITY_MM_PER_SECOND2 * Toss.LEAST_LIFT_MM)
      val most = sqrt(2 * ShakeDriver.GRAVITY_MM_PER_SECOND2 * Toss.MOST_LIFT_MM)
      assertTrue("die $index tossed up at ${linear.z} mm/s", linear.z in least..most)
    }
  }

  @Test
  fun `every die is thrown the way the hand went, at about its speed`() {
    repeat(COUNT) { index ->
      val (linear, _) = Toss.of(ALONG, SEED, index, COUNT)
      val along = Vector3(linear.x, linear.y, 0.0)
      val expected = ALONG.speedMmPerSecond * Toss.taperFor(COUNT)
      val spread = Toss.SPEED_SPREAD + 1e-9
      assertTrue("die $index at ${along.length} mm/s", along.length in expected * (1 - spread)..expected * (1 + spread))
      assertTrue("die $index thrown off the hand's line", along.x / along.length >= cos(Toss.AIM_SPREAD_RADIANS) - 1e-9)
    }
  }

  @Test
  fun `every die is given a spin of its own, never a small one`() {
    val spins = List(COUNT) { Toss.of(ALONG, SEED, it, COUNT).second }

    spins.forEach {
      assertTrue(
        "a spin of ${it.length}",
        it.length in SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND / 2..SpawnLayout.SPAWN_SPIN_RADIANS_PER_SECOND + 1e-9,
      )
    }
    assertNotEquals(spins[0], spins[1])
  }

  @Test
  fun `a hand that went no way in particular still throws its dice together`() {
    val headings =
      List(COUNT) {
        Toss.of(AIMLESS, SEED, it, COUNT).first.let { v ->
          Vector3(v.x, v.y, 0.0).normalised()
        }
      }

    for (one in headings) {
      for (other in headings) {
        assertTrue((one dot other) >= cos(2 * Toss.AIM_SPREAD_RADIANS) - 1e-9)
      }
    }
  }

  @Test
  fun `the same release, seed and die toss the same way`() {
    assertEquals(Toss.of(ALONG, SEED, 2, COUNT), Toss.of(ALONG, SEED, 2, COUNT))
    assertNotEquals(Toss.of(ALONG, SEED, 2, COUNT), Toss.of(ALONG, SEED + 1, 2, COUNT))
  }

  @Test
  fun `a die already sliding is tossed on the way it is going, not turned round`() {
    val sliding = Vector3(0.0, -1_500.0, 0.0)

    val (linear, _) = Toss.of(ALONG, SEED, 0, COUNT, sliding)

    val along = Vector3(linear.x, linear.y, 0.0)
    assertTrue(
      "a sliding die was turned to the hand's stroke",
      along.normalised() dot Vector3(0.0, -1.0, 0.0) >= cos(Toss.AIM_SPREAD_RADIANS) - 1e-9,
    )
    assertTrue("a toss slowed a die down", along.length >= sliding.length * (1 - Toss.SPEED_SPREAD) - 1e-9)
  }

  @Test
  fun `a die barely moving takes the hand's direction`() {
    val (linear, _) = Toss.of(ALONG, SEED, 0, COUNT, Vector3(0.0, Toss.ONWARD_MM_PER_SECOND / 2, 0.0))

    assertTrue(linear.x / Vector3(linear.x, linear.y, 0.0).length >= cos(Toss.AIM_SPREAD_RADIANS) - 1e-9)
  }

  @Test
  fun `a die against a wall is never tossed into it`() {
    val tray = TableGeometry.referenceDevice()
    val atTheEnd = Vector3(tray.longSideMm / 2 - 10.0, 0.0, 8.0)

    repeat(COUNT) { index ->
      val (linear, _) = Toss.of(ALONG, SEED, index, COUNT, position = atTheEnd, tray = tray)
      assertTrue("die $index was tossed into the end wall it lay against", linear.x < 0.0)
    }
  }

  @Test
  fun `a die in the open is tossed the way the hand went, walls or no walls`() {
    val tray = TableGeometry.referenceDevice()

    val (inTheOpen, _) = Toss.of(ALONG, SEED, 0, COUNT, position = Vector3(0.0, 0.0, 8.0), tray = tray)

    assertEquals(Toss.of(ALONG, SEED, 0, COUNT).first, inTheOpen)
  }

  @Test
  fun `off the walls turns back only what points at a wall that is near`() {
    val tray = TableGeometry.referenceDevice()
    val corner = Vector3(tray.longSideMm / 2 - 5.0, tray.shortSideMm / 2 - 5.0, 8.0)

    assertEquals(Vector3(-0.6, -0.8, 0.0), Toss.offTheWalls(Vector3(0.6, 0.8, 0.0), corner, tray))
    val nearTheBottom = Vector3(0.0, -tray.shortSideMm / 2 + 5.0, 8.0)
    assertEquals(Vector3(0.6, 0.8, 0.0), Toss.offTheWalls(Vector3(0.6, -0.8, 0.0), nearTheBottom, tray))
    assertEquals(Vector3(0.6, 0.8, 0.0), Toss.offTheWalls(Vector3(0.6, 0.8, 0.0), nearTheBottom, tray))
  }

  @Test
  fun `the dice leave the hand within a tenth of a second, each when its seed says`() {
    val delays = List(COUNT * 4) { Toss.delayOf(SEED, it) }

    assertTrue(delays.all { it in 0..Toss.STAGGER_STEPS })
    assertTrue("every die left on the same step", delays.toSet().size > 1)
    assertEquals(Toss.delayOf(SEED, 3), Toss.delayOf(SEED, 3))
  }

  @Test
  fun `a handful is tossed hard and a full tray gently`() {
    assertEquals(1.0, Toss.taperFor(0), 1e-12)
    assertEquals(SpawnLayout.CROWDED_SHARE, Toss.taperFor(TableCapacity.MAX_DICE), 1e-12)
    assertEquals(SpawnLayout.CROWDED_SHARE, Toss.taperFor(TableCapacity.MAX_DICE * 2), 1e-12)
  }

  private companion object {
    const val COUNT = 6
    const val SEED = 17L
    val ALONG = Release(direction = Vector3(1.0, 0.0, 0.0), speedMmPerSecond = 1_200.0)
    val AIMLESS = Release(direction = null, speedMmPerSecond = Release.LEAST_MM_PER_SECOND)
  }
}
