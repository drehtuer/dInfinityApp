package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.FaceTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every die has done, as a file (`docs/statistics.md`, "Export and
 * reset"; `docs/TODO.md`, 4.7).
 *
 * The file exists so somebody can check the app's central claim somewhere
 * other than in the app — so what these tests are mostly about is the file
 * being *enough* to do that with, and being honest about what it is not.
 */
class DiceExportTest {
  @Test
  fun `the flat form is one row per face, which is what a pivot table wants`() {
    val file = DiceExport.of(dice = listOf(d6()), faces = facesOf(1, 2, 3, 4, 5, 6), format = Csv, called = "x")

    val lines = file.text.trim().lines()
    assertEquals("a header and six faces", 7, lines.size)
    assertEquals(DiceExport.COLUMNS.joinToString(","), lines.first())
    assertEquals("builtin,d6,6,1,10,0", lines[1])
  }

  @Test
  fun `the dropped ones are counted separately, because an advantage roll asks about them`() {
    // `2d20kh1` throws both and keeps one. The die was still thrown, so it
    // still counts — but which were thrown away is the question somebody
    // wondering about their advantage rolls is asking.
    val file =
      DiceExport.of(
        dice = listOf(d6()),
        faces = listOf(tally(faceValue = 1, count = 10, dropped = 4)),
        format = Csv,
        called = "x",
      )

    assertEquals("builtin,d6,6,1,10,4", file.text.trim().lines()[1])
  }

  @Test
  fun `the full form carries the runs, which the counts cannot give back`() {
    // Everything else in the summary is derivable from the face counts: the
    // throws are their sum, the mean their weighted average. A run is a fact
    // about the order the die was thrown in, and a histogram has forgotten it.
    val file =
      DiceExport.of(
        dice = listOf(d6(highestStreakMax = 4, lowestStreakMax = 2)),
        faces = facesOf(1, 2, 3, 4, 5, 6),
        format = Json,
        called = "x",
      )

    assertTrue("the highest run is missing: ${file.text}", file.text.contains("\"highestRun\": 4"))
    assertTrue("the lowest run is missing: ${file.text}", file.text.contains("\"lowestRun\": 2"))
    assertTrue("the faces are missing", file.text.contains("\"faces\""))
  }

  @Test
  fun `a die nobody has thrown has no mean rather than a mean of zero`() {
    // A mean of zero would be a claim about a die that has done nothing.
    val file = DiceExport.of(dice = listOf(d6(throws = 0, sum = 0)), faces = emptyList(), format = Json, called = "x")

    assertFalse("a die with no throws was given a mean: ${file.text}", file.text.contains("\"mean\""))
    assertTrue("the die itself is missing", file.text.contains("\"die\": \"d6\""))
  }

  @Test
  fun `a die with a summary but no faces is still written, because that disagreement matters`() {
    // It should not happen. If it does, a file that silently dropped the die
    // would hide exactly the thing worth seeing.
    val file = DiceExport.of(dice = listOf(d6()), faces = emptyList(), format = Json, called = "x")

    assertTrue("the die was dropped: ${file.text}", file.text.contains("\"die\": \"d6\""))
    assertTrue("an empty face list was not written", file.text.contains("\"faces\": []"))
  }

  @Test
  fun `faces of a die that is not in the list are not smuggled into the file`() {
    // The export is of what was being looked at. A set filtered out of the
    // list has been filtered out of the file, faces included.
    val file =
      DiceExport.of(
        dice = listOf(d6()),
        faces = facesOf(1, 2) + tally(setId = "brass", faceValue = 1),
        format = Csv,
        called = "x",
      )

    assertFalse("a filtered-out set reached the file: ${file.text}", file.text.contains("brass"))
    assertEquals(
      "a header and two faces",
      3,
      file.text
        .trim()
        .lines()
        .size,
    )
  }

  @Test
  fun `nothing rolled yet is a header with nothing under it, and an empty list`() {
    assertEquals(
      DiceExport.COLUMNS.joinToString(","),
      DiceExport.of(emptyList(), emptyList(), Csv, called = "x").text.trim(),
    )
    assertEquals("[]", DiceExport.of(emptyList(), emptyList(), Json, called = "x").text.trim())
  }

  @Test
  fun `when a die was last rolled is written so another machine can read it`() {
    // ISO-8601 in UTC, like the history's: a file outlives the phone it was
    // made on. A die that has never been rolled says nothing rather than 1970.
    val rolled =
      DiceExport.of(listOf(d6().copy(lastRolledAtEpochMs = 86_400_000)), facesOf(1), Json, called = "x")
    val never = DiceExport.of(listOf(d6()), facesOf(1), Json, called = "x")

    assertTrue("not an ISO instant: ${rolled.text}", rolled.text.contains("1970-01-02T00:00:00Z"))
    assertFalse("a die never rolled claimed a date: ${never.text}", never.text.contains("lastRolled"))
  }

  @Test
  fun `the file is named after the set it was filtered to, and is a path`() {
    // "&" is a separator, not a word: the slug is a path, not a translation.
    assertEquals("brass-bone.csv", DiceExport.of(emptyList(), emptyList(), Csv, "Brass & Bone").name)
    assertEquals("dice.json", DiceExport.of(emptyList(), emptyList(), Json, "dice").name)
  }

  @Test
  fun `a die's name can carry a comma without becoming two columns`() {
    val file =
      DiceExport.of(
        dice = listOf(d6(dieId = "d6,special")),
        faces = listOf(tally(dieId = "d6,special", faceValue = 1)),
        format = Csv,
        called = "x",
      )

    assertTrue("a comma was left unquoted: ${file.text}", file.text.contains("\"d6,special\""))
  }

  @Test
  fun `nothing about how the dice were thrown reaches the file`() {
    // The statistics are what the dice landed on. A seed belongs to a throw,
    // and this file is not about throws — so the same rule the history export
    // keeps structurally is worth asserting here too.
    val file = DiceExport.of(listOf(d6()), facesOf(1, 2, 3), Json, called = "x")

    assertFalse("the word seed reached the file: ${file.text}", file.text.contains("seed", ignoreCase = true))
  }

  private fun d6(
    dieId: String = "d6",
    throws: Long = 60,
    sum: Long = 210,
    highestStreakMax: Int = 0,
    lowestStreakMax: Int = 0,
  ) = DieSummary(
    setId = "builtin",
    dieId = dieId,
    sides = 6,
    throws = throws,
    sum = sum,
    sumOfSquares = 910,
    highestStreakMax = highestStreakMax,
    lowestStreakMax = lowestStreakMax,
    lastRolledAtEpochMs = 0,
  )

  private fun facesOf(vararg values: Int) = values.map { tally(faceValue = it) }

  private fun tally(
    setId: String = "builtin",
    dieId: String = "d6",
    faceValue: Int,
    count: Long = 10,
    dropped: Long = 0,
  ) = FaceTally(setId = setId, dieId = dieId, sides = 6, faceValue = faceValue, count = count, droppedCount = dropped)

  private companion object {
    val Csv = ExportFormat.Csv
    val Json = ExportFormat.Json
  }
}
