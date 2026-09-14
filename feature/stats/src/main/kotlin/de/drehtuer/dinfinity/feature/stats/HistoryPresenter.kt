package de.drehtuer.dinfinity.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.Session
import de.drehtuer.dinfinity.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Past rolls (`design/dInfinity.dc.html`, option 1x;
 * `docs/statistics.md`, "History").
 *
 * It watches rather than reads, so a roll thrown on the tray appears here
 * without anybody asking — which also means the screen has nothing to refresh
 * and no way to be stale.
 *
 * Which row is open is held here rather than in the list, because it has to
 * survive the list being rebuilt when a roll lands: an expanded breakdown that
 * closed itself every time somebody rolled would be a breakdown nobody could
 * read.
 */
class HistoryPresenter(
  private val history: HistoryRepository,
  private val scope: CoroutineScope,
  private val sessions: SessionRepository? = null,
  private val saved: SavedRollRepository? = null,
  private val limit: Int = HistoryRepository.PAGE,
) {
  /** What the screen draws. */
  var state: HistoryState by mutableStateOf(HistoryState())
    private set

  private var watching: Job? = null

  init {
    watch(HistoryFilter.Everything)
    sessions?.let { repository ->
      scope.launch { repository.sessions.collect { all -> state = state.copy(sessionChoices = all) } }
    }
    saved?.let { repository ->
      scope.launch { repository.all.collect { all -> state = state.copy(rollChoices = all) } }
    }
  }

  /** A row was tapped: open its breakdown, or close the one that is open. */
  fun open(id: Long) {
    state = state.copy(openId = if (state.openId == id) null else id)
  }

  /**
   * Show a different slice of the history (`docs/statistics.md`).
   *
   * The open breakdown is closed with it. A row that was open in one filter is
   * not the row under the finger in the next, and a list that changed
   * underneath an expanded breakdown would be showing somebody the wrong
   * roll's dice.
   */
  fun filterBy(filter: HistoryFilter) {
    if (filter == state.filter) return
    state = state.copy(filter = filter, openId = null, loaded = false)
    watch(filter)
  }

  /**
   * Follows whichever query this filter asks for.
   *
   * One subscription at a time: the previous one is cancelled first, because
   * two collectors writing the same field would race and the loser's list
   * would be the one on screen.
   */
  private fun watch(filter: HistoryFilter) {
    watching?.cancel()
    watching =
      scope.launch {
        rollsFor(filter).collect { rolls ->
          state = state.copy(rolls = rolls, loaded = true)
        }
      }
  }

  private fun rollsFor(filter: HistoryFilter): Flow<List<HistoryEntry>> =
    when (filter) {
      HistoryFilter.Everything -> history.recent(limit)
      is HistoryFilter.InSession -> history.inSession(filter.id, limit)
      is HistoryFilter.OfSavedRoll -> history.forSavedRoll(filter.id)
    }
}

/**
 * Which rolls the history is showing (`docs/statistics.md`; design `1x`).
 *
 * The two ways a player asks the question. *Which of these did I roll on
 * Tuesday* is a session, and *how has Fireball been going* is a saved roll —
 * and they are not combined, because "Fireball on Tuesday" is a report rather
 * than a list, and offering it would put two choosers on a screen whose whole
 * job is to be scrollable.
 */
sealed interface HistoryFilter {
  /** Everything, newest first. */
  data object Everything : HistoryFilter

  /** One session's rolls (design `6c`). */
  data class InSession(
    val id: String,
    val name: String,
  ) : HistoryFilter

  /** One saved roll's throws, wherever they were made. */
  data class OfSavedRoll(
    val id: String,
    val name: String,
  ) : HistoryFilter
}

/**
 * What the history screen is showing.
 *
 * @param loaded false until the database has answered once, so an empty list
 *   is not drawn as "you have never rolled anything".
 * @param openId the row whose breakdown is open, or null. One at a time: a
 *   list of fifty open breakdowns is not a list.
 */
data class HistoryState(
  val rolls: List<HistoryEntry> = emptyList(),
  val openId: Long? = null,
  val loaded: Boolean = false,
  val filter: HistoryFilter = HistoryFilter.Everything,
  val sessionChoices: List<Session> = emptyList(),
  val rollChoices: List<SavedRoll> = emptyList(),
) {
  /**
   * True when there really is nothing, rather than nothing yet — **or** when a
   * filter is hiding everything.
   *
   * The two read differently and the screen says which: "you have never rolled
   * anything" is wrong and discouraging in front of somebody who has rolled
   * hundreds of times and picked a quiet session.
   */
  val empty: Boolean get() = loaded && rolls.isEmpty() && filter == HistoryFilter.Everything

  /** True when a filter is on and has left nothing to show. */
  val filteredToNothing: Boolean get() = loaded && rolls.isEmpty() && filter != HistoryFilter.Everything

  /** True when there is more than one thing to choose between, so a chooser is worth drawing. */
  val choosable: Boolean get() = sessionChoices.size > 1 || rollChoices.isNotEmpty()

  /**
   * The sessions in view, in the order their rolls appear.
   *
   * Used to decide whether session headings are worth drawing at all: with one
   * session — which is every install until Step 4.9 — a heading repeated down
   * the whole list says nothing.
   */
  val sessions: List<String> get() = rolls.map(HistoryEntry::sessionId).distinct()

  /** True when the list spans more than one session, so the headings mean something. */
  val bySession: Boolean get() = sessions.size > 1
}
