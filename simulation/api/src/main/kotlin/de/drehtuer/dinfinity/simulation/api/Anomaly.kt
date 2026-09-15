package de.drehtuer.dinfinity.simulation.api

/**
 * A roll the simulation had to finish for
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * **This is evidence, not a statistic.** A forced settle means the
 * twelve-second cap fired or a die was still cocked after its last re-throw; a
 * post-rest correction means something reached a die that had already stopped,
 * which is the one thing this app promises never happens
 * (`.claude/CLAUDE.md`). Both are supposed to be impossible, so a log with
 * anything in it is a bug report rather than a measurement — which is why
 * there is no average here, no rate and no chart.
 *
 * It carries the [seed] and the [ThrowSpec] because reproducing the roll is the
 * whole point of writing it down. That is also why it never leaves the
 * developer toggle: a past roll in the app's own history is a record, has no
 * replay and never shows a seed (`docs/architecture.md`, decision 13, and
 * `docs/statistics.md`).
 *
 * @param atEpochMs when the roll landed.
 * @param thrown the throw that produced it, which replays it exactly.
 * @param outcome what the dice came to, counters and all.
 */
data class Anomaly(
  val atEpochMs: Long,
  val thrown: ThrowSpec,
  val outcome: SimulationOutcome,
) {
  /** The roll's own seed, read off the throw rather than kept beside it. */
  val seed: Long get() = thrown.seed

  /** How many dice were in it. */
  val diceCount: Int get() = outcome.diceCount

  /** Dice the simulation had to finish for. */
  val forcedSettles: Int get() = outcome.forcedSettles

  /** Dice touched after they had come to rest. Must be zero, always. */
  val postRestCorrections: Int get() = outcome.postRestCorrections

  /** True for the worse of the two: a die moved after it had stopped. */
  val invisibleHand: Boolean get() = postRestCorrections > 0

  /**
   * One line saying what went wrong, in the order of how bad it is.
   *
   * A post-rest correction is named first and on its own wherever both
   * happened, because "a die was moved after it stopped" is not a detail
   * beside "a roll ran out of time" — it is the more serious of the two by
   * some way.
   */
  val what: String
    get() =
      when {
        invisibleHand -> "$postRestCorrections die(s) touched after coming to rest"
        else -> "$forcedSettles die(s) force-settled"
      }

  companion object {
    /**
     * The anomaly in [outcome], or null when there is none — which is every
     * roll, on a build that is working.
     *
     * Null rather than a clean entry, so that "the log is empty" stays the
     * only thing a healthy app ever shows and nobody has to read a list to
     * find out.
     */
    fun of(
      thrown: ThrowSpec,
      outcome: SimulationOutcome,
      atEpochMs: Long,
    ): Anomaly? = if (outcome.clean) null else Anomaly(atEpochMs, thrown, outcome)
  }
}

/**
 * What the developer toggle remembers while the app is running: the throws
 * that went wrong, and the last throw of all
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * One seam rather than two, because the roll screen has exactly one thing to
 * say — *this throw landed and here is what it came to* — and which half of
 * that is worth keeping is a question for whatever is keeping it.
 *
 * An interface so the roll screen can be handed one without knowing whether
 * anything is listening: [NONE] is what an install with the toggle off has,
 * and it keeps nothing at all.
 *
 * It is deliberately **not** storage. The anomalies are for a developer
 * watching the app now, an entry is a bug to be reported rather than a number
 * to be trended, and the last throw carries a seed — which decision 13 says
 * has no place in anything the ordinary app can reach, database included
 * (`docs/architecture.md`; `docs/TODO.md`, Open questions).
 */
interface DeveloperLog {
  /** Every anomaly this run has seen, oldest first. Empty on a working build. */
  val anomalies: List<Anomaly>

  /**
   * The last throw made this run, or null before the first one.
   *
   * The whole [ThrowSpec] — the dice, the table, the scale, the seed and the
   * shake that actually drove it — because that is what reproduces a roll. A
   * seed on its own reproduces nothing, which is what the replay surface says
   * in as many words (`FinishedThrow.thrown`).
   */
  val lastThrow: ThrowSpec?

  /** And what it came to, so a replay can say whether it came to it again. */
  val lastOutcome: SimulationOutcome?

  /**
   * A throw landed. Whether any of it is worth keeping is this log's to decide
   * — a clean roll adds no anomaly ([Anomaly.of]).
   */
  fun landed(
    thrown: ThrowSpec,
    outcome: SimulationOutcome,
    atEpochMs: Long,
  )

  /** Throws the lot away, which is the only thing a developer can do to it. */
  fun clear()

