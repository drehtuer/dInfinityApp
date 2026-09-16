package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieRole
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding

/**
 * The lowest and highest total a throw can still come to.
 *
 * Both ends are *achievable*: there is an assignment of faces to the dice
 * still in the air that produces each of them, so the pair is a range the
 * final total genuinely lands in and not a comfortable envelope around one.
 * The one place that is not quite true is the ceiling of an exploding group,
 * which [RollBounds] explains.
 */
data class RollRange(
  val lowest: Long,
  val highest: Long,
)

/**
 * What a throw that is half-read could still come to.
 *
 * The roll screen counts dice off as they settle, so for a second or two there
 * is a total made of the dice that have been read and a question mark over the
 * rest. This answers the question mark: the player watches the range close as
 * each die is taken off the table, and sees at a glance whether the roll can
 * still reach the number they need.
 *
 * **It scores, it does not enumerate.** `100d6` has more outcomes than there
 * are atoms nearby; what this does instead is run the *real* evaluator twice,
 * once with every die still in the air forced to the lowest face it could
 * show and once to the highest, and read the two group subtotals off the
 * results. So every rule about modifiers, chains and percentile pairs is
 * `GroupRoller`'s, with nothing restated here that could drift from it.
 *
 * Forcing works because a group's subtotal only ever *rises* when a die in it
 * rises: `kh`/`kl`/`dh`/`dl` pick from the chains, `min n` lifts a die, a
 * reroll replaces one — none of them can turn a better die into a worse
 * subtotal. So the forced runs land on the group's true extremes, and because
 * they are real assignments of real faces, both are totals the roll could
 * actually produce.
 *
 * **The arithmetic around the groups is walked as an interval**, rather than
 * taking the two totals as they come out of the evaluator, because a formula
 * can subtract: the lowest total of `20 - 1d6` comes from the *highest* d6.
 * Each `dice` node appears exactly once in a formula, so combining the group
 * ranges corner by corner keeps both ends achievable rather than merely safe.
 *
 * **Nothing here decides a number.** A die that has been read keeps the face
 * it was read on in both runs; only the dice nobody has seen yet are forced,
 * and what is forced is thrown away the moment the real face arrives
 * (`docs/architecture.md`, goal 1).
 *
 * ### What is exact and what is not
 *
 * - **Floor: exact.** The lowest faces set off no explosion, so the number is
 *   the total of a throw that could happen.
 * - **Ceiling with `!`: reachable, but only just.** A maximum face earns
 *   another die, and that die is forced to its maximum too, so a forced chain
 *   runs all the way to the explosion depth limit — `8d6!` tops out at 1008,
 *   eight chains of twenty-one sixes. That total *is* attainable, so the bound
 *   is never wrong, but it counts dice the roll has not earned yet and a
 *   player will never see the readout go near it.
 * - **Ceiling versus the tray: loose.** [AddedDice.room] is asked about each
 *   die a forced chain would add, but it answers about the tray as it is now
 *   and cannot know about the dice the chain before it would have dropped. A
 *   tray that fills up mid-chain ends the real roll sooner than this says
 *   (`docs/tables.md`, "Capacity rule"), which leaves the ceiling high rather
 *   than low.
 * - **Added dice are replayed by position**, exactly as `RunningScore` does.
 *   That lines up because a roll only earns extra dice once the throw before
 *   it has been read in full, so a throw with dice still in the air and extra
 *   dice already down cannot get its answers crossed.
 */
object RollBounds {
  /**
   * What [outcome] can still come to, given the dice it has read so far.
   *
   * @param outcome the faces read up to now. A die the simulation has not
   *   reported is a die still in the air, and is what gets forced.
   * @param added the faces of the extra dice this roll has already thrown, in
   *   the order the scoring asked for them — the same list [RunningScore] is
   *   given.
   */
  fun of(
    formula: Formula,
    plan: RollPlan,
    outcome: ThrowOutcome,
    rounding: Rounding = Rounding.Default,
    added: AddedDice = AddedDice(),
  ): RollRange {
    val throwing = ForcedScoring(formula, plan, outcome, rounding, added)
    val spans =
      Spans(
        lowest = throwing.subtotals(Extreme.Lowest),
        highest = throwing.subtotals(Extreme.Highest),
        rounding = rounding,
      )
    return spans.of(formula.root)
  }
}

