package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SavedRollTest {
  @Test
  fun `a saved roll keeps its formula as text, to be re-validated when shown`() {
    val fireball = SavedRoll(id = "fireball", groupId = "thorin", name = "Fireball", formula = "8d6 [Fire]")
    assertEquals("8d6 [Fire]", fireball.formula)
    assertFalse(fireball.favourite)
    assertEquals(0, fireball.useCount)
    assertNull(fireball.lastUsedAtEpochMs)
  }

  @Test
  fun `a roll with no colour tag follows the accent`() {
    assertNull(SavedRoll(id = "r", groupId = "g", name = "R", formula = "1d20").colorArgb)
  }

  @Test
  fun `a roll can pin its own table, which beats the group's`() {
    val roll =
      SavedRoll(
        id = "fireball",
        groupId = "thorin",
        name = "Fireball",
        formula = "8d6",
        tablePin = TablePin(setId = "builtin", tableId = "felt-black"),
      )
    assertEquals(TablePin("builtin", "felt-black"), roll.tablePin)
  }

  @Test
  fun `a table pin names the package as well as the table`() {
    val brassFelt = TablePin(setId = "brass", tableId = "green-felt")
    val builtinFelt = TablePin(setId = "builtin", tableId = "green-felt")
    assertFalse(brassFelt == builtinFelt)
  }

  @Test
  fun `a top-level group has no parent`() {
    assertNull(SavedRollGroup(id = "dnd", name = "D&D").parentId)
  }

  @Test
  fun `a character group hangs under its game`() {
    val thorin = SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd")
    assertEquals("dnd", thorin.parentId)
  }

  @Test
  fun `groups nest exactly one level deep`() {
    assertEquals(2, SavedRollGroup.MAX_DEPTH)
  }

  @Test
  fun `a fresh install has somewhere to put a roll`() {
    assertEquals("unfiled", SavedRollGroup.UNFILED_ID)
  }

  @Test
  fun `a group can pin a table for a whole campaign`() {
    val strahd =
      SavedRollGroup(id = "strahd", name = "Curse of Strahd", tablePin = TablePin("builtin", "felt-black"))
    assertEquals("felt-black", strahd.tablePin?.tableId)
  }
}
