package de.drehtuer.dinfinity.feature.stats

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * When a roll happened, in words.
 *
 * Split out of the screen and handed in as a function, so a test can say what
 * time it is. A history whose rows are asserted against whatever timezone the
 * machine running the tests happens to be in is a test that fails on somebody
 * else's laptop.
 */
object TimeStamp {
  private val format: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

  /** [atEpochMs] in the phone's own timezone and the reader's own locale. */
  fun of(
    atEpochMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
  ): String = format.format(Instant.ofEpochMilli(atEpochMs).atZone(zone))
}
