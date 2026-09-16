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
    // Every shape of the same size covers the same circle now: `size_mm` is
    // the die's width, so its bounding sphere is the same whatever solid is
    // inside it (`docs/dice-sets.md`, "Size").
    assertEquals(2.01, footprintCm2(StandardDice.d20), 0.01)
    assertEquals(2.01, footprintCm2(StandardDice.d6), 0.01)
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
  fun `thirty-eight d6 are the most that roll at full size`() {
    assertEquals(1.0, scaleFor(StandardDice.d6, 38), 1e-9)
    assertTrue(scaleFor(StandardDice.d6, 39) < 1.0)
  }

  @Test
  fun `sixty d6 shrink to four fifths`() {
    assertEquals(0.80, scaleFor(StandardDice.d6, 60), 0.005)
  }

  @Test
  fun `a hundred d6 — the engine's whole cap — still fit, well clear of the floor`() {
    // The floor rule no longer refuses anything the engine would take. Dice of
    // the right size are small enough that it would take about two hundred and
    // forty of them to shrink past the minimum, and the engine stops at a
    // hundred (`docs/tables.md`).
    val scale = scaleFor(StandardDice.d6, TableCapacity.MAX_DICE)

    assertEquals(0.62, scale, 0.005)
    assertTrue(scale > TableCapacity.MIN_SCALE, "a full tray is already at the floor")
  }

  @Test
  fun `a hundred and one d6 are refused, and the message says how many would fit`() {
    val verdict = TableCapacity.check(List(100) { StandardDice.d6 }, table, diceCount = 101)
    assertTrue(verdict is CapacityVerdict.Refused, "$verdict")
    assertEquals("101 dice don't fit on the table; up to 100 do", verdict.reason)
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
    // 8d6! could reach sixteen dice in the tray, which still fits at full size.
    val verdict = TableCapacity.check(planOf(StandardDice.d6, count = 8, explodes = true), table)
    assertTrue(verdict is CapacityVerdict.Fits, "$verdict")
    assertEquals(16, verdict.diceCount)
    assertEquals(1.0, verdict.scale, 0.01)
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

  @Test
  fun `the scale floor cannot refuse anything the engine would take`() {
    // Which constant actually stops a throw, measured rather than assumed
    // (`docs/TODO.md`, Step 4.1 and 5.3). `FLOOR_SHARE` shrinks and `MIN_SCALE`
    // refuses — and at the engine's cap the shrink is nowhere near the floor,
    // so the refusal a player meets is always the body count.
    val atTheCap = scaleFor(StandardDice.d6, TableCapacity.MAX_DICE)

    assertTrue(
      atTheCap > TableCapacity.MIN_SCALE,
      "a hundred d6 shrink to $atTheCap, which is past the ${TableCapacity.MIN_SCALE} floor",
    )
  }

  @Test
  fun `the scale floor would start refusing at about two hundred and forty dice`() {
    // The number itself, so that raising `MAX_DICE` is noticed rather than
    // quietly making `MIN_SCALE` live for the first time. It is the count at
    // which the dice's own circles need more than `FLOOR_SHARE` of the floor
    // even after shrinking as far as they are allowed to.
    val d6 = StandardDice.d6
    val floor = table.floorAreaMm2 * TableCapacity.FLOOR_SHARE
    val shrunk = TableCapacity.footprintMm2(d6) * TableCapacity.MIN_SCALE * TableCapacity.MIN_SCALE
    val wouldRefuseAbove = floor / shrunk

    assertEquals(
      EXPECTED_FLOOR_BITES_ABOVE,
      wouldRefuseAbove.toInt(),
      "the scale floor starts refusing at a different count than the plan records",
    )
    assertTrue(
      wouldRefuseAbove > TableCapacity.MAX_DICE,
      "the scale floor now bites below the engine's cap, so it is doing work the cap used to do",
    )
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

  private companion object {
    /**
     * The count above which `MIN_SCALE` starts refusing 16 mm d6, on the
     * reference table.
     *
     * Written down because it is more than twice the engine's cap of a
     * hundred, which is what makes the scale floor unreachable today — a
     * safety net for a larger table or a higher cap rather than a rule a
     * player ever meets.
     */
    const val EXPECTED_FLOOR_BITES_ABOVE = 241
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
