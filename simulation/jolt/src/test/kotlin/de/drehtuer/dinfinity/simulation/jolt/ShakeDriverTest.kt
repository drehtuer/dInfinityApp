package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shake, replayed (`docs/physics-and-rendering.md`, "Shake input").
 *
 * The whole point of indexing samples by simulation step rather than by the
 * moment they arrived is that the same record drives normal mode, where it is
 * consumed as it happens, and power-saving mode, where it is replayed as a
 * batch afterwards, and both get the same roll. That is a property of this
 * class and of nothing else, so it is checked here.
 */
class ShakeDriverTest {
  @Test
  fun `a tap-to-roll throw is a still phone and plain gravity`() {
    val driver = ShakeDriver(emptyList())
    driver.advance(0)

    assertTrue(driver.isStill)
    assertEquals(ShakeDriver.DEFAULT_GRAVITY, driver.gravity)
  }

  @Test
  fun `a phone pulled one way loads the dice the other`() {
    val driver = ShakeDriver(listOf(sample(step = 0, x = 5_000.0)))
    driver.advance(0)

    assertFalse(driver.isStill)
    assertTrue(
      "the hand's acceleration reaches a die as its inverse, or the tray has to move",
      driver.gravity.x < 0.0,
    )
    assertEquals("and down is still down", -ShakeDriver.GRAVITY_MM_PER_SECOND2, driver.gravity.z, 1e-6)
  }

  @Test
  fun `the hand keeps pushing between one reading and the next`() {
    // The sensors run at about 50 Hz and the simulation at 120, so most steps
    // have no reading of their own. A hand does not stop between two moments of
    // a shake, so its force does not either — dropping it would drive the dice
    // on two steps in five and let them coast through the rest, which is what
    // "the dice do not follow the shake" looked like on the phone.
    val driver = ShakeDriver(listOf(sample(step = 3, x = 10_000.0)))

    driver.advance(2)
    assertEquals("nothing happened before the sample's step", 0.0, driver.gravity.x, 0.0)

    driver.advance(3)
    val pushed = driver.gravity.x
    assertTrue("the sample's own step pushed the dice", pushed < 0.0)

    driver.advance(4)
    assertEquals("the push stopped between two readings", pushed, driver.gravity.x, 0.0)
  }

  @Test
  fun `the hand is let go once the readings have actually stopped`() {
    // Held, not held for ever: a shake that has ended must stop driving, or the
    // dice would never come to rest.
    val driver = ShakeDriver(listOf(sample(step = 0, x = 10_000.0)))

    (0..ShakeDriver.HOLD_STEPS).forEach(driver::advance)
    assertTrue("the hand was let go while it was still within reach", driver.gravity.x < 0.0)

    driver.advance(ShakeDriver.HOLD_STEPS + 1)
    assertEquals("the hand was never let go", 0.0, driver.gravity.x, 0.0)
  }

  @Test
  fun `a roll is still being shaken while its readings run ahead of it`() {
    val driver = ShakeDriver(List(10) { sample(step = it, x = 5_000.0) })

    assertTrue("the hand had barely started", driver.stillShaking(0))
    assertTrue("the last reading is still in reach", driver.stillShaking(9 + ShakeDriver.HOLD_STEPS))
    assertFalse("the shake is long over", driver.stillShaking(9 + ShakeDriver.HOLD_STEPS + 1))
  }

  @Test
  fun `a tap is never shaking`() {
    // Nothing to wait for, so a tapped roll ends when the dice stop and not a
    // step later.
    val driver = ShakeDriver(emptyList())

    assertTrue(driver.isStill)
    assertFalse(driver.stillShaking(0))
  }

  @Test
  fun `a sample arriving mid-roll extends how long the hand is still shaking`() {
    val driver = ShakeDriver(listOf(sample(step = 0, x = 5_000.0)))
    assertFalse(driver.stillShaking(ShakeDriver.HOLD_STEPS + 1))

    driver.add(sample(step = 40, x = 5_000.0))

    assertTrue("a hand that kept going was not noticed", driver.stillShaking(ShakeDriver.HOLD_STEPS + 1))
  }

  @Test
  fun `the same record replays to the same gravity, in any order`() {
    val samples = List(60) { sample(it, x = 4_000.0 * if (it % 2 == 0) 1 else -1) }
    val live = ShakeDriver(samples)
    val replayed = ShakeDriver(samples.shuffled())

    repeat(60) {
      live.advance(it)
      replayed.advance(it)
      assertEquals(
        "a record indexed by step cannot depend on the order it was written down in",
        live.gravity,
        replayed.gravity,
      )
    }
  }

  @Test
  fun `a shake no hand could give is capped rather than passed on`() {
    val driver = ShakeDriver(listOf(sample(step = 0, x = 5_000_000.0)))
    driver.advance(0)

    assertTrue(
      "a sensor fault must not be able to fire the dice through the tray",
      driver.gravity.length <= ShakeDriver.MAX_SHAKE_MM_PER_SECOND2 + ShakeDriver.GRAVITY_MM_PER_SECOND2,
    )
  }

  @Test
  fun `turning the phone turns gravity, and its length is still gravity`() {
    val sideways = Vector3(1.0, 0.0, 0.0)
    val driver = ShakeDriver(listOf(ShakeSample(0, Vector3.Zero, sideways)))
    driver.advance(0)

    assertEquals(ShakeDriver.GRAVITY_MM_PER_SECOND2, driver.gravity.length, 1e-6)
    assertEquals(ShakeDriver.GRAVITY_MM_PER_SECOND2, driver.gravity.x, 1e-6)
  }

  @Test
  fun `a gravity vector of no length at all is ignored rather than divided by`() {
    val driver = ShakeDriver(listOf(ShakeSample(0, Vector3.Zero, Vector3.Zero)))
    driver.advance(0)

    assertEquals(ShakeDriver.DEFAULT_GRAVITY, driver.gravity)
  }

  @Test
  fun `gravity stays where the last sample left it`() {
    val driver = ShakeDriver(listOf(ShakeSample(0, Vector3.Zero, Vector3(1.0, 0.0, 0.0))))
    driver.advance(0)
    val turned = driver.gravity
    repeat(20) { driver.advance(it + 1) }

    assertEquals("a phone that stops being sampled has not been put down", turned, driver.gravity)
  }

  private fun sample(
    step: Int,
    x: Double,
  ): ShakeSample =
    ShakeSample(
      stepIndex = step,
      accelerationMmPerSecond2 = Vector3(x, 0.0, 0.0),
      gravity = Vector3(0.0, 0.0, -1.0),
    )
}
