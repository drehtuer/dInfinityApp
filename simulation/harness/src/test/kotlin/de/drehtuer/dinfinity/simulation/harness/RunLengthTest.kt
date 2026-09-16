package de.drehtuer.dinfinity.simulation.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one question a soak and a counted run answer differently.
 *
 * The device's loop is the same either way and asks only [RunLength.keepGoing],
 * so every boundary of "when does a run stop" is pinned here rather than
 * discovered after five minutes of rolling (`docs/architecture.md`, decision
 * 53).
 */
class RunLengthTest {
  @Test
  fun `a counted run stops when it has made the throws it was asked for`() {
    val length = RunLength.Rolls(3)

    assertTrue(length.keepGoing(made = 1, elapsedSeconds = 0.0))
    assertTrue(length.keepGoing(made = 2, elapsedSeconds = 10_000.0))
    assertFalse(length.keepGoing(made = 3, elapsedSeconds = 0.0))
  }

  @Test
  fun `a soak stops when the time is up, however many throws that took`() {
    val length = RunLength.Soak(seconds = 60.0)

    assertTrue(length.keepGoing(made = 1, elapsedSeconds = 59.9))
    assertFalse(length.keepGoing(made = 10_000, elapsedSeconds = 60.0))
  }

  @Test
  fun `a soak too short for one throw still makes one, because a run of none passes everything`() {
    // A scorecard over no rolls has met every bar and missed none, so a soak
    // whose time ran out during its first throw must still produce that throw.
    assertTrue(RunLength.Soak(seconds = 0.001).keepGoing(made = 0, elapsedSeconds = 30.0))
  }

  @Test
  fun `a run of no rolls, or of no time, is refused where it is asked for`() {
    assertFailsWith<IllegalArgumentException> { RunLength.Rolls(0) }
    assertFailsWith<IllegalArgumentException> { RunLength.Rolls(-1) }
    assertFailsWith<IllegalArgumentException> { RunLength.Soak(0.0) }
    assertFailsWith<IllegalArgumentException> { RunLength.Soak(-30.0) }
  }

  @Test
  fun `both kinds say what they are, in the words the run is reported in`() {
    assertEquals("1000 rolls", RunLength.Rolls(1_000).described)
    assertEquals("300 s of rolling", RunLength.Soak(300.0).described)
  }

  @Test
  fun `a duration is written the way people write one`() {
    assertEquals(90.0, RunLength.secondsOf("90"))
    assertEquals(90.0, RunLength.secondsOf("90s"))
    assertEquals(300.0, RunLength.secondsOf("5m"))
    assertEquals(3_600.0, RunLength.secondsOf("1h"))
    assertEquals(150.0, RunLength.secondsOf(" 2.5M "))
  }

  @Test
  fun `text that is not a duration is no duration rather than a refusal`() {
    // "no soak was asked for" and "a soak was asked for badly" arrive at the
    // same argument, and only one of them should stop a run.
    assertNull(RunLength.secondsOf("soon"))
    assertNull(RunLength.secondsOf(""))
    assertNull(RunLength.secondsOf("m"))
    assertNull(RunLength.secondsOf("0"))
    assertNull(RunLength.secondsOf("-5m"))
  }

  @Test
  fun `the two arguments come to one run, and a soak is the one that wins`() {
    assertEquals(RunLength.Rolls(10), RunLength.from(rolls = "10", soak = null))
    assertEquals(RunLength.Soak(120.0), RunLength.from(rolls = null, soak = "2m"))
    assertEquals(RunLength.Soak(120.0), RunLength.from(rolls = "10", soak = "2m"))
    assertEquals(RunLength.Rolls(10), RunLength.from(rolls = " 10 ", soak = "never"))
  }

  @Test
  fun `neither argument, or neither of them a run, is no run at all`() {
    assertNull(RunLength.from(rolls = null, soak = null))
    assertNull(RunLength.from(rolls = "0", soak = null))
    assertNull(RunLength.from(rolls = "lots", soak = null))
  }
}
