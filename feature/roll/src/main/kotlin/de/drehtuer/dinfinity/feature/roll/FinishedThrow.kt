package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult

/**
 * A throw that has landed and been read.
 *
 * Handed out by [RollMachine.settled] so that whatever wants to write it down
 * can, without this module knowing what writing it down means. The screen's
 * side of the seam described in `data`'s `RollRecording`.
 *
 * @param plan is here as well as the result because the result knows which
 *   face came up and only the plan knows which die it was and which set
 *   supplied it — and the statistics are kept per die.
 * @param seed for reproducing the throw when a bug report needs it. Never
 *   shown and never exported (`docs/architecture.md`, decision 13).
 */
data class FinishedThrow(
  val result: RollResult,
  val plan: RollPlan,
  val seed: Long,
)

/**
 * Something that writes a finished throw down.
 *
 * An interface here and an implementation in `:app`, rather than a dependency
 * on `:data` from this module. A roll screen that could reach a database is a
 * roll screen that will eventually query one mid-throw.
 *
 * It takes no callback and returns nothing on purpose: recording a roll is not
 * allowed to change what the roll came to, or to make the player wait.
 */
fun interface ThrowRecorder {
  fun record(thrown: FinishedThrow)

  companion object {
    /** Records nothing, which is what a test and a preview want. */
    val NONE: ThrowRecorder = ThrowRecorder { }
  }
}
