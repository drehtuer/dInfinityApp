package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.RollPlan
import de.drehtuer.dinfinity.core.model.RollResult

/**
 * Writes down a throw that has landed
 * (`docs/statistics.md`, "What is recorded").
 *
 * The seam between a roll screen and the database. What the screen has is a
 * [RollResult] and the [RollPlan] it came from; what the database wants is a
 * history row, a face count for every die and a running summary for each — all
 * in one transaction. This turns the first into the second and nothing else.
 *
 * It is here rather than in `feature/roll` for the reason every module boundary
 * in this project has: the screen would otherwise have to know what a
 * `DieSummaryRow` is. The screen hands over a result and a plan, both of them
 * `core/model` types, and is told nothing back.
 *
 * @param sessionOf which session a roll belongs to. A function rather than a
 *   value because the active session changes while the app is running, and a
 *   recorder that captured it once would file every roll of an evening under
 *   whichever session was current when the screen opened.
 * @param sessions the sessions there are, to check that the one named is still
 *   one of them. Null skips the check, which is what a test that is not about
 *   sessions wants. It sits *before* [sessionOf] so that the lambda stays the
 *   last parameter: `RollRecording(statistics) { activeSession }` is how every
 *   caller writes it, and a parameter added after a trailing lambda silently
 *   rebinds that lambda onto the new one.
 */
class RollRecording(
  private val statistics: StatisticsRepository,
  private val sessions: SessionRepository? = null,
  private val sessionOf: () -> String = { NO_SESSION },
) {
  /**
   * Records one throw.
   *
   * @param seed kept for reproducing a roll when a bug report needs it, and
   *   never shown or exported (`docs/architecture.md`, decision 13).
   * @param savedRollId the saved roll this came from, when it came from one.
   *   That is what makes "Thorin's attack rolls this campaign" a query rather
   *   than a guess (`docs/statistics.md`).
   * @return the history row's id.
   */
  suspend fun record(
    result: RollResult,
    plan: RollPlan,
    seed: Long = 0,
    savedRollId: String? = null,
    groupId: String? = null,
  ): Long =
    statistics.record(
      FinishedRoll(
        result = result,
        // The result knows which face came up; only the plan knows which die
        // it was and which set supplied it, and the summaries are per die.
        dice = plan.dice.associate { it.index to RolledDieSource(setId = it.setId, die = it.die) },
        context =
          RollContext(
            sessionId = sessionStillThere(),
            savedRollId = savedRollId,
            groupId = groupId,
          ),
        breakdownJson = Breakdown.of(result),
        replay = RollReplay(seed = seed),
      ),
    )

  /**
   * The active session if it is still there, and the first session if it is
   * not (`docs/statistics.md`, per session).
   *
   * Which session is active is a preference, and a preference outlives the
   * thing it names. Deleting the active session on the sessions screen puts
   * the setting right, but a session deleted while another screen is in front
   * — or one already gone when the app was last opened — would leave every
   * throw filed under an id that is not there. Those rolls would then be in
   * the history and in the face counts and visible in neither, because both
   * are read through the list of sessions.
   *
   * Checked here rather than in [StatisticsRepository], which records what it
   * is told and should go on doing so: this is the seam that knows the
   * preference, so this is where a stale one is caught.
   */
  private suspend fun sessionStillThere(): String {
    val asked = sessionOf()
    val repository = sessions ?: return asked
    return if (repository.exists(asked)) asked else SessionRepository.DEFAULT_ID
  }

  companion object {
    /**
     * The session a roll belongs to when nothing else says.
     *
     * A name rather than an empty string, because the column is not nullable
     * and "" in a history is a value somebody would one day have to guess the
     * meaning of. It is the same id [SessionRepository.DEFAULT_ID] carries, so
     * the rolls made before sessions existed belong to the first session
     * rather than to nothing.
     */
    const val NO_SESSION: String = SessionRepository.DEFAULT_ID
  }
}
