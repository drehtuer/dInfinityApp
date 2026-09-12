package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DieStatisticsTest {
  private val d20 = StandardDice.d20

  @Test
  fun `a die nobody has thrown has no average`() {
    val fresh = DieSummary(setId = "builtin", dieId = "d20", sides = 20)
    assertNull(fresh.mean)
    assertNull(fresh.variance)
    assertNull(fresh.standardDeviation)
    assertEquals(0, fresh.throws)
  }

  @Test
  fun `one throw has an average and no variance to speak of`() {
    val once = record(null, 14)
    assertEquals(1, once.throws)
    assertEquals(14.0, once.mean)
    assertNull(once.variance)
  }

  @Test
  fun `the average is the average`() {
    val summary = listOf(1, 2, 3, 4, 5).fold<Int, DieSummary?>(null) { acc, value -> record(acc, value) }!!
    assertEquals(5, summary.throws)
    assertEquals(3.0, summary.mean!!, 1e-12)
    assertEquals(2.5, summary.variance!!, 1e-12)
  }

  @Test
  fun `a natural twenty starts a streak, and another continues it`() {
    var summary = record(null, 20)
    assertEquals(1, summary.highestStreak)
    summary = record(summary, 20)
    assertEquals(2, summary.highestStreak)
    assertEquals(2, summary.highestStreakMax)
  }

  @Test
  fun `anything else breaks the streak, but not the record of it`() {
    var summary = record(null, 20)
    summary = record(summary, 20)
    summary = record(summary, 7)
    assertEquals(0, summary.highestStreak)
    assertEquals(2, summary.highestStreakMax)
  }

  @Test
  fun `natural ones have a streak of their own`() {
    var summary = record(null, 1)
    summary = record(summary, 1)
    summary = record(summary, 1)
    assertEquals(3, summary.lowestStreak)
    assertEquals(3, summary.lowestStreakMax)
    assertEquals(0, summary.highestStreak)
  }

  @Test
  fun `a high breaks a low streak and a low breaks a high one`() {
    var summary = record(null, 1)
    summary = record(summary, 20)
    assertEquals(0, summary.lowestStreak)
    assertEquals(1, summary.highestStreak)
    assertEquals(1, summary.lowestStreakMax)
  }

  @Test
  fun `a die whose highest and lowest are the same counts as high only`() {
    // A one-valued die would otherwise be on a natural high and a natural low
    // at the same time, and both streaks would be every throw it ever had.
    val flat = die("flat", DieShape.Coin, listOf(3, 3))
    val summary = DieStatistics.record(null, flat, rolled(3), atEpochMs = 1)
    assertEquals(1, summary.highestStreak)
    assertEquals(0, summary.lowestStreak)
  }

  @Test
  fun `a die counts by face value, so a d6 of 1,2,3,1,2,3 is a d3`() {
    val d3 = die("d3-as-d6", DieShape.Cube, listOf(1, 2, 3, 1, 2, 3))
    val high = DieStatistics.record(null, d3, rolled(3), atEpochMs = 1)
    assertEquals(1, high.highestStreak, "3 is this die's highest")
    val low = DieStatistics.record(null, d3, rolled(1), atEpochMs = 1)
    assertEquals(1, low.lowestStreak, "1 is this die's lowest")
  }

  @Test
  fun `the time of the last throw is kept`() {
    assertEquals(99L, DieStatistics.record(null, d20, rolled(5), atEpochMs = 99).lastRolledAtEpochMs)
  }

  @Test
  fun `a face tally counts every throw of that face`() {
    var tally = DieStatistics.record(null, d20, rolled(20))
    tally = DieStatistics.record(tally, d20, rolled(20))
    assertEquals(2, tally.count)
    assertEquals(0, tally.droppedCount)
    assertEquals(20, tally.faceValue)
  }

  @Test
  fun `a dropped die still counts, and is remembered as dropped`() {
    val dropped = rolled(1).copy(notes = setOf(DieNote.Dropped))
    val tally = DieStatistics.record(null, d20, dropped)
    assertEquals(1, tally.count)
    assertEquals(1, tally.droppedCount)
  }

  @Test
  fun `a dropped die counts towards the streaks too, because it was thrown`() {
    val dropped = rolled(20).copy(notes = setOf(DieNote.Dropped))
    assertEquals(1, DieStatistics.record(null, d20, dropped, atEpochMs = 1).highestStreak)
  }

  @Test
  fun `the fair share of a plain die is one over its faces`() {
    val expected = DieStatistics.expectedShare(StandardDice.d6)
    assertEquals(6, expected.size)
    expected.values.forEach { assertEquals(1.0 / 6, it, 1e-12) }
  }

  @Test
  fun `the fair share of a d6 of 1,2,3,1,2,3 is a third each`() {
    val expected = DieStatistics.expectedShare(die("d3-as-d6", DieShape.Cube, listOf(1, 2, 3, 1, 2, 3)))
    assertEquals(setOf(1, 2, 3), expected.keys)
    expected.values.forEach { assertEquals(1.0 / 3, it, 1e-12) }
  }

  @Test
  fun `the fair shares add to one`() {
    StandardDice.all.forEach { die ->
      assertTrue(kotlin.math.abs(DieStatistics.expectedShare(die).values.sum() - 1.0) < 1e-12, die.id)
    }
  }

  @Test
  fun `variance never goes negative, however the floating point falls`() {
    val summary = (1..100).fold<Int, DieSummary?>(null) { acc, _ -> record(acc, 7) }!!
    assertEquals(0.0, summary.variance!!, 1e-12)
    assertEquals(0.0, summary.standardDeviation!!, 1e-12)
  }

  private fun record(
    summary: DieSummary?,
    value: Int,
  ): DieSummary = DieStatistics.record(summary, d20, rolled(value), atEpochMs = value.toLong())

  private fun rolled(value: Int): RolledDie = RolledDie(instanceIndex = 0, dieId = "d20", value = value)

  private fun die(
    id: String,
    shape: DieShape,
    values: List<Int>,
  ): Die = Die(id = id, shape = shape, faces = values.mapIndexed { i, v -> Face.labelled(i, v) })
}
