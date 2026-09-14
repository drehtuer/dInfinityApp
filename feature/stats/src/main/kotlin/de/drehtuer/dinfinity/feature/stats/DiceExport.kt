package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.DieSummary
import de.drehtuer.dinfinity.core.stats.FaceTally
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * What every die has done, as a file (`docs/TODO.md`, 4.7;
 * `docs/statistics.md`, "Export and reset").
 *
 * **The two formats carry different things here, and deliberately.** The
 * question somebody exports statistics to answer is *are my dice fair*, and
 * that question is asked of face counts — so the flat form is one row per
 * face, which is the shape a pivot table wants. The full form carries the
 * per-die summary as well, because a streak is the one thing face counts
 * cannot be made to give back: how often a die came up highest in a row is a
 * fact about the order it was thrown in, and the histogram has forgotten that.
 *
 * Everything else in the summary *is* derivable from the counts — the throws
 * are their sum and the mean is their weighted average — so it is not repeated
 * down every row of the flat form.
 *
 * Kept apart from the sharing, which is the part that needs Android, the same
 * way [HistoryExport] is.
 */
object DiceExport {
  /** The columns of the flat form, in order. */
  internal val COLUMNS: List<String> = listOf("set", "die", "sides", "face", "count", "dropped")

  private val json = Json { prettyPrint = true }

  /**
   * The dice in [dice] and the faces in [faces].
   *
   * @param dice one row per die, in the order the screen had them.
   * @param faces every face of every die. Faces whose die is not in [dice] are
   *   dropped rather than written: the export is of what was being looked at,
   *   and a set filtered out of the list has been filtered out of the file.
   * @param called what the file is named after.
   * @param at how an epoch is written. Only for a test to pin.
   */
  fun of(
    dice: List<DieSummary>,
    faces: List<FaceTally>,
    format: ExportFormat,
    called: String,
    at: (Long) -> String = ::isoOf,
  ): ExportFile {
    val wanted = dice.map { it.setId to it.dieId }.toSet()
    val kept = faces.filter { (it.setId to it.dieId) in wanted }
    return ExportFile(
      name = Slug.of(called) + format.extension,
      text = if (format == ExportFormat.Csv) csvOf(kept) else jsonOf(dice, kept, at),
      mediaType = format.mediaType,
    )
  }

  private fun isoOf(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

  /** One row per face, which is what a pivot table wants. */
  private fun csvOf(faces: List<FaceTally>): String =
    buildString {
      appendLine(Csv.row(COLUMNS))
      faces.forEach { face ->
        appendLine(
          Csv.row(
            listOf(
              face.setId,
              face.dieId,
              face.sides.toString(),
              face.faceValue.toString(),
              face.count.toString(),
              face.droppedCount.toString(),
            ),
          ),
        )
      }
    }

  /**
   * Every die, with its summary and its faces under it.
   *
   * A die that has been thrown but has no face rows — which should not happen,
   * and would mean a summary and a histogram that disagree — still gets an
   * empty `faces` array rather than being left out. A file that silently drops
   * a die is a file that hides exactly the disagreement worth seeing.
   */
  private fun jsonOf(
    dice: List<DieSummary>,
    faces: List<FaceTally>,
    at: (Long) -> String,
  ): String {
    val byDie = faces.groupBy { it.setId to it.dieId }
    return json.encodeToString(
      JsonArray.serializer(),
      buildJsonArray {
        dice.forEach { die ->
          add(
            buildJsonObject {
              put("set", die.setId)
              put("die", die.dieId)
              put("sides", die.sides)
              put("throws", die.throws)
              die.mean?.let { put("mean", it) }
              die.standardDeviation?.let { put("standardDeviation", it) }
              put("highestRun", die.highestStreakMax)
              put("lowestRun", die.lowestStreakMax)
              if (die.lastRolledAtEpochMs > 0) put("lastRolled", at(die.lastRolledAtEpochMs))
              put("faces", JsonArray(byDie[die.setId to die.dieId].orEmpty().map(::faceOf)))
            },
          )
        }
      },
    )
  }

  private fun faceOf(face: FaceTally) =
    buildJsonObject {
      put("value", face.faceValue)
      put("count", face.count)
      // Written always rather than only when there were some: "none of these
      // were dropped" is a fact about an advantage roll worth having in the
      // file, and an absent key would read as "nobody counted".
      put("dropped", face.droppedCount)
    }
}
