package de.drehtuer.dinfinity.core.stats

/**
 * One face of a die, and how often it has come up
 * (`docs/statistics.md`, "Screens"; design option `1w`).
 *
 * @param share what fraction of this die's throws landed here, `0.0` when it
 *   has never been thrown.
 * @param fairShare what fraction a fair die would land here, which is the
 *   faint line the bars are drawn against. Not always `1/sides`: a die labelled
 *   `1,2,3,1,2,3` shows each of three values twice as often as a d3 would, and
 *   the line has to say so or the die looks loaded.
 */
data class FaceBar(
  val value: Int,
  val count: Long,
  val droppedCount: Long,
  val share: Double,
  val fairShare: Double,
) {
  /** True when this face has come up more often than a fair die would. */
  val over: Boolean get() = share > fairShare
}

/**
 * Turning the tallies of one die into the bars a screen draws.
 *
 * Pure arithmetic, apart from the screen, for the usual two reasons: it is the
 * part that can be wrong, and a Canvas draw lambda is the one place a test
 * cannot reach.
 *
 * The rule that makes it more than a division: **the fair line is per value,
 * not per face.** A die's values are counted, not its faces, so a d6 labelled
 * `1,2,3,1,2,3` is a d3 whose fair share is a third — and drawing it against a
 * sixth would show a die that looks twice as lucky as it is on every value
 * (`docs/statistics.md`, per die).
 */
object FaceHistogram {
  /**
   * The bars for one die.
   *
   * @param values every value the die can show, with repeats — the face list,
   *   mapped to values. When the set is not installed any more and nobody can
   *   say what its faces were, pass the values that have actually come up: the
   *   fair line is then a guess, and the screen says so.
   * @param tallies what has been recorded. A value with no tally is a bar of
   *   zero rather than a gap: "this d20 has never rolled a 20" is the single
   *   most interesting thing a histogram can say.
   */
  fun of(
    values: List<Int>,
    tallies: List<FaceTally>,
  ): List<FaceBar> {
    if (values.isEmpty()) return emptyList()
    val counted = tallies.associateBy(FaceTally::faceValue)
    val throws = tallies.sumOf(FaceTally::count)
    // Repeats are what make the fair line right, so they are counted here and
    // the bars are drawn one per distinct value.
    val weights = values.groupingBy { it }.eachCount()
    return values
      .distinct()
      .sorted()
      .map { value ->
        val tally = counted[value]
        FaceBar(
          value = value,
          count = tally?.count ?: 0,
          droppedCount = tally?.droppedCount ?: 0,
          share = if (throws == 0L) 0.0 else (tally?.count ?: 0).toDouble() / throws,
          fairShare = weights.getValue(value).toDouble() / values.size,
        )
      }
  }

  /**
   * How many of [tallies] landed on the die's highest value, and how many on
   * its lowest.
   *
   * The two numbers a player actually asks for — "how many natural 20s have I
   * rolled this campaign" — and the reason the statistics are kept at all.
   */
  fun extremes(
    values: List<Int>,
    tallies: List<FaceTally>,
  ): Extremes {
    if (values.isEmpty()) return Extremes()
    val counted = tallies.associateBy(FaceTally::faceValue)
    val highest = values.max()
    val lowest = values.min()
    return Extremes(
      highestValue = highest,
      lowestValue = lowest,
      highs = counted[highest]?.count ?: 0,
      lows = counted[lowest]?.count ?: 0,
    )
  }
}

/** The natural highs and lows of one die (design option `1w`). */
data class Extremes(
  val highestValue: Int = 0,
  val lowestValue: Int = 0,
  val highs: Long = 0,
  val lows: Long = 0,
)
