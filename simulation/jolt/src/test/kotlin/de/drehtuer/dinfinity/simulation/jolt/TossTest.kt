package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.Release
import de.drehtuer.dinfinity.simulation.api.TableCapacity
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
