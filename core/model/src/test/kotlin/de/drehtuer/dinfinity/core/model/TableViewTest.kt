package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * How far the camera leans over the table (design: the Table view row).
 *
 * Two positions, a stable id each, and a fallback: the id is what goes on
 * disk, and anything read back that nobody wrote has to land somewhere sane
 * rather than throw on the way to the first frame.
 */
class TableViewTest {
  @Test
  fun `a new install looks straight down`() {
    // The default, and the reason the setting exists: a leaning shot on a tall
    // phone spends a large share of the frame on the wooden rim
    // (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
    assertEquals(TableView.StraightDown, TableView.Default)
    assertEquals(TableView.StraightDown, AppSettings().tableView)
  }

  @Test
  fun `a stored choice comes back`() {
    TableView.entries.forEach { assertEquals(it, TableView.ofId(it.id)) }
  }

  @Test
  fun `anything unrecognised looks straight down`() {
    // A preferences file written by a newer version, or edited by hand. The
    // app goes on working rather than refusing to draw a table.
    assertEquals(TableView.Default, TableView.ofId("isometric"))
    assertEquals(TableView.Default, TableView.ofId(null))
    assertEquals(TableView.Default, TableView.ofId(""))
  }

  @Test
  fun `the two ids are two ids, and neither is the enum's own name`() {
    // Storage is never `name`: renaming a constant would then silently reset
    // everybody's setting on the next launch.
    assertEquals("straight_down", TableView.StraightDown.id)
    assertEquals("angled", TableView.Angled.id)
    assertNotEquals(TableView.StraightDown.id, TableView.Angled.id)
  }

  @Test
  fun `the lean is part of the settings, and changing it leaves the rest alone`() {
    val before = AppSettings(rounding = Rounding.Up)
    val after = before.copy(tableView = TableView.Angled)

    assertEquals(TableView.Angled, after.tableView)
    assertEquals(Rounding.Up, after.rounding)
    assertEquals(TableView.StraightDown, before.tableView)
    assertNotEquals(before, after)
  }
}
