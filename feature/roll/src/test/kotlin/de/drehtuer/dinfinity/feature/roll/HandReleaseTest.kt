package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.simulation.api.ShakeSample
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dice are in the hand while the phone is shaken and thrown when it stops
 * (`docs/physics-and-rendering.md`, "Starting a roll"; decision 103).
 */
@RunWith(RobolectricTestRunner::class)
class HandReleaseTest {
  @Test
  fun `a shake takes the waiting dice off the table and throws nothing yet`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0, 1 to 0)))
    presenter.type("2d6")
    assertEquals(2, tray.boards.last().size)

    assertTrue(presenter.hold())

    assertTrue("the dice in the hand are still on the table", tray.boards.last().isEmpty())
    assertEquals(0, tray.throws)
  }

  @Test
  fun `the end of the shake throws them`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    presenter.type("1d20")
    presenter.hold()
    stroke().forEach(presenter::shaking)

    assertTrue(presenter.release())

    assertEquals(1, tray.throws)
  }

  @Test
  fun `no moment of a shake reaches the tray - the dice are in the hand or out of it`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    presenter.type("1d20")

    stroke().forEach(presenter::shaking)
    presenter.hold()
    stroke().forEach(presenter::shaking)
    presenter.release()
    stroke().forEach(presenter::shaking)

    assertTrue(tray.shaken.isEmpty())
  }

  @Test
  fun `a shake at dice still in the air holds nothing and throws nothing`() {
    val tray = CountingTray(read = emptyMap())
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    presenter.type("1d20")
    presenter.hold()
    presenter.release()

    assertFalse(presenter.hold())
    assertFalse(presenter.release())
  }

  @Test
  fun `a release with no shake before it throws nothing`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    presenter.type("1d20")

    assertFalse(presenter.release())
    assertEquals(0, tray.throws)
  }

  @Test
  fun `a shake with nothing to throw gives the table back`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(emptyMap()))
    presenter.type("3d6 +")
    presenter.hold()

    assertFalse(presenter.release())
    assertEquals(0, tray.throws)
  }

  @Test
  fun `leaving mid-shake puts the dice back on the board`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0, 1 to 0)))
    presenter.type("2d6")
    presenter.hold()

    presenter.drop()

    assertEquals(2, tray.boards.last().size)
    assertFalse("a dropped hand still threw", presenter.release())
  }

  @Test
  fun `dropping an empty hand changes nothing`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0)))
    presenter.type("1d6")
    val shown = tray.boards.size

    presenter.drop()

    assertEquals(shown, tray.boards.size)
  }

  @Test
  fun `the board stays off the table while the hand holds the dice, whatever is published`() {
    val tray = DirectTray()
    val presenter = rollPresenter(tray, LandingRolls(mapOf(0 to 0, 1 to 0, 2 to 0)))
    presenter.type("2d6")
    presenter.hold()

    presenter.type("3d6")

    assertTrue(tray.boards.last().isEmpty())
  }

  /** A hand moving along the tray and stopping, as a shake's moments. */
  private fun stroke(): List<ShakeSample> =
    List(STROKE_STEPS) { step ->
      ShakeSample(
        stepIndex = step,
        accelerationMmPerSecond2 = Vector3(if (step < STROKE_STEPS / 2) PUSH else -PUSH, 0.0, 0.0),
        gravity = Vector3(0.0, 0.0, -1.0),
      )
    }

  private companion object {
    const val STROKE_STEPS = 24
    const val PUSH = 9_000.0
  }
}
