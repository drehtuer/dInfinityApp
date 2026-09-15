package de.drehtuer.dinfinity.simulation.harness

/**
 * What a sample of numbers came to: the middle of it, its 99th percentile and
 * its worst case.
 *
 * Three figures and not a histogram, because those are the three Step 5 is
 * written in — "median under 2 s, p99 under 4 s, the cap never reached"
 * (`docs/TODO.md`, Step 5.5). The whole sample is still in the JSON document
 * one roll at a time, so anything else can be worked out afterwards from the
 * file rather than being guessed at now.
 *
 * @param median the middle of the sample.
 * @param p99 the value only one roll in a hundred is worse than.
 * @param worst the worst single value in the sample. A p99 says nothing about
 *   the tail beyond it, and the tail is where a roll that never settled lives.
 */
data class Distribution(
  val median: Double,
  val p99: Double,
  val worst: Double,
) {
  companion object {
    /** The empty sample, which is what a run of no rolls has. */
    val Nothing: Distribution = Distribution(0.0, 0.0, 0.0)

    /**
     * The three figures of [values], by nearest rank.
     *
     * **Nearest rank, never interpolated.** An interpolated percentile is an
     * average of two rolls, and a settle time that no roll actually took is
     * not evidence about a roll — it is arithmetic about a list. Every figure
     * here is a value some roll really produced, which is what makes "p99
     * under 4 s" a statement about dice.
     */
    fun of(values: List<Double>): Distribution {
      if (values.isEmpty()) return Nothing
      val sorted = values.sorted()
      return Distribution(
        median = nearestRank(sorted, MEDIAN),
        p99 = nearestRank(sorted, NINETY_NINTH),
        worst = sorted.last(),
      )
    }

    /**
     * The value at [fraction] of the way through [sorted], counted by rank.
     *
     * The rank is `ceil(fraction × n)`, clamped into the list — the standard
     * nearest-rank definition, which puts p100 at the last value and any
     * fraction at or below zero at the first.
     */
    fun nearestRank(
      sorted: List<Double>,
      fraction: Double,
    ): Double {
      require(sorted.isNotEmpty()) { "there is no percentile of an empty sample" }
      val rank = kotlin.math.ceil(fraction * sorted.size).toInt()
      return sorted[rank.coerceIn(1, sorted.size) - 1]
    }

    private const val MEDIAN = 0.5
    private const val NINETY_NINTH = 0.99
  }
}
