package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.data.Session
import de.drehtuer.dinfinity.feature.roll.WhatIsThere
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first-launch count line's two numbers
 * (`design/dInfinity.dc.html`, option 9a).
 *
 * `feature/roll` does not know what a saved roll or a session is, so the
 * counts arrive from here. What is worth asserting is the joining: that a line
 * is never drawn from one new number and one old one, and that a fresh install
 * says "0" rather than saying nothing.
 */
class WhatIsThereTest {
  @Test
  fun `a fresh install counts nothing, and says so`() =
    runTest {
      val what = whatIsThere(flowOf(emptyList()), flowOf(emptyList())).first()

      assertEquals(WhatIsThere(savedRolls = 0, sessions = 0), what)
    }

  @Test
  fun `it counts what is really there`() =
    runTest {
      val what = whatIsThere(flowOf(listOf(roll("fireball"), roll("shield"))), flowOf(listOf(session("first")))).first()

      assertEquals(2, what.savedRolls)
      assertEquals(1, what.sessions)
    }

  @Test
  fun `the dice sets are not its business`() =
    runTest {
      // The roll screen already knows how many sets it has; this is only the
      // two counts it cannot know, and it leaves the third where it found it.
      val what = whatIsThere(flowOf(listOf(roll("fireball"))), flowOf(emptyList())).first()

      assertEquals(0, what.sets)
    }

  private fun roll(id: String) = SavedRoll(id = id, groupId = SavedRollGroup.UNFILED_ID, name = id, formula = "1d20")

  private fun session(id: String) = Session(id = id, name = id, startedAtEpochMs = 0L)
}
