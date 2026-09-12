package de.drehtuer.dinfinity.core.probability

/**
 * The exact distribution of the sum of the highest — or lowest — few of
 * several identical dice: `2d20kh1`, `4d6dl1`, and everything like them.
 *
 * There is no shortcut here. `2d20kh1` is not "a d20 shifted"; it is a
 * genuinely different distribution, and half the reason the outcome graph
 * exists is to show a player what advantage actually does to their odds
 * (`docs/probability.md`).
 *
 * The method is a dynamic program over the die's distinct values taken from
 * the highest down. Going down is what makes it cheap: the dice assigned so
 * far are always the largest ones, so how many of them are *kept* is simply
 * `min(assigned, keep)` and needs no dimension of its own. The state is the
 * number of dice placed and the total they contribute, and the transition at
 * each value is "how many of the dice left over show this one", weighted by a
 * binomial — the multinomial coefficient of the whole assignment, factored.
 */
internal object OrderStatistics {
  /** The distribution of the sum of the [keep] highest of [count] rolls of [unit]. */
  fun keepHighest(
    unit: Pmf,
    count: Int,
    keep: Int,
  ): Pmf {
    require(count >= 1) { "there is nothing to keep out of $count dice" }
    require(keep in 0..count) { "cannot keep $keep of $count dice" }
    if (keep == 0) return Pmf.certain(0)
    if (keep == count) return Convolution.repeated(unit, count)
    return dynamicProgramme(unit, count, keep)
  }

  /**
   * The distribution of the sum of the [keep] lowest.
   *
   * Keeping the lowest of some dice is keeping the highest of those dice
   * turned upside down, so it is the same program with the values negated
   * going in and coming out. One implementation to get right instead of two.
   */
  fun keepLowest(
    unit: Pmf,
    count: Int,
    keep: Int,
  ): Pmf = PmfArithmetic.negate(keepHighest(PmfArithmetic.negate(unit), count, keep))

  private fun dynamicProgramme(
    unit: Pmf,
    count: Int,
    keep: Int,
  ): Pmf {
    val values = unit.support.filter { unit.probabilityOf(it) > 0.0 }.sortedDescending()
    val low = minOf(0, keep * values.min())
    val width = maxOf(0, keep * values.max()) - low + 1
    checkWork(values.size.toLong() * (count + 1) * width * (count + 1), "keeping some of this many dice")
    var current = Array(count + 1) { DoubleArray(width) }
    current[0][-low] = 1.0
    values.forEach { value -> current = step(current, unit.probabilityOf(value), value, keep) }
    return Pmf.of(low, current[count])
  }

  /**
   * One value's worth of transitions: how many of the dice still free show it.
   *
   * The weight `C(free, taken) · p^taken` is carried along rather than
   * computed from a table of binomials. `C(200, 100)` is 9e58 and `p^100` for
   * a d6 is 1.5e-78; multiplying them in that order is fine in a `Double`, but
   * the habit of building the huge factor first is how a distribution over
   * more dice would quietly become a row of zeroes.
   */
  private fun step(
    current: Array<DoubleArray>,
    probability: Double,
    value: Int,
    keep: Int,
  ): Array<DoubleArray> {
    val count = current.size - 1
    val next = Array(count + 1) { DoubleArray(current[0].size) }
    for (placed in 0..count) {
      val row = current[placed]
      val free = count - placed
      var weight = 1.0
      for (taken in 0..free) {
        val shift = (minOf(placed + taken, keep) - minOf(placed, keep)) * value
        if (weight != 0.0) spread(row, next[placed + taken], shift, weight)
        weight *= (free - taken).toDouble() / (taken + 1) * probability
      }
    }
    return next
  }

  /** Adds [row], shifted by [shift] and weighted by [weight], into [target]. */
  private fun spread(
    row: DoubleArray,
    target: DoubleArray,
    shift: Int,
    weight: Double,
  ) {
    for (index in row.indices) {
      val mass = row[index]
      if (mass != 0.0) target[index + shift] += mass * weight
    }
  }
}
