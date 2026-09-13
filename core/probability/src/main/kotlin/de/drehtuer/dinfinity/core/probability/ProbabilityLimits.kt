package de.drehtuer.dinfinity.core.probability

/**
 * What the outcome graph will and will not compute (`docs/probability.md`).
 *
 * The graph is exact, and exactness has a price: a distribution is an array
 * with an entry per reachable total, and some legal formulas reach a great
 * many. `1000d20` is twenty thousand entries and perfectly fine; a set is free
 * to define a d20 with faces up to 9,999, and a thousand of those would be ten
 * million.
 *
 * So the graph says "too large" rather than either lying with an
 * approximation or filling the phone's memory with a set file's arithmetic.
 * A formula this refuses can still be rolled, if it fits on the table; the two
 * limits have nothing to do with each other.
 */
object ProbabilityLimits {
  /** Entries in one distribution: eight megabytes of doubles at the very most. */
  const val MAX_SUPPORT: Int = 1_000_000

  /**
   * Multiply-adds one step may cost.
   *
   * It is the order-statistics dynamic programming this really bounds: keeping
   * half of two hundred dice is exact and also thousands of times more work
   * than keeping one of two.
   */
  const val MAX_WORK: Long = 200_000_000L

  /**
   * How far an exploding chain is followed, matching the notation's own limit
   * so the graph and the roll agree about what `8d6!` means.
   */
  const val EXPLOSION_DEPTH: Int = 20
}

/** [size] as an array length, refusing one past [ProbabilityLimits.MAX_SUPPORT]. */
internal fun checkedSize(size: Long): Int {
  if (size > ProbabilityLimits.MAX_SUPPORT) {
    throw TooLargeToGraph("this formula reaches $size different totals, more than the graph computes exactly")
  }
  return size.toInt()
}

/** Refuses a step that would cost more than [ProbabilityLimits.MAX_WORK] multiply-adds. */
internal fun checkWork(
  work: Long,
  what: String,
) {
  if (work > ProbabilityLimits.MAX_WORK) throw TooLargeToGraph("$what would take too long to work out exactly")
}

/** Raised inside the module when a formula is past [ProbabilityLimits]; never escapes it. */
internal class TooLargeToGraph(
  val reason: String,
) : RuntimeException(reason, null, false, false)
