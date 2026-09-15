package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.StoredDie
import de.drehtuer.dinfinity.data.StoredGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history as a file (`docs/statistics.md`, "Export and reset"; `docs/TODO.md`, 4.8).
 *
 * Two things are being checked and they are not the same thing. One is that
 * the numbers come out right. The other is that **nothing comes out that
 * should not** — which for this file means a seed, and is the reason the
 * export is built from `HistoryEntry` rather than from the row.
 */
class HistoryExportTest {
  @Test
  fun `the flat form is a header and one row per roll`() {
    val file = HistoryExport.of(listOf(roll(total = 7), roll(total = 11)), ExportFormat.Csv, called = "All rolls")

    val lines = file.text.trim().lines()
    assertEquals("a header and two rolls should be three lines", 3, lines.size)
    assertEquals(HistoryExport.COLUMNS.joinToString(","), lines.first())
    assertTrue("the first roll's total is missing: ${lines[1]}", lines[1].contains(",7,"))
  }

  @Test
  fun `a formula with a comma in it does not become two columns`() {
    // `2d6,3d8` is a perfectly good thing to type, and an unquoted comma is a
    // new column in every reader there is.
    val file = HistoryExport.of(listOf(roll(formula = "2d6,3d8")), ExportFormat.Csv, called = "x")

    assertTrue("a comma in a formula was left unquoted: ${file.text}", file.text.contains("\"2d6,3d8\""))
  }

