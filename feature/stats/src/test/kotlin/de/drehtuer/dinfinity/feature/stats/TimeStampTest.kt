package de.drehtuer.dinfinity.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * When a roll happened, in words.
 *
 * The history screen is always handed a formatter in its tests, so the one the
 * app actually ships with — the default — is only run here.
 */
class TimeStampTest {
  @Test
  fun `a moment is written in the zone it is asked for`() {
    // Noon on the first of March 2026, in UTC.
    val written = TimeStamp.of(NOON_UTC, ZoneOffset.UTC)

    assertTrue("the year is missing: $written", written.contains("2026"))
  }

  @Test
  fun `the same moment reads differently on the other side of the date line`() {
    // The point of taking a zone at all: a throw at noon in London is the
    // small hours of the next day in Kiritimati, and the row should say so.
    val london = TimeStamp.of(NOON_UTC, ZoneOffset.UTC)
    val kiritimati = TimeStamp.of(NOON_UTC, ZoneId.of("Pacific/Kiritimati"))

    assertNotEquals(london, kiritimati)
  }

  @Test
  fun `the phone's own zone is what a caller that names none gets`() {
    assertEquals(TimeStamp.of(NOON_UTC, ZoneId.systemDefault()), TimeStamp.of(NOON_UTC))
  }

  private companion object {
    /** 2026-03-01T12:00:00Z. */
    const val NOON_UTC = 1_772_366_400_000L
  }
}
