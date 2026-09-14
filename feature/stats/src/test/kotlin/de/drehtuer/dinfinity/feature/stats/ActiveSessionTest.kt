package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.data.Session
import de.drehtuer.dinfinity.data.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which session new rolls are filed under when a fresh list arrives
 * (`docs/statistics.md`, per session).
 *
 * The rule is one line inside a collector, and it was wrong in a way only a
 * slower machine found: a list that was already in flight when a session was
 * created arrives *after* it, says the new session does not exist, and the
 * session somebody just named is swapped for the default one.
 *
 * So it is a plain function, and these are the four things it has to get right.
 */
class ActiveSessionTest {
  @Test
  fun `a session that is in the list stays active`() {
    assertEquals("tuesday", activeIn(listOf(session("tuesday"), session("default")), "tuesday", awaiting = null))
  }

  @Test
  fun `a session that has been deleted under us falls back to the first`() {
    // Deleted on another screen, or by an import. Not one to go on filing
    // rolls under.
    assertEquals(SessionRepository.DEFAULT_ID, activeIn(listOf(session("default")), "tuesday", awaiting = null))
  }

  @Test
  fun `a session that has just been made is new, not missing`() {
    // The emission that was already in flight. This is the one that broke.
    assertEquals("made-0", activeIn(listOf(session("default")), "made-0", awaiting = "made-0"))
  }

  @Test
  fun `waiting for one session does not protect a different one`() {
    // Making session B must not keep a deleted session A active.
    assertEquals(
      SessionRepository.DEFAULT_ID,
      activeIn(listOf(session("default")), active = "gone", awaiting = "made-0"),
    )
  }

  @Test
  fun `an empty list is still a list`() {
    // Before `ensureDefault` has landed there is nothing at all, and the
    // answer is the session every roll belongs to by default.
    assertEquals(SessionRepository.DEFAULT_ID, activeIn(emptyList(), "tuesday", awaiting = null))
  }

  private fun session(id: String) = Session(id = id, name = id, startedAtEpochMs = 0)
}