  @Test
  fun `a quote in a session name is doubled, not dropped`() {
    val file =
      HistoryExport.of(
        listOf(roll().copy(sessionId = """Tuesday "the good one"""")),
        ExportFormat.Csv,
        called = "x",
      )

    assertTrue(
      "a quote was not escaped: ${file.text}",
      file.text.contains(""""Tuesday ""the good one""""""),
    )
  }

  @Test
  fun `the dice that counted and the dice that were dropped are told apart`() {
    // `2d20kh1` keeps one and drops one, and a file that mixed them would say
    // the player rolled two dice towards a total they did not.
    val file =
      HistoryExport.of(
        listOf(roll(dice = listOf(die(value = 18), die(value = 3, dropped = true)))),
        ExportFormat.Csv,
        called = "x",
      )

    val row = file.text.trim().lines()[1]
    assertTrue("kept and dropped were not separated: $row", row.endsWith("18,3"))
  }

  @Test
  fun `a roll with no dice in it has empty columns rather than missing ones`() {
    // `4 + 4` is a roll with a total and no dice.
    val file = HistoryExport.of(listOf(roll(formula = "4 + 4", dice = emptyList())), ExportFormat.Csv, called = "x")

    val row = file.text.trim().lines()[1]
    assertEquals("a row was short of columns: $row", HistoryExport.COLUMNS.size, row.split(",").size)
    assertTrue("the last two columns should be empty: $row", row.endsWith(","))
  }

  @Test
  fun `the full form carries the breakdown the flat one flattens`() {
    val file =
      HistoryExport.of(
        listOf(roll(dice = listOf(die(value = 18), die(value = 3, dropped = true)))),
        ExportFormat.Json,
        called = "x",
      )

    assertTrue("the breakdown is missing: ${file.text}", file.text.contains("\"groups\""))
    assertTrue(file.text.contains("\"value\": 18"))
    assertTrue("the dropped die was not marked", file.text.contains("\"kept\": false"))
  }

  @Test
  fun `a set a roll fell back to is named, and one it did not is not`() {
    // "the set you asked for" is news exactly when it is not the set you got.
    val fell =
      HistoryExport.of(
        listOf(roll(groups = listOf(group(setId = "builtin", requested = "brass")))),
        ExportFormat.Json,
        called = "x",
      )
    val didNot =
      HistoryExport.of(
        listOf(roll(groups = listOf(group(setId = "builtin", requested = "builtin")))),
        ExportFormat.Json,
        called = "x",
      )

    assertTrue("a fallback was not recorded", fell.text.contains("requestedSet"))
    assertFalse("a set that was not fallen back to was named anyway", didNot.text.contains("requestedSet"))
  }

  @Test
  fun `a natural high and a natural low are marked, and an ordinary roll is not`() {
    // The two things a player scans a record for. Written only when true, so
    // an ordinary die carries no claim either way.
    val extremes =
      HistoryExport.of(
        listOf(roll(dice = listOf(die(value = 20, naturalMax = true), die(value = 1, naturalMin = true)))),
        ExportFormat.Json,
        called = "x",
      )
    val ordinary = HistoryExport.of(listOf(roll(dice = listOf(die(value = 4)))), ExportFormat.Json, called = "x")

    assertTrue("a natural high was not marked", extremes.text.contains("\"naturalMax\": true"))
    assertTrue("a natural low was not marked", extremes.text.contains("\"naturalMin\": true"))
    assertFalse("an ordinary die was marked as a high", ordinary.text.contains("\"naturalMax\": true"))
    assertFalse("an ordinary die was marked as a low", ordinary.text.contains("naturalMin"))
  }

  @Test
  fun `a roll made from a saved roll says which, and a typed one says nothing`() {
    // What makes "how has Fireball been going" answerable from the file.
    val saved = HistoryExport.of(listOf(roll().copy(savedRollId = "fireball")), ExportFormat.Json, called = "x")
    val typed = HistoryExport.of(listOf(roll()), ExportFormat.Json, called = "x")

    assertTrue("the saved roll was not named", saved.text.contains("\"savedRoll\": \"fireball\""))
    assertFalse("a typed roll claimed a saved roll", typed.text.contains("savedRoll"))
  }

  @Test
  fun `a roll of nothing but arithmetic has no breakdown rather than an empty one`() {
    // `4 + 4` has a total and no dice. An empty `groups` array would say the
    // roll had dice that came to nothing.
    val file = HistoryExport.of(listOf(roll(formula = "4 + 4", dice = emptyList())), ExportFormat.Json, called = "x")

    assertTrue("the total is missing: ${file.text}", file.text.contains("\"total\": 7"))
    assertFalse("an empty breakdown was written: ${file.text}", file.text.contains("groups"))
  }

  @Test
  fun `times are written so that another machine can read them`() {
    // Not the way the screen shows them: a file outlives the phone it was made
    // on, and a localised date is one a spreadsheet has to guess at.
    val file = HistoryExport.of(listOf(roll()), ExportFormat.Csv, called = "x")

    assertTrue("not an ISO instant: ${file.text}", file.text.contains("1970-01-01T00:00:00Z"))
  }

  @Test
  fun `an empty history is a header with nothing under it, and an empty list`() {
    assertEquals(
      HistoryExport.COLUMNS.joinToString(","),
      HistoryExport.of(emptyList(), ExportFormat.Csv, called = "x").text.trim(),
    )
    assertEquals("[]", HistoryExport.of(emptyList(), ExportFormat.Json, called = "x").text.trim())
  }

  @Test
  fun `the file is named after what was exported, and is a path rather than a name`() {
    // A session is named by a person; a file name is a path on whatever the
    // share sheet hands it to.
    assertEquals("tuesday-campaign.csv", HistoryExport.of(emptyList(), ExportFormat.Csv, "Tuesday campaign").name)
    assertEquals("d-d-5e.json", HistoryExport.of(emptyList(), ExportFormat.Json, "D&D / 5e?").name)
    assertEquals("a name of nothing", "rolls.csv", HistoryExport.of(emptyList(), ExportFormat.Csv, "???").name)
  }

  @Test
  fun `the format decides the media type, because the share sheet asks`() {
    assertEquals("text/csv", HistoryExport.of(emptyList(), ExportFormat.Csv, "x").mediaType)
    assertEquals("application/json", HistoryExport.of(emptyList(), ExportFormat.Json, "x").mediaType)
  }

  @Test
  fun `no seed reaches either file, because there is none to reach it`() {
    // The point of building the export from `HistoryEntry` rather than from
    // `RollHistoryRow`. The seed is dropped in the repository, so there is no
    // line here that omits it and no reviewer who has to check for one: the
    // value is not reachable from the type this is written against.
    //
    // Asserted rather than assumed, because "the type has no field" is exactly
    // the kind of thing a later convenience can undo.
    assertTrue(
      "HistoryEntry has grown a seed; the history export can now leak one",
      roll().javaClass.declaredFields.none { it.name.contains("seed", ignoreCase = true) },
    )

    val rolls = listOf(roll(total = 7), roll(total = 11))
    listOf(ExportFormat.Csv, ExportFormat.Json).forEach { format ->
      val text = HistoryExport.of(rolls, format, called = "x").text
      assertFalse("the word seed appears in the $format export: $text", text.contains("seed", ignoreCase = true))
    }
  }

  @Test
  fun `a file that keeps the breakdown keeps one that adds up`() {
    // JSON is the format that keeps the breakdown, and a breakdown missing
    // what the formula added is one whose rows do not reach its total
    // (`docs/statistics.md`, "Export and reset"). Seven from the dice plus
    // four is eleven, and all three numbers are in the file.
    val withFour = roll(total = 11, formula = "2d6 + 4").copy(adjustments = listOf(4L))

    val file = HistoryExport.of(listOf(withFour), ExportFormat.Json, called = "x")

    assertTrue("what the formula added is missing: ${file.text}", file.text.contains("adjustments"))
    assertTrue("the amount is missing: ${file.text}", file.text.contains("4"))
    assertTrue("the total is missing: ${file.text}", file.text.contains("\"total\": 11"))
  }

  @Test
  fun `a roll that added nothing exports the object it always did`() {
    // Not an empty array: that would say the formula had modifiers which came
    // to nothing, the same mistake an empty `groups` would make.
    val file = HistoryExport.of(listOf(roll()), ExportFormat.Json, called = "x")

    assertFalse("an empty list of modifiers was written: ${file.text}", file.text.contains("adjustments"))
  }

  /**
   * One roll.
   *
   * The session and the saved roll are set with `copy` at the two call sites
   * that care, rather than being two more parameters here: the helper is for
   * what every test needs, and a parameter list long enough to trip detekt is
   * a helper that has started describing the type instead of the tests.
   */
  private fun roll(
    total: Long = 7,
    formula: String = "2d6",
    dice: List<StoredDie> = listOf(die(value = 3), die(value = 4)),
    groups: List<StoredGroup>? = null,
  ) = HistoryEntry(
    id = AT,
    atEpochMs = AT,
    sessionId = "default",
    savedRollId = null,
    groupId = null,
    formula = formula,
    total = total,
    groups = groups ?: if (dice.isEmpty()) emptyList() else listOf(group(dice = dice)),
  )

  private fun group(
    setId: String = "builtin",
    requested: String = "builtin",
    dice: List<StoredDie> = listOf(die(value = 3)),
  ) = StoredGroup(notation = "2d6", setId = setId, requestedSetId = requested, subtotal = 7, dice = dice)

  /** The epoch every roll in this file is at: the one instant everybody can read. */
  private companion object {
    const val AT = 0L
  }

  private fun die(
    value: Int,
    dropped: Boolean = false,
    naturalMax: Boolean = false,
    naturalMin: Boolean = false,
  ) = StoredDie(
    dieId = "d6",
    value = value,
    label = value.toString(),
    naturalMax = naturalMax,
    naturalMin = naturalMin,
    notes = if (dropped) setOf(DieNote.Dropped) else emptySet(),
  )
}
