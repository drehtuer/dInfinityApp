package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.DieSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the statistics screen's state says about itself, without a database.
 *
 * Three things a list with nothing in it can mean — nothing has arrived, nothing
 * has ever been thrown, and a filter is hiding it all — and the screen says
 * something different for each (`docs/statistics.md`).
 */
class StatsStateTest {
  private val d6 = row("builtin", lastRolled = 1_000)
  private val brass = row("brass", lastRolled = 2_000)

  @Test
  fun `nothing having arrived is neither an empty history nor an empty filter`() {
    assertFalse(StatsState().empty)
    assertFalse(StatsState().filteredToNothing)
    assertFalse(
      "an answer still on its way was called a filter that found nothing",
      StatsState(allTime = listOf(d6), setFilter = "gone").filteredToNothing,
    )
  }

  @Test
  fun `nothing ever thrown is an empty history, and no filter is to blame`() {
    val state = StatsState(loaded = true, setFilter = "brass")

    assertTrue(state.empty)
    assertFalse(state.filteredToNothing)
  }

  @Test
  fun `a list with dice in it is neither`() {
    val state = StatsState(all = listOf(d6), allTime = listOf(d6), loaded = true)

    assertFalse(state.empty)
    assertFalse(state.filteredToNothing)
  }

  @Test
  fun `a roll-up was last thrown when the most recent of its dice was, whichever comes first`() {
    fun lastRolled(vararg rows: DieRow) =
      StatsState(all = rows.toList(), acrossSets = true)
        .dice
        .single()
        .summary.lastRolledAtEpochMs

    assertEquals(2_000L, lastRolled(d6, brass))
    assertEquals(2_000L, lastRolled(brass, d6))
  }

  @Test
  fun `a pool with one die whose set has gone says its fair line is a guess`() {
    // Its throws are in the bars and its faces are missing from the line.
    fun pooled(vararg rows: DieRow) =
      DieDetail(
        row =
          StatsState(all = rows.toList(), acrossSets = true)
            .dice
            .single(),
      )

    assertFalse(pooled(d6, brass).fairLineIsAGuess)
    assertTrue(pooled(d6, brass.copy(installed = false)).fairLineIsAGuess)
  }

  private fun row(
    setId: String,
    lastRolled: Long,
  ) = DieRow(
    summary = DieSummary(setId = setId, dieId = "d6", sides = 6, throws = 1, sum = 3, lastRolledAtEpochMs = lastRolled),
    name = "d6",
  )
}