  companion object {
    /** Keeps nothing, and is what every install has until the toggle is on. */
    val NONE: DeveloperLog =
      object : DeveloperLog {
        override val anomalies: List<Anomaly> get() = emptyList()

        override val lastThrow: ThrowSpec? get() = null

        override val lastOutcome: SimulationOutcome? get() = null

        override fun landed(
          thrown: ThrowSpec,
          outcome: SimulationOutcome,
          atEpochMs: Long,
        ) = Unit

        override fun clear() = Unit
      }
  }
}

/**
 * The last throw, and the last [capacity] anomalies, in memory.
 *
 * Bounded because the thing it is watching for is a physics bug, and a bug
 * that fires on every roll would otherwise turn an evening of debugging into a
 * heap the app runs out of. The oldest go first: a run that has produced two
 * hundred of these is described perfectly well by its most recent fifty.
 */
class DeveloperNotes(
  private val capacity: Int = DEFAULT_CAPACITY,
) : DeveloperLog {
  init {
    require(capacity > 0) { "a log of $capacity entries holds nothing" }
  }

  private val kept = ArrayDeque<Anomaly>()

  override val anomalies: List<Anomaly> get() = kept.toList()

  override var lastThrow: ThrowSpec? = null
    private set

  override var lastOutcome: SimulationOutcome? = null
    private set

  override fun landed(
    thrown: ThrowSpec,
    outcome: SimulationOutcome,
    atEpochMs: Long,
  ) {
    lastThrow = thrown
    lastOutcome = outcome
    Anomaly.of(thrown, outcome, atEpochMs)?.let { anomaly ->
      kept.addLast(anomaly)
      while (kept.size > capacity) kept.removeFirst()
    }
  }

  override fun clear() {
    kept.clear()
    lastThrow = null
    lastOutcome = null
  }

  companion object {
    /** Enough to describe a session's worth of trouble and no more. */
    const val DEFAULT_CAPACITY: Int = 50
  }
}

/**
 * What a replay is, said in one place so that both ends mean the same thing
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * **A throw is a [ThrowSpec], not a seed.** The dice, the table, the scale and
 * the shake that drove it are every bit as much a part of what came out as the
 * number, and a "replay from seed" that took a seed and nothing else would
 * reproduce a roll only by coincidence. So both actions replay a spec, and the
 * only difference between them is whose seed it carries.
 */
object Replay {
  /**
   * The same throw again: the same dice, the same table, the same shake and
   * the same seed. It comes to the same faces, and that is the check
   * ([SimulationOutcome] is compared against the recorded one).
   */
  fun again(spec: ThrowSpec): ThrowSpec = spec

  /**
   * The same throw with a different seed — the honest reading of "replay from
   * seed".
   *
   * Everything else is held: a seed typed into a box means *this* throw under
   * that seed, which is the question somebody chasing a stacking bug is
   * actually asking. It is not the throw that seed produced somewhere else,
   * and the screen says so.
   */
  fun withSeed(
    spec: ThrowSpec,
    seed: Long,
  ): ThrowSpec = spec.copy(seed = seed)

  /**
   * The seed in [typed], or null when it is not one.
   *
   * A seed is a signed 64-bit number and nothing else. Whitespace is forgiven
   * because a pasted one usually brings some; everything else is refused, so a
   * half-typed seed produces a disabled button rather than a throw of
   * something else.
   */
  fun seedOf(typed: String): Long? = typed.trim().toLongOrNull()
}

/**
 * The log as text, for pasting into a bug report.
 *
 * Plain lines rather than JSON: the reader is a person writing an issue, and
 * every field here is a number or a name. It is the one place a seed is
 * *written out anywhere*, which is why it is reached only from the developer
 * screen and never from the statistics export (`docs/statistics.md`).
 */
object AnomalyReport {
  /** The header a report with nothing in it carries, which is the good case. */
  const val NOTHING: String = "No anomalies. This is what a working build looks like."

  /** What the file is called when it is shared. */
  const val FILE_NAME: String = "dInfinity-anomalies.txt"

  /** [entries] as lines, newest last, or [NOTHING]. */
  fun text(entries: List<Anomaly>): String =
    if (entries.isEmpty()) {
      NOTHING
    } else {
      entries.joinToString(separator = "\n", transform = ::line)
    }

  /** One anomaly, on one line. */
  fun line(anomaly: Anomaly): String =
    buildString {
      append(anomaly.atEpochMs)
      append("  seed=").append(anomaly.seed)
      append("  dice=").append(anomaly.diceCount)
      append("  steps=").append(anomaly.outcome.steps)
      append("  corrections=").append(anomaly.outcome.corrections)
      append("  rethrows=").append(anomaly.outcome.rethrows)
      append("  forced=").append(anomaly.forcedSettles)
      append("  postRest=").append(anomaly.postRestCorrections)
      append("  ").append(anomaly.what)
    }
}
