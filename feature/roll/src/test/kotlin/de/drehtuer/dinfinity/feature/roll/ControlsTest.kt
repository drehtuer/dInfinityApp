package de.drehtuer.dinfinity.feature.roll

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What of the roll screen's controls is over the table ([Controls],
 * decision 83).
 *
 * The rules are small and each is wrong in one corner — a formula drawer left
 * open over dice that are rolling, a tab that came back by itself, a total
 * that landed behind a cleared table — so they are arithmetic and are asked
 * here, every one of them, rather than of a composable.
 */
class ControlsTest {
  @Test
  fun `the screen opens with both menus shut, the formula's tab out and nothing cleared`() {
    val controls = Controls()

    assertFalse(controls.editing)
    assertFalse(controls.picking)
    assertTrue(controls.formulaOut)
    assertFalse(controls.hidden)
  }

  @Test
  fun `opening either menu shuts the other`() {
    val picking = Controls().editing(true).picking(true)
    assertTrue(picking.picking)
    assertFalse(picking.editing)

    val editing = Controls().picking(true).editing(true)
    assertTrue(editing.editing)
    assertFalse(editing.picking)
  }

  @Test
  fun `shutting a menu leaves the other alone`() {
    assertFalse(Controls().picking(true).editing(false).editing)
    assertTrue(Controls().picking(true).editing(false).picking)
    assertTrue(Controls().editing(true).picking(false).editing)
  }

  @Test
  fun `a throw shuts both menus and folds the formula into the dice`() {
    val open = Controls().editing(true)
    val thrown = open.thrown()

    assertFalse("the formula drawer stayed over the dice", thrown.editing)
    assertFalse(Controls().picking(true).thrown().picking)
    assertFalse("the formula's tab stayed on the table", thrown.formulaOut)
    assertTrue(thrown.stowed)
  }

  @Test
  fun `the top stays folded until the dice are opened, and nothing else unfolds it`() {
    // A tab that came back on its own when a total landed would be one more
    // thing sliding over the dice somebody is looking at.
    val thrown = Controls().thrown()

    assertFalse("shutting a shut menu unfolded the top", thrown.picking(false).formulaOut)
    assertFalse("a double tap there and back unfolded the top", thrown.toggled().toggled().formulaOut)
    assertFalse("a second throw unfolded the top", thrown.thrown().formulaOut)

    val opened = thrown.picking(true)
    assertTrue("opening the dice did not bring the formula back", opened.formulaOut)
    assertTrue("shutting the dice again folded the formula away", opened.picking(false).formulaOut)
  }

  @Test
  fun `a double tap clears the table and the next brings back exactly what was there`() {
    val before = Controls().picking(true)

    val cleared = before.toggled()
    assertTrue(cleared.hidden)
    assertTrue("clearing the table forgot the open menu", cleared.picking)

    assertEquals(before, cleared.toggled())
  }

  @Test
  fun `a throw gives a cleared table its controls back, with the top folded`() {
    // A result that arrived behind a cleared table would be a total nobody saw.
    val thrown = Controls().toggled().thrown()

    assertFalse(thrown.hidden)
    assertFalse(thrown.formulaOut)
  }

  @Test
  fun `every one of the four survives a rotation`() {
    val scope = SaverScope { true }
    listOf(
      Controls(),
      Controls(editing = true),
      Controls(picking = true),
      Controls(stowed = true),
      Controls(hidden = true),
      Controls(picking = true, stowed = true, hidden = true),
    ).forEach { controls ->
      val saved = with(Controls.SAVER) { scope.save(controls) }
      assertEquals(controls, Controls.SAVER.restore(requireNotNull(saved)))
    }
  }
}
