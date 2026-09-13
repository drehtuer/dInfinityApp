package de.drehtuer.dinfinity.input.shake

/**
 * Decides when the player is shaking the phone
 * (`docs/physics-and-rendering.md`, "Shake input").
 *
 * A shake begins when the phone has been moving hard for long enough to mean
 * it, and ends after it has been still for long enough to mean that too. Two
 * thresholds rather than one, with a gap between them, because a single one
 * would flicker on and off through the quiet part of every swing: this is a
 * Schmitt trigger, and a shake is exactly the kind of signal that needs one.
 *
 * It is a state machine over timestamps and magnitudes and nothing else — no
 * Android, no clock of its own — so a whole shake can be played through it in
 * a unit test, at any speed, in either order.
 */
class ShakeDetector {
  /** What the detector thinks is happening. */
  enum class State {
    /** Nothing worth calling a shake. */
    Idle,

    /** Moving hard, but not for long enough yet to be sure. */
    Starting,

    /** Being shaken. */
    Shaking,

    /** Has gone quiet, but not for long enough yet to be sure it has stopped. */
    Stopping,
  }

  /** What a sample changed, if anything. */
  enum class Event {
    /** Nothing to report. */
    None,

    /** A shake has begun: the dice are spawned now. */
    Started,

    /** The shake is over: the dice are released. */
    Ended,
  }

  var state: State = State.Idle
    private set

  private var since: Long = 0
  private var startedAt: Long = 0

  /**
   * Takes one sample and says what it changed.
   *
   * @param atMillis the sample's own timestamp. Monotonic, from the sensor,
   *   never from a wall clock: a shake must not end because the user crossed
   *   a time zone.
   * @param magnitudeMmPerSecond2 how hard the phone is being moved, with
   *   gravity already taken out.
   */
  fun sample(
    atMillis: Long,
    magnitudeMmPerSecond2: Double,
  ): Event {
    val hard = magnitudeMmPerSecond2 >= ShakeThresholds.START_MM_PER_SECOND2
    val quiet = magnitudeMmPerSecond2 <= ShakeThresholds.STOP_MM_PER_SECOND2
    return when (state) {
      State.Idle -> if (hard) enter(State.Starting, atMillis) else Event.None
      State.Starting -> starting(atMillis, hard)
      State.Shaking -> shaking(atMillis, quiet)
      State.Stopping -> stopping(atMillis, hard)
    }
  }

  /** Abandons whatever was in progress, for a roll that was cancelled. */
  fun reset() {
    state = State.Idle
    since = 0
    startedAt = 0
  }

  private fun starting(
    atMillis: Long,
    hard: Boolean,
  ): Event =
    when {
      !hard -> enter(State.Idle, atMillis)
      atMillis - since < ShakeThresholds.START_MILLIS -> Event.None
      else -> {
        startedAt = since
        enter(State.Shaking, atMillis)
        Event.Started
      }
    }

  private fun shaking(
    atMillis: Long,
    quiet: Boolean,
  ): Event =
    when {
      atMillis - startedAt >= ShakeThresholds.MAX_SESSION_MILLIS -> {
        enter(State.Idle, atMillis)
        Event.Ended
      }
      quiet -> enter(State.Stopping, atMillis)
      else -> Event.None
    }

  private fun stopping(
    atMillis: Long,
    hard: Boolean,
  ): Event =
    when {
      hard -> enter(State.Shaking, atMillis)
      atMillis - since < ShakeThresholds.STOP_MILLIS -> Event.None
      else -> {
        enter(State.Idle, atMillis)
        Event.Ended
      }
    }

  private fun enter(
    next: State,
    atMillis: Long,
  ): Event {
    if (next != state) since = atMillis
    state = next
    return Event.None
  }
}