/**
 * The throw as it stands, ready to be scored either way.
 *
 * The two runs differ in one thing only — which way the dice nobody has read
 * are pushed — so everything else about the throw is held here and the
 * difference is the argument.
 */
private class ForcedScoring(
  private val formula: Formula,
  private val plan: RollPlan,
  private val outcome: ThrowOutcome,
  private val rounding: Rounding,
  private val added: AddedDice,
) {
  /** What every group comes to with the unread dice pushed to one [extreme]. */
  fun subtotals(extreme: Extreme): Map<Int, Long> {
    val forcing = Forcing(plan, extreme)
    val forced = outcome.copy(faces = forcing.faces(outcome.faces))
    val result = RollEvaluator.score(formula, plan, forced, rounding, ForcedThrow(added, forcing))
    return result.groups.subtotals()
  }
}

/** Which way a die nobody has read yet is pushed. */
private enum class Extreme {
  Lowest,
  Highest,
}

/**
 * Walks the formula with every group replaced by the range its dice can still
 * cover.
 *
 * Plain `Long` arithmetic with no overflow checks, for the same reason
 * `RollEvaluator`'s own walk has none: `ResultBounds` proved at plan time that
 * every sub-expression fits, and it proved it with the explosion depth already
 * counted in, so the forced ceiling is inside the number it bounded. Division
 * is safe for the same reason — a divisor whose range could reach zero is a
 * parse error, and these ranges sit inside the ones that were checked.
 */
private class Spans(
  private val lowest: Map<Int, Long>,
  private val highest: Map<Int, Long>,
  private val rounding: Rounding,
) {
  fun of(node: FormulaNode): RollRange =
    when (node) {
      is NumberNode -> RollRange(node.value, node.value)
      // A minus turns the range over: the lowest of `-1d6` is the highest d6.
      is NegateNode -> of(node.operand).let { RollRange(-it.highest, -it.lowest) }
      is DiceNode -> RollRange(lowest.getValue(node.id), highest.getValue(node.id))
      is BinaryNode -> combine(node)
    }

  private fun combine(node: BinaryNode): RollRange {
    val left = of(node.left)
    val right = of(node.right)
    return when (node.operator) {
      BinaryOperator.Plus -> RollRange(left.lowest + right.lowest, left.highest + right.highest)
      BinaryOperator.Minus -> RollRange(left.lowest - right.highest, left.highest - right.lowest)
      BinaryOperator.Times -> corners(left, right) { a, b -> a * b }
      BinaryOperator.Divide -> corners(left, right) { a, b -> rounding.divide(a, b) }
    }
  }

  /**
   * Both ends of a product or a quotient, taken from the four corners.
   *
   * Either operation is monotone in each side once the other is held still —
   * and a divisor cannot change sign, since a range that straddled zero was
   * refused while the formula was still text — so whatever the extremes are,
   * they are at a corner of the box and not somewhere in the middle of it.
   */
  private fun corners(
    left: RollRange,
    right: RollRange,
    apply: (Long, Long) -> Long,
  ): RollRange {
    val values =
      listOf(
        apply(left.lowest, right.lowest),
        apply(left.lowest, right.highest),
        apply(left.highest, right.lowest),
        apply(left.highest, right.highest),
      )
    return RollRange(values.min(), values.max())
  }
}

/**
 * Picks the face an unread die is made to show.
 *
 * The choice is per **scoring unit** rather than per die, because a percentile
 * pair is not the sum of two dice pushed the same way: `00` and `0` read as
 * 100, so the two lowest faces in the set produce the highest result there is
 * (`docs/dice-notation.md`, "d100 and d%"). Both halves are therefore chosen
 * together, over every combination of their faces — a couple of hundred at
 * most, since no solid in the catalogue has more than twenty sides.
 *
 * Choosing over the combinations rather than reasoning about them also means a
 * set with odd face values gets the right answer without a special case: the
 * unit is scored, and the scoring is the same arithmetic `GroupRoller` does.
 */
