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
    assertEquals(0, fireball.sortOrder)
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

  @Test
  fun `the roll's own pin wins over its group's`() {
    // Most specific first (`docs/tables.md`, "Selecting a table"). Fireball is
    // thrown on black felt even in a campaign played on oak.
    val pin = tablePinFor(roll(pin = TablePin("builtin", "felt-black")), group(pin = TablePin("brass", "oak")))

    assertEquals(TablePin("builtin", "felt-black"), pin)
  }

  @Test
  fun `a roll with no pin of its own takes its group's`() {
    val pin = tablePinFor(roll(pin = null), group(pin = TablePin("brass", "oak")))

    assertEquals(TablePin("brass", "oak"), pin)
  }

  @Test
  fun `nothing pinned anywhere is the app default`() {
    // `null` is not "no table" — it is "whatever the app is set to", which is
    // the one thing this function deliberately does not know about.
    assertNull(tablePinFor(roll(pin = null), group(pin = null)))
  }

  @Test
  fun `a group that is not there does not get a say`() {
    // A group deleted while another screen was in front. The roll's own pin
    // still stands, and a roll with none falls through to the app default
    // rather than to nothing at all.
    assertEquals(TablePin("builtin", "felt-black"), tablePinFor(roll(pin = TablePin("builtin", "felt-black")), null))
    assertNull(tablePinFor(roll(pin = null), null))
  }

  @Test
  fun `a throw from a saved roll carries the roll, its group and its table`() {
    // All three together, because all three are decided at the same moment —
    // when somebody taps — and a throw that carried only some of them would
    // have to go back and ask for the rest (`docs/tables.md`).
    val source = SavedRollSource(rollId = "fireball", groupId = "thorin", tablePin = TablePin("brass", "oak"))

    assertEquals("fireball", source.rollId)
    assertEquals("thorin", source.groupId)
    assertEquals(TablePin("brass", "oak"), source.tablePin)
  }

  @Test
  fun `a throw pinned to nothing says so rather than naming a table`() {
    // `null` is the app default, and it is the default here because most
    // throws pin nothing.
    assertNull(SavedRollSource(rollId = "fireball", groupId = "thorin").tablePin)
  }

  private fun roll(pin: TablePin?) =
    SavedRoll(id = "fireball", groupId = "thorin", name = "Fireball", formula = "8d6", tablePin = pin)

  private fun group(pin: TablePin?) = SavedRollGroup(id = "thorin", name = "Thorin", tablePin = pin)
}
