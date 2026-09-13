package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.notation.ExtraThrow
import de.drehtuer.dinfinity.core.notation.Formula
import de.drehtuer.dinfinity.core.notation.RollEvaluator
import de.drehtuer.dinfinity.core.notation.ThrowOutcome

/**
 * The distribution of a formula, obtained by rolling it every possible way.
 *
 * This is the golden reference `docs/probability.md` asks for, and it is
 * deliberately built out of [RollEvaluator] rather than out of a second piece
 * of probability theory. A second derivation could agree with the first and
 * both be wrong about what the app actually does; this cannot. It walks every
 * combination of faces the dice could land on, scores each one with the code
 * that scores a real roll, and adds up. If the graph and this disagree, the
 * chart is telling the player something the dice will not do.
 *
 * It is exponential, so it is only ever pointed at small formulas — which is
 * fine, because a bug in a convolution shows up in `3d6` just as well as in
 * `300d6`.
 */
internal object BruteForce {
  /** Beyond this many leaves a case is not worth enumerating; ask for a smaller one. */
  const val MAX_LEAVES = 4_000_000L

  /** The exact distribution of [formula], as a [Pmf]. */
  fun distributionOf(
    formula: Formula,
    plan: RollPlan,
    rounding: Rounding = Rounding.Default,
  ): Pmf {
    val tally = mutableMapOf<Long, Double>()
    var leaves = 0L
    val initial = plan.dice.map { it.die.faces.size }

    fun visit(
      faces: List<Int>,
      extras: List<Int>,
      weight: Double,
    ) {
      leaves++
      check(leaves <= MAX_LEAVES) { "'${formula.text}' is too big to enumerate; use a smaller case" }
      val drawn = ArrayDeque(extras)
      var wanted: Die? = null
      val outcome =
        ThrowOutcome(faces = plan.dice.indices.associateWith { faces[it] })
      val total =
        runCatching {
          RollEvaluator
            .score(
              formula = formula,
              plan = plan,
              outcome = outcome,
              rounding = rounding,
              extra =
                ExtraThrow { die ->
                  drawn.removeFirstOrNull() ?: run {
                    wanted = die
                    throw NeedsAnotherThrow
                  }
                },
            ).total
        }
      val needed = wanted
      if (needed != null) {
        val share = weight / needed.faces.size
        needed.faces.indices.forEach { face -> visit(faces, extras + face, share) }
        return
      }
      tally.merge(total.getOrThrow(), weight) { a, b -> a + b }
    }

    fun spread(
      index: Int,
      faces: List<Int>,
      weight: Double,
    ) {
      if (index == initial.size) {
        visit(faces, emptyList(), weight)
        return
      }
      val share = weight / initial[index]
      repeat(initial[index]) { face -> spread(index + 1, faces + face, share) }
    }

    spread(0, emptyList(), 1.0)
    val low = tally.keys.min().toInt()
    val weights = DoubleArray(tally.keys.max().toInt() - low + 1)
    tally.forEach { (value, probability) -> weights[value.toInt() - low] = probability }
    return Pmf.of(low, weights)
  }

  /** Thrown when the enumeration has run out of prepared throws and has to branch. */
  private val NeedsAnotherThrow = NeedsAnotherThrowException()

  /** Carries no stack trace: it is thrown once per branch of an exponential walk. */
  private class NeedsAnotherThrowException : RuntimeException("needs another throw", null, false, false)
}