private class Forcing(
  plan: RollPlan,
  private val extreme: Extreme,
) {
  /** Every scoring unit of the plan: one die, or the two halves of a pair. */
  private val units: List<List<DieInstance>> =
    plan.groups.flatMap { group ->
      group.dice.chunked(if (group.dice.firstOrNull()?.role != DieRole.Normal) PAIR else 1)
    }

  /**
   * The face each die shows when it is thrown as an extra.
   *
   * An explosion or a reroll asks for one die at a time, so a pair's two
   * halves arrive as two unrelated questions and cannot be weighed together
   * there. They are weighed here instead, where the pair is still a pair, and
   * the answer is looked up by die when the question comes.
   */
  private val extras: Map<Die, Int> =
    units
      .flatMap { unit ->
        val chosen = choose(unit, emptyMap())
        unit.mapIndexed { position, instance -> instance.die to chosen[position] }
      }.toMap()

  /** [known] filled out with a face for every die that has not been read. */
  fun faces(known: Map<Int, Int>): Map<Int, Int> =
    units
      .flatMap { unit ->
        val chosen = choose(unit, known)
        unit.mapIndexed { position, instance -> instance.index to chosen[position] }
      }.toMap()

  /**
   * The face [die] shows when the roll throws another one.
   *
   * Every extra die is a copy of one the plan already holds, so there is
   * always an answer; being asked for a die that is not in the plan is the
   * same kind of wrong as being handed a throw for a die nobody rolled, and
   * fails the same loud way rather than guessing a face.
   */
  fun faceOf(die: Die): Int = extras.getValue(die)

  /**
   * Which face each die of [unit] shows, leaving the ones [known] has read
   * exactly as they landed.
   *
   * A pair with one half already down is the reason this takes [known] rather
   * than working on whole units only: what the other half should show depends
   * on what the first one did, and a tens die reading 90 wants its partner at
   * 0 where a tens die reading 00 wants it at 1.
   */
  private fun choose(
    unit: List<DieInstance>,
    known: Map<Int, Int>,
  ): List<Int> {
    val candidates: List<Iterable<Int>> =
      unit.map { instance ->
        known[instance.index]?.let { listOf(it) } ?: instance.die.faces.indices
      }
    return combinations(candidates).extremeBy { score(unit, it) }
  }

  /** What one unit scores, the way `GroupRoller` scores it. */
  private fun score(
    unit: List<DieInstance>,
    faces: List<Int>,
  ): Int {
    val values = unit.mapIndexed { position, instance -> instance.die.valueAt(faces[position]) }
    if (unit.first().role == DieRole.Normal) return values.single()
    return values.sum().let { if (it == 0) PERCENTILE_MAX else it }
  }

  /** The end of [this] that [extreme] asks for, by [value]. */
  private fun <T> Iterable<T>.extremeBy(value: (T) -> Int): T =
    when (extreme) {
      Extreme.Lowest -> minBy(value)
      Extreme.Highest -> maxBy(value)
    }

  /** Every way of picking one entry from each of [candidates], in order. */
  private fun combinations(candidates: List<Iterable<Int>>): List<List<Int>> =
    candidates.fold(listOf(emptyList<Int>())) { rows, column ->
      rows.flatMap { row -> column.map { row + it } }
    }

  private companion object {
    /** A percentile pair is two dice read as one unit. */
    const val PAIR = 2

    /** A percentile pair reads 1 to 100; `00` and `0` together are the 100. */
    const val PERCENTILE_MAX = 100
  }
}

/**
 * Answers like `RunningScore`'s replay until the dice actually thrown run out,
 * then answers with the face [Forcing] chose.
 *
 * [roomForAnother] repeats the replay's rule rather than simplifying it: a die
 * that has already been thrown went into a tray that had room for it, whatever
 * the tray looks like now, and only a die that has not been thrown is a real
 * question.
 */
private class ForcedThrow(
  private val added: AddedDice,
  private val forcing: Forcing,
) : ExtraThrow {
  private var taken = 0

  override fun roll(die: Die): Int {
    if (taken < added.faces.size) return added.faces[taken++]
    return forcing.faceOf(die)
  }

  override fun roomForAnother(die: Die): Boolean = taken < added.faces.size || added.room(die)
}
