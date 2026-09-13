package de.drehtuer.dinfinity.core.probability

import de.drehtuer.dinfinity.core.model.Rounding

/**
 * The arithmetic of `docs/probability.md`'s table: what `+`, `-`, `*` and `/`
 * do to a distribution.
 *
 * Adding is convolution and lives in [Convolution]. Everything here is either
 * a remap of the values — which never changes a probability, only which total
 * it belongs to — or, for two distributions multiplied or divided by each
 * other, an enumeration over both supports.
 */
internal object PmfArithmetic {
  /** The distribution of `-x`. */
  fun negate(pmf: Pmf): Pmf = remap(pmf) { -it.toLong() }

  /** The distribution of `a - b`. */
  fun subtract(
    a: Pmf,
    b: Pmf,
  ): Pmf = Convolution.of(a, negate(b))

  /** The distribution of `a * b`, one of which is a single certain value. */
  fun scale(
    pmf: Pmf,
    factor: Long,
  ): Pmf = remap(pmf) { it * factor }

  /** The distribution of `x / divisor`, rounded the way the setting says. */
  fun divideBy(
    pmf: Pmf,
    divisor: Long,
    rounding: Rounding,
  ): Pmf = remap(pmf) { rounding.divide(it.toLong(), divisor) }

  /** The distribution of `divisor / x`, for a certain numerator over a rolled divisor. */
  fun divideInto(
    numerator: Long,
    pmf: Pmf,
    rounding: Rounding,
  ): Pmf = remap(pmf) { rounding.divide(numerator, it.toLong()) }

  /** The distribution of `a * b`, by enumeration over both supports. */
  fun multiply(
    a: Pmf,
    b: Pmf,
  ): Pmf = combine(a, b) { left, right -> left.toLong() * right.toLong() }

  /** The distribution of `a / b`, by enumeration over both supports. */
  fun divide(
    a: Pmf,
    b: Pmf,
    rounding: Rounding,
  ): Pmf = combine(a, b) { left, right -> rounding.divide(left.toLong(), right.toLong()) }

  /** A die value raised to at least [floor], which is what `min n` does. */
  fun atLeast(
    pmf: Pmf,
    floor: Int,
  ): Pmf = remap(pmf) { maxOf(it, floor).toLong() }

  /**
   * Moves every value somewhere else, carrying its probability with it.
   *
   * Two values may land on the same total — `1d6 / 2` sends 5 and 4 both to 2 —
   * in which case their probabilities add, which is exactly right.
   */
  fun remap(
    pmf: Pmf,
    move: (Int) -> Long,
  ): Pmf {
    val moved = pmf.support.map(move)
    val low = moved.min()
    val size = checkedSize(moved.max() - low + 1)
    val weights = DoubleArray(size)
    pmf.support.forEachIndexed { index, value ->
      weights[(moved[index] - low).toInt()] += pmf.probabilityOf(value)
    }
    return Pmf.of(low.toInt(), weights)
  }

  /**
   * Every pairing of the two supports, twice over: once to find how wide the
   * answer is, once to fill it in.
   *
   * Two passes rather than reasoning about where the extremes must be. The
   * corners of the two ranges are the extremes for a product, and for a
   * quotient, and for everything else this is ever asked to do — but "must be"
   * arguments about integer division that rounds are exactly the kind that
   * turn out to have a case in them.
   */
  private fun combine(
    a: Pmf,
    b: Pmf,
    operation: (Int, Int) -> Long,
  ): Pmf {
    checkWork(a.probabilities().size.toLong() * b.probabilities().size.toLong(), "this formula")
    var low = Long.MAX_VALUE
    var high = Long.MIN_VALUE
    a.support.forEach { left ->
      b.support.forEach { right ->
        val value = operation(left, right)
        low = minOf(low, value)
        high = maxOf(high, value)
      }
    }
    val weights = DoubleArray(checkedSize(high - low + 1))
    a.support.forEach { left ->
      val leftProbability = a.probabilityOf(left)
      if (leftProbability > 0.0) {
        b.support.forEach { right ->
          weights[(operation(left, right) - low).toInt()] += leftProbability * b.probabilityOf(right)
        }
      }
    }
    return Pmf.of(low.toInt(), weights)
  }
}
