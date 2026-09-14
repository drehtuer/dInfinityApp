package de.drehtuer.dinfinity.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
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
  scope: CoroutineScope,
  private val limit: Int = HistoryRepository.PAGE,
) {
  /** What the screen draws. */
  var state: HistoryState by mutableStateOf(HistoryState())
    private set

  init {
    scope.launch {
      history.recent(limit).collect { rolls ->
        state = state.copy(rolls = rolls, loaded = true)
      }
    }
  }

  /** A row was tapped: open its breakdown, or close the one that is open. */
  fun open(id: Long) {
    state = state.copy(openId = if (state.openId == id) null else id)
  }
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
) {
  /** True when there really is nothing, rather than nothing yet. */
  val empty: Boolean get() = loaded && rolls.isEmpty()

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
