package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.fixtures.StandardDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The range a half-read throw can still come to, which is what the roll screen
 * shows beside the running total while dice are still moving.
 *
 * Two things are worth holding here, and they are different claims. One is
 * that the numbers are *right* — the floor and the ceiling are what the dice
 * left in the air can actually reach, modifiers and all. The other is that
 * they are **achievable**: a range is a promise that the final total lands
 * inside it, and a floor nobody could roll is a promise about nothing. The
 * brute-force tests below are the ones that hold the second claim, by rolling
 * every outcome of a small formula and asking the bounds to agree with it.
 */
class RollRangeTest {
  private val catalog = DiceCatalog.of(listOf(StandardDice.set()))

  @Test
  fun `a throw with nothing on the table yet can be anything the dice can be`() {
    assertEquals(RollRange(3L, 18L), bounds("3d6"))
  }

  @Test
  fun `a throw that has been read to the last die has nothing left to say`() {
    val landed = bounds("3d6 + 4", values = listOf(2, 3, 5))

    assertEquals(RollRange(14L, 14L), landed)
    assertEquals(total("3d6 + 4", listOf(2, 3, 5)), landed.lowest, "the range closed on the actual total")
  }

  @Test
  fun `the dice already read are held to the faces they landed on`() {
    // One die down on a five, two still rolling: the five is a five in both
    // directions, and only the two that nobody has seen move.
    assertEquals(RollRange(7L, 17L), bounds("3d6", values = listOf(5)))
  }

  @Test
  fun `the range closes as the dice are counted off`() {
    // What the player actually watches. Each die read narrows it, and it never
    // widens — a readout that grew would be saying the roll had got less
    // decided than it was a moment ago.
    val steps =
      listOf(
        bounds("3d6"),
        bounds("3d6", values = listOf(4)),
        bounds("3d6", values = listOf(4, 4)),
        bounds("3d6", values = listOf(4, 4, 4)),
      )

    assertEquals(listOf(RollRange(3L, 18L), RollRange(6L, 16L), RollRange(9L, 14L), RollRange(12L, 12L)), steps)
  }

  @Test
  fun `dropping the lowest lifts the floor rather than the ceiling`() {
    assertEquals(RollRange(3L, 18L), bounds("4d6dl1"))
    // Two sixes down, two dice still going: the worst left is a pair of ones,
    // one of which is then dropped.
    assertEquals(RollRange(13L, 18L), bounds("4d6dl1", values = listOf(6, 6)))
  }

  @Test
  fun `keeping the highest of two makes a die already read into a floor`() {
    // Advantage: the fifteen on the table cannot be taken away, so it is the
    // least the roll can come to and the d20 still rolling is the ceiling.
    assertEquals(RollRange(15L, 20L), bounds("2d20kh1", values = listOf(15)))
    assertEquals(RollRange(1L, 20L), bounds("2d20kh1"))
  }

  @Test
  fun `keeping the lowest of two makes the same die into a ceiling`() {
    // Disadvantage is the mirror of it, and the reason the two evaluator runs
    // cannot simply be "all low" and "all high" at the level of the total.
    assertEquals(RollRange(1L, 15L), bounds("2d20kl1", values = listOf(15)))
  }

  @Test
  fun `arithmetic around a group moves both ends of the range with it`() {
    // `(3 + 5) / 2` and `(18 + 5) / 2`, rounded down as the default rounding
    // says (`docs/dice-notation.md`, "Division rounding").
    assertEquals(RollRange(4L, 11L), bounds("(3d6 + 5) / 2"))
  }

  @Test
  fun `the rounding the throw is scored under is the rounding the range uses`() {
    assertEquals(RollRange(1L, 9L), bounds("3d6 / 2", rounding = Rounding.Down))
    assertEquals(RollRange(2L, 9L), bounds("3d6 / 2", rounding = Rounding.Up))
  }

