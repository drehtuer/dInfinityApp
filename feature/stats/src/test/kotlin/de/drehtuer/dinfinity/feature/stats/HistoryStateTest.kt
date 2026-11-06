package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.data.HistoryEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an empty history list means (`docs/statistics.md`).
 *
 * "You have never rolled anything" is wrong and discouraging in front of
 * somebody who has rolled hundreds of times and picked a quiet session, and
 * wrong again a frame before the database has answered.
 */
class HistoryStateTest {
  private val quiet = HistoryFilter.InSession("tuesday", "Tuesday")

  @Test
  fun `nothing having arrived is neither empty nor filtered, whatever the filter`() {
    listOf(HistoryFilter.Everything, quiet).forEach { filter ->
      val state = HistoryState(filter = filter)

      assertFalse("$filter called itself empty before an answer", state.empty)
      assertFalse("$filter called itself filtered before an answer", state.filteredToNothing)
    }
  }

  @Test
  fun `no rolls with everything showing is an empty history`() {
    val state = HistoryState(loaded = true)

    assertTrue(state.empty)
    assertFalse(state.filteredToNothing)
  }

  @Test
  fun `no rolls with a filter on is the filter's doing`() {
    val state = HistoryState(loaded = true, filter = quiet)

    assertFalse(state.empty)
    assertTrue(state.filteredToNothing)
  }

  @Test
  fun `rolls showing is neither, with a filter or without`() {
    listOf(HistoryFilter.Everything, quiet).forEach { filter ->
      val state = HistoryState(rolls = listOf(entry), loaded = true, filter = filter)

      assertFalse(state.empty)
      assertFalse(state.filteredToNothing)
    }
  }

  private val entry =
    HistoryEntry(
      id = 1,
      atEpochMs = 1,
      sessionId = "tuesday",
      savedRollId = null,
      groupId = null,
      formula = "1d6",
      total = 4,
      groups = emptyList(),
    )
}
