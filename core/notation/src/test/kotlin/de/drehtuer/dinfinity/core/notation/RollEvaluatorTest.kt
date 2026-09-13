package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollEvaluatorTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `the total is the dice plus the modifiers`() {
    val result = score("3d6 + 1d20 - 4", values = listOf(2, 3, 5, 15))
    assertEquals(21L, result.total)
    assertEquals(listOf(10L, 15L), result.groups.map { it.subtotal })
  }

  @Test
  fun `a group's dice are listed with the faces they landed on`() {
    val result = score("3d6", values = listOf(2, 3, 5))
    assertEquals(listOf(2, 3, 5), result.dice.map(RolledDie::value))
    assertEquals(listOf("2", "3", "5"), result.dice.map(RolledDie::label))
  }

  @Test
  fun `a natural twenty is marked, and so is a natural one`() {
    val result = score("1d20", values = listOf(20))
    assertTrue(result.hasNaturalMax)
    assertTrue(result.dice.single().naturalMax)
    assertFalse(result.dice.single().naturalMin)
    assertTrue(score("1d20", values = listOf(1)).dice.single().naturalMin)
  }

  @Test
  fun `kh1 keeps the higher and strikes the other through`() {
    val result = score("2d20kh1 + 6", values = listOf(7, 18))
    assertEquals(24L, result.total)
    assertEquals(listOf(false, true), result.dice.map(RolledDie::kept))
    assertTrue(DieNote.Dropped in result.dice.first().notes)
  }

  @Test
  fun `kl1 keeps the lower`() {
    assertEquals(7L, score("2d20kl1", values = listOf(7, 18)).total)
  }

  @Test
  fun `dl1 drops the lowest, which is how a stat is rolled`() {
    val result = score("4d6dl1", values = listOf(1, 4, 5, 6))
    assertEquals(15L, result.total)
    assertEquals(listOf(1), result.dice.filterNot(RolledDie::kept).map(RolledDie::value))
  }

  @Test
  fun `dh1 drops the highest`() {
    assertEquals(10L, score("4d6dh1", values = listOf(1, 4, 5, 6)).total)
  }

  @Test
  fun `a tie between dice drops only as many as it was told to`() {
    val result = score("4d6dl1", values = listOf(3, 3, 3, 3))
    assertEquals(9L, result.total)
    assertEquals(1, result.dice.count { !it.kept })
  }

  @Test
  fun `an explosion adds a die of the same kind, and it counts`() {
    val result = score("1d6!", values = listOf(6), extra = listOf(6, 2))
    assertEquals(14L, result.total)
    assertEquals(listOf(6, 6, 2), result.dice.map(RolledDie::value))
    assertTrue(DieNote.FromExplosion in result.dice.last().notes)
  }

  @Test
  fun `an explosion that never comes up stops immediately`() {
    assertEquals(3L, score("1d6!", values = listOf(3)).total)
  }

  @Test
  fun `an exploded die is thrown as a new die, with its own place in the throw`() {
    val result = score("1d6!", values = listOf(6), extra = listOf(2))
    assertEquals(listOf(0, 1), result.dice.map(RolledDie::instanceIndex))
  }

  @Test
  fun `an explosion stops at the documented depth and says so`() {
    val endless = List(NotationLimits.MAX_EXPLOSION_DEPTH + 5) { 6 }
    val result = score("1d6!", values = listOf(6), extra = endless)
    assertEquals(NotationLimits.MAX_EXPLOSION_DEPTH + 1, result.dice.size)
    assertTrue(DieNote.ExplosionLimitReached in result.dice.last().notes)
  }

  @Test
  fun `an exploding chain belongs to its own die, so kh1 keeps the better chain`() {
    // The first d6 explodes into 6 + 2 = 8; the second is a plain 5.
    val result = score("2d6!kh1", values = listOf(6, 5), extra = listOf(2))
    assertEquals(8L, result.total)
  }

  @Test
  fun `a reroll replaces a low die once, and both are shown`() {
    val result = score("4d6r1", values = listOf(1, 4, 5, 6), extra = listOf(3))
    assertEquals(18L, result.total)
    assertEquals(listOf(1, 3, 4, 5, 6), result.dice.map(RolledDie::value).sorted())
    val rerolled = result.dice.first { DieNote.Rerolled in it.notes && !it.kept }
    assertEquals(1, rerolled.value)
  }

  @Test
  fun `a reroll happens once, however low the replacement is`() {
    val result = score("1d6r1", values = listOf(1), extra = listOf(1))
    assertEquals(1L, result.total)
    assertEquals(2, result.dice.size)
  }

  @Test
  fun `min raises a die without moving it`() {
    val result = score("4d6min2", values = listOf(1, 1, 5, 6))
    assertEquals(15L, result.total)
    assertEquals(listOf(1, 1, 5, 6), result.dice.map(RolledDie::value))
    assertTrue(DieNote.ClampedToMin in result.dice.first().notes)
  }

  @Test
  fun `min leaves a die that is already high enough alone`() {
    val result = score("2d6min2", values = listOf(3, 4))
    assertEquals(7L, result.total)
    assertTrue(result.dice.none { DieNote.ClampedToMin in it.notes })
  }

  @Test
  fun `a percentile pair reads tens plus units`() {
    val result = score("1d100", values = listOf(30, 7))
    assertEquals(37L, result.total)
    assertEquals(listOf("30", "7"), result.dice.map(RolledDie::label))
  }

  @Test
  fun `double zero reads as one hundred`() {
    assertEquals(100L, score("1d%", values = listOf(0, 0)).total)
  }

  @Test
  fun `the lowest a percentile can read is one`() {
    assertEquals(1L, score("1d%", values = listOf(0, 1)).total)
  }

  @Test
  fun `both halves of a pair are marked in the breakdown`() {
    val result = score("1d%", values = listOf(30, 7))
    assertTrue(DieNote.PercentileTens in result.dice.first().notes)
    assertTrue(DieNote.PercentileUnits in result.dice.last().notes)
  }

  @Test
  fun `keep and drop work on whole percentile pairs`() {
    // Pairs read 41 and 92; kh1 keeps the second.
    val result = score("2d%kh1", values = listOf(40, 1, 90, 2))
    assertEquals(92L, result.total)
    assertEquals(2, result.dice.count(RolledDie::kept))
  }

  @Test
  fun `a fudge die sums its minuses and pluses`() {
    assertEquals(1L, score("4dF", values = listOf(-1, 0, 1, 1)).total)
  }

  @Test
  fun `division rounds down by default`() {
    // 1 + 2 + 3 + 5 = 11, halved and rounded down.
    assertEquals(5L, score("(3d6 + 5) / 2", values = listOf(1, 2, 3)).total)
  }

  @Test
  fun `the sheet can round the same dice the other way`() {
    val formula = parsed("(3d6 + 5) / 2 [Half]")
    val result = score("(3d6 + 5) / 2 [Half]", values = listOf(1, 2, 3))
    assertEquals(6L, RollEvaluator.rescore(formula, result, Rounding.Nearest).total)
    assertEquals(6L, RollEvaluator.rescore(formula, result, Rounding.Up).total)
    assertEquals(5L, RollEvaluator.rescore(formula, result, Rounding.Down).total)
  }

  @Test
  fun `rounding the sheet's way does not move a single die`() {
    val formula = parsed("(3d6 + 5) / 2")
    val result = score("(3d6 + 5) / 2", values = listOf(1, 2, 3))
    val again = RollEvaluator.rescore(formula, result, Rounding.Up)
    assertEquals(result.groups, again.groups)
    assertEquals(Rounding.Up, again.rounding)
  }

  @Test
  fun `a scored roll carries the rounding it was scored under`() {
    val result = score("1d6 / 2", values = listOf(5), rounding = Rounding.Up)
    assertEquals(Rounding.Up, result.rounding)
    assertEquals(3L, result.total)
  }

  @Test
  fun `the label and the formula travel to the result`() {
    val result = score("2d20kh1 + 6 [Attack]", values = listOf(7, 18))
    assertEquals("Attack", result.label)
    assertEquals("2d20kh1 + 6 [Attack]", result.formula)
  }

  @Test
  fun `a group names the set it came from`() {
    val result = score("1d20", values = listOf(11))
    assertEquals("builtin", result.groups.single().setId)
    assertFalse(result.groups.single().fellBack)
  }

  @Test
  fun `what the simulation reported about the throw is carried through`() {
    val outcome = ThrowOutcome(faces = mapOf(0 to 10), rethrows = 2, forcedSettles = 1, rolledAtEpochMs = 99L)
    val formula = parsed("1d20")
    val plan = plan("1d20")
    val result = RollEvaluator.score(formula, plan, outcome)
    assertEquals(2, result.rethrows)
    assertEquals(1, result.forcedSettles)
    assertEquals(99L, result.rolledAtEpochMs)
  }

  @Test
  fun `a formula with no dice still adds up`() {
    assertEquals(14L, score("2 * (3 + 4)", values = emptyList()).total)
  }

  @Test
  fun `a missing face from the simulation is a bug, and says which die`() {
    val failure =
      assertFailsWith<IllegalArgumentException> {
        RollEvaluator.score(parsed("2d6"), plan("2d6"), ThrowOutcome(faces = mapOf(0 to 0)))
      }
    assertTrue("1" in failure.message.orEmpty(), failure.message.orEmpty())
  }

  @Test
  fun `a formula that needs no extra throws never reaches for one`() {
    // The default ExtraThrow throws if called; a plain roll must not call it.
    assertEquals(8L, score("3d6", values = listOf(1, 2, 5)).total)
  }

  private fun plan(text: String): RollPlan = (RollPlanner.plan(parsed(text), catalog) as PlanResult.Planned).plan

  /**
   * Scores [text] with each die landing on the value given, in throw order.
   *
   * Tests say what the dice *showed*, not which face index they came to rest
   * on: a test about `4d6dl1` is about the 1 that was dropped, and having to
   * write `0` for it would make every case here a puzzle.
   */
  private fun score(
    text: String,
    values: List<Int>,
    extra: List<Int> = emptyList(),
    rounding: Rounding = Rounding.Default,
  ): RollResult {
    val plan = plan(text)
    val faces = plan.dice.mapIndexed { position, instance -> position to faceShowing(instance.die, values[position]) }
    return RollEvaluator.score(
      formula = parsed(text),
      plan = plan,
      outcome = ThrowOutcome(faces = faces.toMap()),
      rounding = rounding,
      extra = FakeThrows(ArrayDeque(extra)),
    )
  }

  /** Hands back a face showing each value a test lined up, in order. */
  private class FakeThrows(
    private val queue: ArrayDeque<Int>,
  ) : ExtraThrow {
    override fun roll(die: Die): Int {
      val value = requireNotNull(queue.removeFirstOrNull()) { "an extra ${die.id} the test did not line up" }
      return faceShowing(die, value)
    }
  }
}

/** The index of a face of [die] showing [value]; the test is wrong if there is none. */
private fun faceShowing(
  die: Die,
  value: Int,
): Int =
  die.faces.indexOfFirst { it.value == value }.also {
    require(it >= 0) { "${die.id} has no face showing $value" }
  }
