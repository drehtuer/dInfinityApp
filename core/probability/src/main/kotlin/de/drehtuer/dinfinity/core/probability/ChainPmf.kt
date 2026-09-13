package de.drehtuer.dinfinity.core.probability

/**
 * The distribution of one *chain*: a die, whatever it was rerolled into, every
 * die its explosions added, and `min` applied to each of them.
 *
 * A chain is the unit `kh`/`kl`/`dh`/`dl` rank, which is why it is worked out
 * as one thing: `2d6!kh1` keeps the better of two chains, not the better of
 * two first throws (`docs/dice-notation.md`, "The order modifiers are applied
 * in"). The graph applies the modifiers in that same order, so the chart and
 * the dice agree about what the formula means.
 */
internal object ChainPmf {
  /**
   * @param die what one throw of this die produces.
   * @param rerollAtOrBelow a first throw at or below this is thrown once more;
   *   `null` for no reroll.
   * @param explodesAt the value that sets off another throw, or `null` for a
   *   group that does not explode.
   * @param minimum the floor each throw counts as, or `null` for none.
   */
  fun of(
    die: Pmf,
    rerollAtOrBelow: Int? = null,
    explodesAt: Int? = null,
    minimum: Int? = null,
  ): Pmf {
    val first = rerollAtOrBelow?.let { rerolled(die, it) } ?: die
    if (explodesAt == null) return floor(first, minimum)
    return exploded(first, die, explodesAt, minimum)
  }

  /**
   * How likely an exploding chain is to run all the way into the depth limit
   * and be cut off — the mass the graph is not quite exact about, and says so
   * (`docs/probability.md`).
   */
  fun truncatedMass(
    die: Pmf,
    explodesAt: Int?,
  ): Double {
    if (explodesAt == null) return 0.0
    val chance = die.probabilityOf(explodesAt)
    var mass = 1.0
    repeat(ProbabilityLimits.EXPLOSION_DEPTH) { mass *= chance }
    return mass
  }

  /** The highest face a chain's die can show, which is what sets an explosion off. */
  fun explodingValue(die: Pmf): Int = die.max

  /** `r n`: a first throw at or below [threshold] is replaced by an independent one. */
  private fun rerolled(
    die: Pmf,
    threshold: Int,
  ): Pmf {
    val rerolledMass = die.atMost(threshold)
    if (rerolledMass == 0.0) return die
    val low = die.min
    val weights = DoubleArray(die.max - low + 1)
    die.support.forEach { value ->
      val kept = if (value > threshold) die.probabilityOf(value) else 0.0
      weights[value - low] = kept + rerolledMass * die.probabilityOf(value)
    }
    return Pmf.of(low, weights)
  }

  /**
   * `!`: a throw showing [explodesAt] is followed by another of the same die,
   * down to the depth limit, where the last throw simply does not explode.
   *
   * Built from the bottom up, so the recursion is a loop rather than a stack,
   * and every level is a proper distribution — there is no missing mass to
   * account for, only a tail the chain was not followed into.
   */
  private fun exploded(
    first: Pmf,
    die: Pmf,
    explodesAt: Int,
    minimum: Int?,
  ): Pmf {
    val explodingChance = die.probabilityOf(explodesAt)
    if (explodingChance == 0.0) return floor(first, minimum)
    val rules = Rules(explodesAt, maxOf(explodesAt, minimum ?: explodesAt), minimum)
    // The deepest throw is the one that does not explode, however it lands; on
    // top of it go the levels that did, and the player's own first throw last.
    var tail = floor(die, minimum)
    repeat(ProbabilityLimits.EXPLOSION_DEPTH - 1) { tail = addOneThrow(die, tail, rules, explodingChance) }
    return addOneThrow(first, tail, rules, first.probabilityOf(explodesAt))
  }

  /** What every level of a chain has in common. */
  private data class Rules(
    val explodesAt: Int,
    val step: Int,
    val minimum: Int?,
  )

  /** One more level of the chain: this throw, plus what follows if it exploded. */
  private fun addOneThrow(
    throwPmf: Pmf,
    rest: Pmf,
    rules: Rules,
    explodingChance: Double,
  ): Pmf {
    if (explodingChance == 0.0) return floor(throwPmf, rules.minimum)
    val shifted = PmfArithmetic.remap(rest) { it + rules.step.toLong() }
    val start = minOf(floorValue(throwPmf.min, rules.minimum), shifted.min)
    val end = maxOf(floorValue(throwPmf.max, rules.minimum), shifted.max)
    val weights = DoubleArray(end - start + 1)
    throwPmf.support.forEach { value ->
      if (value != rules.explodesAt) {
        weights[floorValue(value, rules.minimum) - start] += throwPmf.probabilityOf(value)
      }
    }
    shifted.support.forEach { value -> weights[value - start] += explodingChance * shifted.probabilityOf(value) }
    return Pmf.of(start, weights)
  }

  private fun floor(
    pmf: Pmf,
    minimum: Int?,
  ): Pmf = if (minimum == null) pmf else PmfArithmetic.atLeast(pmf, minimum)

  private fun floorValue(
    value: Int,
    minimum: Int?,
  ): Int = if (minimum == null) value else maxOf(value, minimum)
}