  @Test
  fun `subtracting a group turns its range over`() {
    // The lowest total of `20 - 1d6` comes from the *highest* d6, which is why
    // the arithmetic is walked as an interval instead of being read off two
    // whole-formula totals.
    assertEquals(RollRange(14L, 19L), bounds("20 - 1d6"))
    assertEquals(RollRange(-2L, 11L), bounds("2d6 + -1d4"))
  }

  @Test
  fun `a product takes its ends from the corners, not from the matching ends`() {
    // A fudge die runs from −1 to +1, so the smallest product of `1dF * 1d6`
    // pairs the *lowest* fudge with the *highest* d6.
    assertEquals(RollRange(-6L, 6L), bounds("1dF * 1d6"))
  }

  @Test
  fun `an exploding group counts its re-rolls at their lowest, and says it can go higher`() {
    // Eight sixes, and the eight throws they earn coming up one apiece.
    // Letting the forced chains run instead gave `8 to 1008` — twenty-one sixes
    // each, attainable and useless (`docs/dice-notation.md`).
    assertEquals(RollRange(8L, 56L, more = true), bounds("8d6!"))
  }

  @Test
  fun `three exploding sixes read three to twenty-one`() {
    // Three sixes is eighteen, and the three throws they earn are worth one
    // each at worst. The `+` is what says they could be sixes as well.
    assertEquals(RollRange(3L, 21L, more = true), bounds("3d6!"))
  }

  @Test
  fun `a group that cannot explode never says there is more`() {
    // The mark has to mean something. A formula with no `!` in it has a real
    // ceiling, and marking it would be telling the player to expect a number
    // that cannot come.
    assertEquals(RollRange(4L, 24L), bounds("4d6"))
    assertEquals(false, bounds("4d6dl1").more)
  }

  @Test
  fun `a six already on the table has earned a die, and the range says so`() {
    // `RunningScore` is asking for one more d6 at this point. The range does
    // not guess what it will be.
    assertIs<Scoring.OneMoreDie>(scoring("1d6!", values = listOf(6)))
    // Six on the table and a throw earned but not made. Both ends stop at the
    // dice in play, which is why they meet, and the `+` is what says the roll
    // is not over.
    assertEquals(RollRange(7L, 7L, more = true), bounds("1d6!", values = listOf(6)))
  }

  @Test
  fun `the dice an explosion has already thrown are read, not re-imagined`() {
    // Two sixes down the same chain: both stand, and only the die that has not
    // landed is still a question.
    assertEquals(RollRange(13L, 13L, more = true), bounds("1d6!", values = listOf(6), added = listOf(6)))
  }

  @Test
  fun `a chain with no table left under it has nothing left to add`() {
    // The tray is the other end of a chain, and an honest one: a die that
    // cannot be dropped anywhere is a die that is not coming
    // (`docs/tables.md`, "Capacity rule").
    assertEquals(RollRange(6L, 6L), bounds("1d6!", values = listOf(6), room = { false }))
  }

  @Test
  fun `a minimum raises the floor and leaves the ceiling where it was`() {
    assertEquals(RollRange(4L, 6L), bounds("1d6min4"))
  }

  @Test
  fun `a reroll cannot rescue the floor, because the replacement stands`() {
    // `r n` throws once more and the second die stands however low it is, so
    // the worst `1d6r3` can do is still a one.
    assertEquals(RollRange(1L, 6L), bounds("1d6r3"))
  }

  @Test
  fun `a percentile pair is bounded as a pair, not as two dice`() {
    // `00` and `0` are the two lowest faces in the set and they read as 100,
    // so pushing both halves down would hand back the highest result there is
    // (`docs/dice-notation.md`, "d100 and d%").
    assertEquals(RollRange(1L, 100L), bounds("1d100"))
  }

  @Test
  fun `a percentile half already read decides what its partner should show`() {
    // Tens on 90: the units die can only take it to 99. Tens on 00: the same
    // die can take it to 1 or to 100, and nothing in between is either end.
    assertEquals(RollRange(90L, 99L), bounds("1d100", values = listOf(90)))
    assertEquals(RollRange(1L, 100L), bounds("1d100", values = listOf(0)))
  }

