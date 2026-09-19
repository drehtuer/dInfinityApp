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

    driver.add(sample(step = 40, x = 5_000.0), atStep = 40)

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
  fun `the table stays horizontal however the phone is held`() {
    // The virtual table is horizontal, whatever the phone is doing. It was not
    // always: the gyroscope turned the world, and a vigorous shake could leave
    // down pointing at a wall for the rest of the roll — which is a chute, and
    // is what a hundred dice heaped into one corner looked like on the phone.
    val sideways = Vector3(1.0, 0.0, 0.0)
    val driver = ShakeDriver(listOf(ShakeSample(0, Vector3.Zero, sideways)))

    driver.advance(0)

    assertEquals("the tray was tipped on its side", ShakeDriver.DEFAULT_GRAVITY, driver.gravity)
  }

  @Test
  fun `whatever the gyroscope says, down is down`() {
    // Every direction a sensor could report, including the ones it should not.
    val directions =
      listOf(
        Vector3(1.0, 0.0, 0.0),
        Vector3(0.0, -1.0, 0.0),
        Vector3(0.0, 0.0, 1.0),
        Vector3.Zero,
      )

    directions.forEach { direction ->
      val driver = ShakeDriver(listOf(ShakeSample(0, Vector3.Zero, direction)))
      driver.advance(0)

      assertEquals("gravity followed $direction", ShakeDriver.DEFAULT_GRAVITY, driver.gravity)
    }
  }

  @Test
  fun `a shake still loads the dice sideways, on a table that stays flat`() {
    // Horizontal is not inert: the hand is what moves the dice, and it still
    // does. What it no longer does is tip the table under them.
    val driver = ShakeDriver(listOf(sample(step = 0, x = 10_000.0)))

    driver.advance(0)

    assertTrue("the hand stopped reaching the dice", driver.gravity.x < 0.0)
    assertEquals("the table was tilted after all", ShakeDriver.DEFAULT_GRAVITY.z, driver.gravity.z, 1e-6)
  }

  @Test
  fun `a tap-to-roll throw has no record, because there was no hand`() {
    assertEquals(emptyList<ShakeSample>(), ShakeDriver(emptyList()).recorded())
  }

  @Test
  fun `the record is what drove the roll, in step order`() {
    // A map has no order and a step is the only clock a sample has, so the
    // record is sorted by the thing that placed it rather than by the order the
    // sensors happened to fire.
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 7, x = 1_000.0), atStep = 7)
    driver.add(sample(step = 2, x = 2_000.0), atStep = 2)
    driver.add(sample(step = 11, x = 3_000.0), atStep = 11)

    assertEquals(listOf(2, 7, 11), driver.recorded().map(ShakeSample::stepIndex))
  }

  @Test
  fun `a step that got two readings is recorded once, as the one that drove it`() {
    // Sensors can outrun 120 Hz and a step has one gravity. What comes out of
    // the record has to be what went into the world, or a replay of it is a
    // different roll.
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 4, x = 1_000.0), atStep = 4)
    driver.add(sample(step = 4, x = 9_000.0), atStep = 4)

    assertEquals(listOf(sample(step = 4, x = 9_000.0)), driver.recorded())
  }

  @Test
  fun `a throw replayed from its own record keeps its samples`() {
    // What `spec.copy(shake = recorded())` has to survive: the record read back
    // off a driver built from it is the same record.
    val shake = listOf(sample(step = 0, x = 1_000.0), sample(step = 5, x = 2_000.0))

    assertEquals(shake, ShakeDriver(shake).recorded())
  }

  @Test
  fun `a hand that goes on shaking cannot grow the record without bound`() {
    // Thirty seconds of shaking against a roll that is force-settled at
    // twelve. Every moment past the cap names a step nothing will ever take.
    val driver = ShakeDriver(emptyList())

    repeat(THIRTY_SECONDS_OF_STEPS) { step -> driver.add(sample(step = step, x = 1_000.0), atStep = step) }

    assertEquals(ShakeSample.MAX_RECORDED, driver.recorded().size)
    assertEquals(ShakeSample.MAX_RECORDED - 1, driver.recorded().last().stepIndex)
  }

  @Test
  fun `a moment past the cap is not kept and does not hold the roll open`() {
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = ShakeSample.MAX_RECORDED, x = 10_000.0), atStep = ShakeSample.MAX_RECORDED)

    assertEquals(emptyList<ShakeSample>(), driver.recorded())
    assertTrue("a sample that drives nothing still counted as a shake", driver.isStill)
    assertFalse(
      "a step nothing will ever take held the roll open",
      driver.stillShaking(ShakeSample.MAX_RECORDED),
    )
  }

  @Test
  fun `a record handed in past the cap is trimmed on the way in`() {
    val shake = listOf(sample(step = 0, x = 1_000.0), sample(step = ShakeSample.MAX_RECORDED + 500, x = 1_000.0))

    assertEquals(listOf(0), ShakeDriver(shake).recorded().map(ShakeSample::stepIndex))
  }

  @Test
  fun `a live sample drives the step the world is about to take, not the one its own clock names`() {
    // The recorder counts steps in wall-clock milliseconds from the start of
    // the shake; the world counts the steps it has actually taken. Pacing a
    // watched roll pulls the two apart, and a sample left on the recorder's
    // number would reach the dice a beat late — or, once the world had gone
    // past it, never.
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 240, x = 5_000.0), atStep = 100)
    driver.advance(100)

    assertTrue("the hand did not reach the step it arrived for", driver.gravity.x < 0.0)
  }

  @Test
  fun `a sample whose own clock is behind the world is not filed in the past`() {
    // What shaking a phone at tumbling dice used to do: nothing at all.
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 3, x = 5_000.0), atStep = 200)

    driver.advance(3)
    assertEquals("a step already taken was driven", ShakeDriver.DEFAULT_GRAVITY, driver.gravity)
    driver.advance(200)
    assertTrue("the hand never reached the roll", driver.gravity.x < 0.0)
  }

  @Test
  fun `the record names the step that drove the roll, so it replays`() {
    // `spec.copy(shake = recorded())` has to reproduce the roll, and it cannot
    // if the record keeps the sensor's numbering while the world used another.
    val driver = ShakeDriver(emptyList())
    driver.add(sample(step = 240, x = 5_000.0), atStep = 100)

    val record = driver.recorded()
    assertEquals(listOf(100), record.map(ShakeSample::stepIndex))

    val replayed = ShakeDriver(record)
    repeat(REPLAY_STEPS) { step ->
      driver.advance(step)
      replayed.advance(step)
      assertEquals("the record did not replay the roll it drove", driver.gravity, replayed.gravity)
    }
  }

  @Test
  fun `how long the hand is still shaking is counted on the world's clock`() {
    // A roll may not end while this holds, so a number off the wrong clock
    // would hold a finished roll open for as long as the drift lasted.
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 1_000, x = 5_000.0), atStep = 20)

    assertTrue(driver.stillShaking(20 + ShakeDriver.HOLD_STEPS))
    assertFalse(
      "a sensor's numbering held the roll open long after the hand stopped",
      driver.stillShaking(20 + ShakeDriver.HOLD_STEPS + 1),
    )
  }

  @Test
  fun `a sample placed past the cap is dropped, however early its own clock said it was`() {
    val driver = ShakeDriver(emptyList())

    driver.add(sample(step = 0, x = 5_000.0), atStep = ShakeSample.MAX_RECORDED)

    assertEquals(emptyList<ShakeSample>(), driver.recorded())
    assertTrue("a sample that drives nothing still counted as a shake", driver.isStill)
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

  private companion object {
    /** Long enough to be past the roll's own cap several times over. */
    const val THIRTY_SECONDS_OF_STEPS = 3_600

    /** Far enough past the one sample in the record to cover it letting go. */
    const val REPLAY_STEPS = 140
  }
}
