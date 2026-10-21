package de.drehtuer.dinfinity.simulation.jolt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The toss log's two decisions: whether it writes at all, and the median it writes. */
class TossLogTest {
  @Test
  fun `it writes nothing unless asked for`() {
    // The JVM's stand-in for `Log.isLoggable` says no, as a phone does until
    // `setprop log.tag.dinfinity.toss DEBUG`.
    assertNull(TossLog.current())
  }

  @Test
  fun `the median is the middle value, and nought for none`() {
    assertEquals(2.0, TossLog.medianOf(listOf(3.0, 1.0, 2.0)), 0.0)
    assertEquals(3.0, TossLog.medianOf(listOf(4.0, 1.0, 3.0, 2.0)), 0.0)
    assertEquals(0.0, TossLog.medianOf(emptyList()), 0.0)
  }
}
