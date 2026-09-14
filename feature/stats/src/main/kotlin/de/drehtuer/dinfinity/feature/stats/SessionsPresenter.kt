package de.drehtuer.dinfinity.feature.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.data.Session
import de.drehtuer.dinfinity.data.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The buckets statistics are filtered by
 * (`design/dInfinity.dc.html`, option 6c; `docs/statistics.md`, per session).
 *
 * Nothing here starts or stops a session. A session is a label somebody puts
 * on a stretch of rolls, and the app files what is thrown under whichever is
 * active — so there is no button to forget to press, and a session left
 * running overnight is not a thing that can happen.
 *
 * @param activeId which session new rolls are filed under, held above this
 *   screen because it outlives it — the roll screen reads it and so does the
 *   history.
 * @param onActive told when the player picks a different one.
 * @param defaultName what the session every roll can be filed under is called
 *   on a fresh install. Words on a screen, so they come from the resources.
 */
class SessionsPresenter(
  private val repository: SessionRepository,
  private val scope: CoroutineScope,
  private val defaultName: String,
  activeId: String = SessionRepository.DEFAULT_ID,
  private val onActive: (String) -> Unit = {},
) {
  /** What the screen draws. */
  var state: SessionsState by mutableStateOf(SessionsState(activeId = activeId))
    private set

  init {
    scope.launch {
      repository.ensureDefault(defaultName)
      repository.sessions.collect { sessions ->
        // A session deleted under us — on another screen, or by an import —
        // is not one to go on filing rolls under.
        val active = if (sessions.none { it.id == state.activeId }) SessionRepository.DEFAULT_ID else state.activeId
        state = state.copy(sessions = sessions, activeId = active, loaded = true)
      }
    }
  }

  /** New rolls go here from now on. */
  fun activate(sessionId: String) {
    state = state.copy(activeId = sessionId)
    onActive(sessionId)
  }

  /** A session is being named, or renamed. A blank draft is a new one. */
  fun edit(draft: SessionDraft?) {
    state = state.copy(editing = draft)
  }

  /** The name being typed into the draft. */
  fun name(typed: String) {
    state = state.copy(editing = state.editing?.copy(name = typed))
  }

  /**
   * Writes the draft.
   *
   * A new session becomes the active one, because making a session and then
   * having to tap it is two acts where the player meant one.
   */
  fun save() {
    val draft = state.editing ?: return
    if (!draft.savable) return
    val name = draft.name.trim()
    state = state.copy(editing = null)
    scope.launch {
      if (draft.id == null) activate(repository.create(name)) else repository.rename(draft.id, name)
    }
  }

  /** Takes a session away. Its rolls move to the first session, never away. */
  fun delete(sessionId: String) {
    if (sessionId == SessionRepository.DEFAULT_ID) return
    scope.launch {
      repository.delete(sessionId, defaultName)
      if (state.activeId == sessionId) activate(SessionRepository.DEFAULT_ID)
    }
  }
}

/**
 * A session being named.
 *
 * @param id null for one that does not exist yet, which is also what tells the
 *   sheet whether it is making or renaming.
 */
data class SessionDraft(
  val id: String? = null,
  val name: String = "",
) {
  val fresh: Boolean get() = id == null

  /** A session needs a name of its own. */
  val savable: Boolean get() = name.isNotBlank()
}

/** What the sessions screen is showing. */
data class SessionsState(
  val sessions: List<Session> = emptyList(),
  val activeId: String = SessionRepository.DEFAULT_ID,
  val editing: SessionDraft? = null,
  val loaded: Boolean = false,
) {
  /** The one new rolls are filed under, or null before anything has loaded. */
  val active: Session? get() = sessions.firstOrNull { it.id == activeId }
}