  @Test
  fun `a hundred dice are bounded without being enumerated`() {
    // The point of scoring twice rather than counting outcomes: a full tray of
    // d6 has 6^100 of them and this has to answer between two frames.
    assertEquals(RollRange(100L, 600L), bounds("100d6"))
  }

  @Test
  fun `both ends of the range are totals the dice could really produce`() {
    // The claim a range makes is that the final total lands inside it, and the
    // only way to hold that is to roll every outcome and look.
    assertEquals(everyTotal("3d6dl1"), bounds("3d6dl1"))
    assertEquals(everyTotal("2d6kh1"), bounds("2d6kh1"))
    assertEquals(everyTotal("2d6 - 1d6"), bounds("2d6 - 1d6"))
    assertEquals(everyTotal("(2d6 + 3) / 2"), bounds("(2d6 + 3) / 2"))
  }

  @Test
  fun `both ends stay achievable once some of the dice have been read`() {
    assertEquals(everyTotal("3d6dl1", read = listOf(2)), bounds("3d6dl1", values = listOf(2)))
    assertEquals(everyTotal("3d6dl1", read = listOf(6, 1)), bounds("3d6dl1", values = listOf(6, 1)))
  }

  /**
   * The range of `text` worked out the slow, obviously-correct way: every face
   * every unread die could show, scored, and the smallest and largest kept.
   *
   * Only for formulas small enough to walk — a handful of d6 — and only for
   * ones that add no dice to themselves, since an explosion has no finite set
   * of outcomes to walk.
   */
  private fun everyTotal(
    text: String,
    read: List<Int> = emptyList(),
  ): RollRange {
    val plan = plan(text)
    val formula = parsed(text)
    val totals =
      plan.dice.indices
        .fold(listOf(emptyList<Int>())) { rows, position ->
          val column = read.getOrNull(position)?.let { listOf(it) } ?: plan.dice[position].die.values()
          rows.flatMap { row -> column.map { row + it } }
        }.map { values -> RollEvaluator.score(formula, plan, ThrowOutcome(faces = faces(plan, values))).total }
    return RollRange(totals.min(), totals.max())
  }

  private fun bounds(
    text: String,
    values: List<Int> = emptyList(),
    added: List<Int> = emptyList(),
    rounding: Rounding = Rounding.Default,
    room: (Die) -> Boolean = { true },
  ): RollRange {
    val plan = plan(text)
    return RollBounds.of(
      formula = parsed(text),
      plan = plan,
      outcome = ThrowOutcome(faces = faces(plan, values)),
      rounding = rounding,
      added =
        AddedDice(
          faces = added.map { value -> faceShowing(plan.dice.first().die, value) },
          room = room,
        ),
    )
  }

  private fun total(
    text: String,
    values: List<Int>,
  ): Long {
    val plan = plan(text)
    return RollEvaluator.score(parsed(text), plan, ThrowOutcome(faces = faces(plan, values))).total
  }

  private fun scoring(
    text: String,
    values: List<Int>,
  ): Scoring {
    val plan = plan(text)
    return RunningScore.of(parsed(text), plan, ThrowOutcome(faces = faces(plan, values)))
  }

  /** [values] as face indices, leaving the dice it does not reach unread. */
  private fun faces(
    plan: RollPlan,
    values: List<Int>,
  ): Map<Int, Int> =
    plan.dice
      .mapIndexedNotNull { position, instance ->
        values.getOrNull(position)?.let { position to faceShowing(instance.die, it) }
      }.toMap()

  private fun parsed(text: String): Formula = (FormulaParser.parse(text) as ParseResult.Parsed).formula

  private fun plan(text: String): RollPlan = (RollPlanner.plan(text, catalog) as PlanResult.Planned).plan

  private fun faceShowing(
    die: Die,
    value: Int,
  ): Int =
    die.faces.indexOfFirst { it.value == value }.also {
      require(it >= 0) { "${die.id} has no face showing $value" }
    }
}
