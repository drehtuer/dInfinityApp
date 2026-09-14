package de.drehtuer.dinfinity.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the menu names the session it is rolling into
 * (`design/dInfinity.dc.html`, option 1q; `docs/statistics.md`).
 *
 * One rule, and it is about restraint: every install starts with exactly one
 * session, and a line naming it would never change and never tell anybody
 * anything.
 */
class MenuHeaderTest {
  @Test
  fun `with one session, the header is just the app`() {
    val header = MenuHeader.of(appName = "dInfinity", activeSession = "First rolls", sessions = 1)

    assertEquals("dInfinity", header.appName)
    assertNull("the only session there is was named", header.session)
  }

  @Test
  fun `with more than one, it says which`() {
    val header = MenuHeader.of(appName = "dInfinity", activeSession = "Tuesday campaign", sessions = 3)

    assertEquals("Tuesday campaign", header.session)
  }

  @Test
  fun `a session nobody could name is not named`() {
    // The list has not arrived yet, or the active id points at a session that
    // has just been deleted. Either way there is no name to print.
    assertNull(MenuHeader.of("dInfinity", activeSession = null, sessions = 5).session)
  }

  @Test
  fun `no sessions at all is not a session`() {
    // Before the first reading of the table.
    assertNull(MenuHeader.of("dInfinity", activeSession = "First rolls", sessions = 0).session)
  }
}
