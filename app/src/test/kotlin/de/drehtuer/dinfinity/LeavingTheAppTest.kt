package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.navigation.LeavingTheApp
import de.drehtuer.dinfinity.navigation.LeavingTheApp.Answer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two presses to leave, and the second one has two seconds to arrive
 * (`docs/architecture.md`, "Navigation").
 *
 * A plain JVM test with no Robolectric and no waiting: the rule takes the time
 * as an argument, so the press that arrives a millisecond too late is a number
 * here rather than a two-second sleep in a suite that runs on every commit.
 * That is the whole reason the rule is not a timer inside a composable.
 */
class LeavingTheAppTest {
  @Test
  fun `the first press arms rather than leaving`() {
    val leaving = LeavingTheApp()

    assertEquals(Answer.Arm, leaving.pressed(now = 0))
    assertTrue("the first press should have armed", leaving.armed)
  }

  @Test
  fun `a second press inside the window leaves`() {
    val leaving = LeavingTheApp()
    leaving.pressed(now = 0)

    assertEquals(Answer.Leave, leaving.pressed(now = 1_999))
  }

  @Test
  fun `a press on the far edge of the window arms again`() {
    // The window is the two seconds *after* the first press, and a boundary
    // that closes the app is the wrong one to guess at.
    val leaving = LeavingTheApp()
    leaving.pressed(now = 0)

    assertEquals(Answer.Arm, leaving.pressed(now = 2_000))
  }

  @Test
  fun `a stray press a minute later cannot close the app`() {
    // The bug the window exists for: an edge swipe on a phone in a pocket,
    // long after the words have gone.
    val leaving = LeavingTheApp()
    leaving.pressed(now = 0)

    assertEquals(Answer.Arm, leaving.pressed(now = 60_000))
    assertEquals("and the one after it still leaves", Answer.Leave, leaving.pressed(now = 60_500))
  }

  @Test
  fun `leaving disarms, so the next press starts again`() {
    val leaving = LeavingTheApp()
    leaving.pressed(now = 0)
    leaving.pressed(now = 500)

    assertFalse("leaving should have disarmed", leaving.armed)
    assertEquals(Answer.Arm, leaving.pressed(now = 600))
  }

  @Test
  fun `the words going away take the arming with them`() {
    // The toast keeps its own clock, and when it takes itself off the screen
    // it says so. Otherwise the rule would still be armed with nothing on
    // screen saying it was.
    val leaving = LeavingTheApp()
    leaving.pressed(now = 0)

    leaving.lapse()

    assertFalse(leaving.armed)
    assertEquals(Answer.Arm, leaving.pressed(now = 100))
  }

  @Test
  fun `the window is the design's two seconds`() {
    assertEquals(2_000L, LeavingTheApp.WINDOW_MILLIS)
  }

  @Test
  fun `a shorter window is honoured, so the number is not baked in`() {
    val leaving = LeavingTheApp(window = 50)
    leaving.pressed(now = 0)

    assertEquals(Answer.Leave, leaving.pressed(now = 49))
    leaving.pressed(now = 100)
    assertEquals(Answer.Arm, leaving.pressed(now = 150))
  }
}
