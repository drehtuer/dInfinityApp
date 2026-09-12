package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.PlannedGroup
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The capacity rule, against the worked table published in `docs/tables.md`.
 *
 * Those numbers are the specification: if one of them moves, the document
 * moves with it in the same pull request (`.claude/CLAUDE.md`). They also say
 * something a reader cares about — that eighty 16 mm d6 are exactly what a
 * phone-sized tray holds — which is not a thing to discover by accident later.
 */
class TableCapacityTest {
  private val table = TableGeometry.referenceDevice()

  @Test
  fun `the reference device's table is 240 by 108 millimetres`() {
    assertEquals(240.0, table.longSideMm, 1e-9)
    assertEquals(108.0, table.shortSideMm, 1e-9)
    assertEquals(25_920.0, table.floorAreaMm2, 1e-6)
  }

  @Test
  fun `a screen far outside what a phone is still gets a table dice can roll on`() {
    assertEquals(240.0 * 0.40, TableGeometry.forAspect(0.1).shortSideMm, 1e-9)
    assertEquals(240.0 * 0.75, TableGeometry.forAspect(2.0).shortSideMm, 1e-9)
  }

  @Test
  fun `the published footprints are what the rule computes`() {
    assertEquals(7.27, footprintCm2(StandardDice.d20), 0.01)
    assertEquals(6.03, footprintCm2(StandardDice.d6), 0.01)
  }

  @Test
  fun `one d20 rolls at full size`() {
    assertEquals(1.0, scaleFor(StandardDice.d20, 1), 1e-9)
  }

  @Test
  fun `eight d6 roll at full size`() {
    assertEquals(1.0, scaleFor(StandardDice.d6, 8), 1e-9)
  }

  @Test
  fun `twenty d6 shrink to four fifths`() {
    assertEquals(0.80, scaleFor(StandardDice.d6, 20), 0.005)
  }

  @Test
  fun `sixty d6 shrink to under a half`() {
    assertEquals(0.46, scaleFor(StandardDice.d6, 60), 0.005)
  }

  @Test
  fun `eighty d6 is exactly the limit`() {
    assertEquals(TableCapacity.MIN_SCALE, scaleFor(StandardDice.d6, 80), 0.005)
    assertTrue(scaleFor(StandardDice.d6, 80) >= TableCapacity.MIN_SCALE)
  }

  @Test
  fun `eighty-one d6 is one too many`() {
    val verdict = TableCapacity.check(List(81) { StandardDice.d6 }, table)
    assertTrue(verdict is CapacityVerdict.Refused, "$verdict")
    assertEquals(80, verdict.largestThatFits)
  }

  @Test
  fun `a hundred d6 are refused, and the message says how many would fit`() {
    val verdict = TableCapacity.check(List(100) { StandardDice.d6 }, table)
    assertTrue(verdict is CapacityVerdict.Refused, "$verdict")
    assertEquals("100 dice don't fit on the table; up to 80 do", verdict.reason)
  }

  @Test
  fun `five hundred d6 are refused`() {
    val verdict = TableCapacity.check(List(100) { StandardDice.d6 }, table, diceCount = 500)
    assertTrue(verdict is CapacityVerdict.Refused, "$verdict")
    assertEquals(500, verdict.diceCount)
  }

  @Test
  fun `the engine's own cap refuses more than a hundred dice however small they are`() {
    val tiny = StandardDice.d6.copy(material = StandardDice.d6.material.copy(sizeMm = 8.0))
    val verdict = TableCapacity.check(List(100) { tiny }, table, diceCount = 101)
    assertTrue(verdict is CapacityVerdict.Refused, "$verdict")
    assertEquals(TableCapacity.MAX_DICE, verdict.largestThatFits)
  }

  @Test
  fun `a roll of nothing needs no table`() {
    val verdict = TableCapacity.check(emptyList(), table)
    assertEquals(CapacityVerdict.Fits(scale = 1.0, diceCount = 0), verdict)
  }

  @Test
  fun `a plan is checked on the dice it actually puts on the table`() {
    val plan = planOf(StandardDice.d6, count = 8, explodes = false)
    assertEquals(CapacityVerdict.Fits(1.0, 8), TableCapacity.check(plan, table))
  }

  @Test
  fun `an exploding group is given room for the dice it could add`() {
    // 8d6! could reach sixteen dice in the tray, which still fits but shrinks.
    val verdict = TableCapacity.check(planOf(StandardDice.d6, count = 8, explodes = true), table)
    assertTrue(verdict is CapacityVerdict.Fits, "$verdict")
    assertEquals(16, verdict.diceCount)
    assertEquals(0.90, verdict.scale, 0.01)
  }

  @Test
  fun `a mixed throw is measured by what its dice really cover`() {
    val mixed = listOf(StandardDice.d20, StandardDice.d6, StandardDice.d4)
    val verdict = TableCapacity.check(mixed, table)
    assertTrue(verdict is CapacityVerdict.Fits, "$verdict")
    assertEquals(1.0, verdict.scale, 1e-9)
  }

  @Test
  fun `the published constants are these`() {
    assertEquals(0.30, TableCapacity.FLOOR_SHARE)
    assertEquals(0.40, TableCapacity.MIN_SCALE)
    assertEquals(100, TableCapacity.MAX_DICE)
  }

  @Test
  fun `the table's fixed dimensions are these`() {
    assertEquals(240.0, TableGeometry.LONG_SIDE_MM)
    assertEquals(60.0, TableGeometry.WALL_HEIGHT_MM)
    assertEquals(200.0, TableGeometry.CEILING_HEIGHT_MM)
    assertEquals(12.0, TableGeometry.CORNER_RADIUS_MM)
    assertEquals(0.40..0.75, TableGeometry.ASPECT_RANGE)
  }

  private fun footprintCm2(die: Die): Double = TableCapacity.footprintMm2(die) / 100.0

  private fun scaleFor(
    die: Die,
    count: Int,
  ): Double {
    val verdict = TableCapacity.check(List(count) { die }, table)
    return (verdict as? CapacityVerdict.Fits)?.scale
      ?: error("$count of ${die.id} was refused: $verdict")
  }

  private fun planOf(
    die: Die,
    count: Int,
    explodes: Boolean,
  ): RollPlan {
    val dice =
      List(count) { DieInstance(index = it, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die) }
    return RollPlan(
      formula = "${count}d${die.shape.faceCount}",
      groups = listOf(PlannedGroup(id = 0, notation = "x", dice = dice, explodes = explodes)),
    )
  }
}
