package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.simulation.api.ThrowSpec

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
 * @param thrown the throw that produced [result], as it actually happened: the
 *   spec it started as with the shake that actually arrived written back into
 *   it, so running it again gives this roll back. For a shake that is not the
 *   spec the dice were spawned from — the dice are spawned when the shake is
 *   confirmed and the moments arrive afterwards — which is the whole reason
 *   the two are not the same object (`docs/physics-and-rendering.md`, "Shake
 *   input").
 *
 *   **This is as far as it goes.** A past roll is a record, not something to
 *   re-run: `HistoryEntry` has no seed on it to show and the exports have no
 *   column for one (`docs/architecture.md`, decision 13, and
 *   `docs/statistics.md`). [ThrowRecorder] is the seam, and what crosses it is
 *   the result, the plan, the [seed] and where the throw came from — never
 *   this. Reproducing a roll is for a bug report, on a developer's machine,
 *   from a roll that is still on screen.
 */
data class FinishedThrow(
  val result: RollResult,
  val plan: RollPlan,
  val thrown: ThrowSpec,
  /**
   * The saved roll this throw came from, and the group it is in, or null for a
   * formula somebody typed (`docs/statistics.md`, per saved roll and per
   * group).
   *
   * Without it every throw is recorded as belonging to nothing, and the
   * saved-roll statistics screen is a screen that can never have anything on
   * it. It is dropped the moment the formula is edited: a roll that was
   * Fireball and has been typed over is not Fireball's throw.
   */
  val savedRollId: String? = null,
  val groupId: String? = null,
) {
  /**
   * The roll's own seed, for reproducing the throw when a bug report needs it.
   * Never shown and never exported (`docs/architecture.md`, decision 13).
   *
   * Read off [thrown] rather than carried beside it, because a seed that could
   * disagree with the spec it belongs to would reproduce a different roll.
   */
  val seed: Long get() = thrown.seed
}

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
