package de.drehtuer.dinfinity.navigation

/**
 * What a press of system back on the roll screen means: arm, or leave
 * (`docs/architecture.md`, "Navigation").
 *
 * **Back off the roll screen is two presses, not one.** The first press arms
 * and says so; a second within [window] milliseconds leaves the app; after
 * that the arming lapses, so a stray edge swipe cannot close the app a minute
 * later.
 *
 * It is a state machine over "back was pressed at time *t*" rather than a
 * timer inside a composable, for two reasons. A composable that owned a
 * coroutine and a flag would make the rule a thing that can only be observed
 * by pressing buttons on a phone; and the clock is then something a test has
 * to *wait* for. Here the time is handed in, so the whole of the behaviour —
 * including the press that arrives a millisecond too late — is a JVM test that
 * runs in no time at all ([LeavingTheAppTest]).
 *
 * The caller decides what to do with the answer: raise the toast, or finish
 * the activity. This knows nothing about either.
 *
 * @param window how long an arming lasts, in milliseconds. The design's two
 *   seconds (2026-09-17).
 */
class LeavingTheApp(
  private val window: Long = WINDOW_MILLIS,
) {
  /**
   * When the arming lapses, or null while nothing is armed.
   *
   * A deadline rather than "when it was armed", so the comparison is one
   * subtraction-free `<` and cannot be got the wrong way round.
   */
  private var armedUntil: Long? = null

  /** Whether a second press right now would leave the app. */
  val armed: Boolean get() = armedUntil != null

  /**
   * System back was pressed at [now]. Says what should happen.
   *
   * A press exactly on the deadline arms again rather than leaving: the window
   * is the two seconds *after* the first press, and a boundary that closes the
   * app is the wrong one to guess at.
   */
  fun pressed(now: Long): Answer {
    val deadline = armedUntil
    if (deadline != null && now < deadline) {
      armedUntil = null
      return Answer.Leave
    }
    armedUntil = now + window
    return Answer.Arm
  }

  /**
   * The arming is over — the words have gone, or the screen has.
   *
   * Called by whatever showed the toast when it takes it away, so that the
   * promise on screen and the state in here cannot disagree.
   */
  fun lapse() {
    armedUntil = null
  }

  /** What a press of back comes to. */
  enum class Answer {
    /** Nothing leaves. Say that another press would. */
    Arm,

    /** The player has said it twice. Leave the app. */
    Leave,
  }

  companion object {
    /** The design's window: two seconds (`docs/architecture.md`, "Navigation"). */
    const val WINDOW_MILLIS: Long = 2_000L
  }
}
