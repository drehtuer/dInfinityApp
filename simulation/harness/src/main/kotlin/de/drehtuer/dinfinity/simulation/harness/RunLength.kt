package de.drehtuer.dinfinity.simulation.harness

import java.util.Locale

/**
 * How much of a run there is: a number of throws, or a length of time.
 *
 * A soak is "the same runner given a duration rather than a roll count"
 * (`docs/TODO.md`, Step 5.1), and this is that sentence made into a type. The
 * device's loop asks [keepGoing] after every throw and asks nothing else, so
 * there is one runner rather than two and no place for a soak to quietly become
 * a different measurement from the run it is soaking.
 *
 * It is a type and not a large number of rolls on purpose. "Roll for five
 * minutes" cannot be written as a roll count without knowing how long a roll
 * takes — which is the very thing the run is measuring — so a count chosen to
 * fill five minutes would be a guess that is wrong on exactly the device where
 * it matters, the slow one.
 */
sealed interface RunLength {
  /** What was asked for, in words, for the run's own record of itself. */
  val described: String

  /**
   * Whether a run that has made [made] throws in [elapsedSeconds] should make
   * another.
   *
   * Asked *after* each throw and before the next, so a run always makes its
   * first one: a scorecard over no rolls has met every bar and missed none,
   * which is the one way a harness can pass by doing nothing.
   */
  fun keepGoing(
    made: Int,
    elapsedSeconds: Double,
  ): Boolean

  /**
   * A run of a fixed number of throws — the harness as it has always been.
   *
   * @param rolls how many throws to make.
   */
  data class Rolls(
    val rolls: Int,
  ) : RunLength {
    init {
      require(rolls > 0) { "a run of $rolls rolls has nothing to measure" }
    }

    override val described: String get() = "$rolls rolls"

    override fun keepGoing(
      made: Int,
      elapsedSeconds: Double,
    ): Boolean = made < rolls
  }

  /**
   * A run of a fixed length of time, which is what soak mode is.
   *
   * @param seconds how long to go on throwing for. The throw under way when
   *   the time runs out is finished rather than cut short — a roll stopped
   *   half way is a settle time nobody measured, and it would be the longest
   *   one in the sample.
   */
  data class Soak(
    val seconds: Double,
  ) : RunLength {
    init {
      require(seconds > 0.0) { "a soak of $seconds seconds is not a length of time to roll for" }
    }

    override val described: String get() = String.format(Locale.ROOT, "%.0f s of rolling", seconds)

    override fun keepGoing(
      made: Int,
      elapsedSeconds: Double,
    ): Boolean = made == 0 || elapsedSeconds < seconds
  }

  companion object {
    /** How many seconds a minute is, for a duration written as one. */
    const val SECONDS_PER_MINUTE: Double = 60.0

    /** And an hour, because a soak long enough to find a leak is written in them. */
    const val SECONDS_PER_HOUR: Double = 3_600.0

    /**
     * The run the two arguments ask for, or null when they ask for none.
     *
     * A soak wins over a roll count when both are given, because asking for
     * both is asking for the rarer of the two: nobody passes `--soak` by
     * accident, and the roll count has a default that is always there to be
     * inherited.
     */
    fun from(
      rolls: String?,
      soak: String?,
    ): RunLength? {
      val soaked = soak?.let(::secondsOf)
      if (soaked != null) return Soak(soaked)
      val counted = rolls?.trim()?.toIntOrNull() ?: return null
      return if (counted > 0) Rolls(counted) else null
    }

    /**
     * How many seconds [text] is, written the way a person writes a duration:
     * `90`, `90s`, `5m` or `1h`.
     *
     * Null rather than an exception for text that is not a duration at all,
     * because "no soak was asked for" and "a soak was asked for badly" arrive
     * at the same argument and only one of them should stop a run. A bare
     * number is seconds, which is the unit the rest of the harness reports in.
     */
    fun secondsOf(text: String): Double? {
      val wanted = text.trim().lowercase()
      if (wanted.isEmpty()) return null
      val scale =
        when (wanted.last()) {
          'h' -> SECONDS_PER_HOUR
          'm' -> SECONDS_PER_MINUTE
          's' -> 1.0
          else -> 0.0
        }
      val number = if (scale == 0.0) wanted else wanted.dropLast(1)
      return number.toDoubleOrNull()?.times(if (scale == 0.0) 1.0 else scale)?.takeIf { it > 0.0 }
    }
  }
}
