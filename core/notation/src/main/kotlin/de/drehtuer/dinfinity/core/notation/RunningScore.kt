package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.Rounding

/**
 * Scores a throw as far as the dice already thrown allow, and says what else
 * has to be thrown before it can be finished.
 *
 * [RollEvaluator.score] asks for an extra die's face the moment it needs it and
 * expects an answer straight away, which is fine for a throw nobody is
 * watching: the simulation runs headless and hands a number back in a
 * millisecond. It is no use at all for a throw on screen, where an exploding
 * die takes a second and a half of real time to land and the frame callback
 * that steps it is the thread that would be blocked waiting.
 *
 * So the question is turned round. Scoring is *pure* — the same faces and the
 * same extra dice always produce the same result — so it can simply be run
 * again each time a die lands, from the beginning, with one more answer in
 * hand. What comes out is either the finished result or the next die to throw,
 * and the tray throws that one and asks again. How many extra dice a roll needs
 * is not knowable in advance (`8d6!` might add none or a dozen), which is why
 * this is a conversation rather than a list.
 *
 * **Nothing here decides a number.** Every face in [added] came out of a
 * simulation, in the order this asked for them, and re-running the scoring
 * cannot change which face a die landed on (`docs/architecture.md`, goal 1).
 */
object RunningScore {
  /** [outcome] scored with what the roll has already added to itself. */
  fun of(
    formula: Formula,
    plan: RollPlan,
    outcome: ThrowOutcome,
    rounding: Rounding = Rounding.Default,
    added: AddedDice = AddedDice(),
  ): Scoring =
    try {
      Scoring.Scored(RollEvaluator.score(formula, plan, outcome, rounding, Replay(added)))
    } catch (needed: NeedsAnotherDie) {
      Scoring.OneMoreDie(needed.die, added.faces.size)
    }
}

/**
 * What a roll has added to itself so far, and whether the tray could take one
 * more.
 *
 * The two travel together because they are two halves of one question. The
 * faces are what the dice already thrown came to, in the order the scoring
 * asked for them; [room] is asked only about a die that has *not* been thrown,
 * because a die that has landed is on the table whatever the tray looks like
 * now.
 *
 * @param room whether the tray could take one more of a given die. A chain of
 *   explosions stops when the answer is no, which is the honest end for it: an
 *   added die is dropped into clear floor and there is none left
 *   (`docs/tables.md`, "Capacity rule").
 */
class AddedDice(
  val faces: List<Int> = emptyList(),
  val room: (Die) -> Boolean = { true },
)

/** What scoring a throw came to, which is not always a result. */
sealed interface Scoring {
  /** Every die the formula called for has been thrown, and this is the total. */
  data class Scored(
    val result: RollResult,
  ) : Scoring

  /**
   * The formula calls for one more die before it can be scored.
   *
   * @param die which die to throw — one of the dice already in the roll, so
   *   which set it came from is known rather than guessed.
   * @param ordinal how many dice the roll has already added, which is what
   *   gives the new throw a seed of its own (`Seeds.derived`).
   */
  data class OneMoreDie(
    val die: Die,
    val ordinal: Int,
  ) : Scoring
}

/**
 * Answers from what has already been thrown, and stops the scoring dead the
 * moment it is asked for something that has not.
 *
 * The count is a position in [added] rather than a tally of calls, so the same
 * answers always go to the same dice however often the scoring is re-run.
 */
private class Replay(
  private val added: AddedDice,
) : ExtraThrow {
  private var taken = 0

  override fun roll(die: Die): Int {
    if (taken == added.faces.size) throw NeedsAnotherDie(die)
    return added.faces[taken++]
  }

  /**
   * A die that has already been thrown was thrown into a tray that had room
   * for it, whatever the tray looks like now.
   *
   * Without that, a re-score would drop a die the player has already watched
   * land: the tray fills up as the chain grows, so asking the live question
   * again about an old die would eventually answer no and take it out of the
   * breakdown. Only the die that has *not* been thrown yet is a real question.
   */
  override fun roomForAnother(die: Die): Boolean = taken < added.faces.size || added.room(die)
}

/** Thrown by [Replay] and caught by [RunningScore]; never escapes this file. */
private class NeedsAnotherDie(
  val die: Die,
) : RuntimeException("the roll needs another ${die.id} before it can be scored", null, false, false)
