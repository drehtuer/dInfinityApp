package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.data.HistoryEntry
import de.drehtuer.dinfinity.data.StoredDie
import de.drehtuer.dinfinity.data.StoredGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** What a file of numbers travels as (`docs/statistics.md`, "Export and reset"). */
enum class ExportFormat(
  val extension: String,
  val mediaType: String,
) {
  /** Everything, breakdown included — the format for keeping. */
  Json(".json", "application/json"),

  /** One row per roll — the format for a spreadsheet. */
  Csv(".csv", "text/csv"),
}

/**
 * Numbers on their way out of the app.
 *
 * @param name what to call the file, extension and all.
 * @param text what is in it.
 * @param mediaType what to tell the share sheet it is.
 */
data class ExportFile(
  val name: String,
  val text: String,
  val mediaType: String,
)

/**
 * The history as a file (`docs/TODO.md`, 4.8; `docs/statistics.md`, "Export and reset").
 *
 * **No seed can leave here, and that is structural rather than remembered.**
 * The export is built from [HistoryEntry], which has no seed on it — the
 * column exists in `roll_history` and is dropped on the way out of the
 * repository. So there is no line in this file that omits it and no reviewer
 * who has to check: the value is not reachable from the type. A past roll is a
 * record, not something to re-run, and a record that carries its seed is a
 * replay waiting to be written.
 *
 * Kept apart from the sharing, which is the part that needs Android: this is
 * the half with the judgements in it, and so the half worth testing.
 *
 * Times are written as **ISO-8601 in UTC** rather than the way the screen
 * shows them. A file that outlives the phone it was made on should not be
 * ambiguous about when anything happened, and a localised string is a date a
 * spreadsheet has to guess at.
 */
object HistoryExport {
  /**
   * The rolls in [entries], in the order they were given.
   *
   * That order is the screen's — newest first. Which rolls arrive here is the
   * caller's decision and it is not the same as which are on screen:
   * [HistoryPresenter.export] passes everything the current filter matches,
   * because a player who has filtered to one session wants that session and
   * not the newest page of it.
   *
   * @param called what the file is named after, usually the filter.
   * @param at how an epoch is written. Only for a test to pin; the default is
   *   the one that ships.
   */
  fun of(
    entries: List<HistoryEntry>,
    format: ExportFormat,
    called: String,
    at: (Long) -> String = ::isoOf,
  ): ExportFile =
    ExportFile(
      name = Slug.of(called) + format.extension,
      text = if (format == ExportFormat.Csv) csvOf(entries, at) else jsonOf(entries, at),
      mediaType = format.mediaType,
    )

  /** The columns of the flat form, in order. */
  internal val COLUMNS: List<String> =
    listOf("when", "session", "formula", "total", "natural_max", "kept", "dropped")

  private val json = Json { prettyPrint = true }

  private fun isoOf(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

  /**
   * One row per roll, which is what a spreadsheet wants.
   *
   * The breakdown is flattened to two columns of values rather than dropped:
   * `kept` is what counted towards the total and `dropped` is what `kh` and
   * `dl` threw away. A roll with no dice in it — `4 + 4` — has both empty,
   * which is true rather than missing.
   */
  private fun csvOf(
    entries: List<HistoryEntry>,
    at: (Long) -> String,
  ): String =
    buildString {
      appendLine(Csv.row(COLUMNS))
      entries.forEach { entry ->
        val dice = entry.groups.flatMap(StoredGroup::dice)
        appendLine(
          Csv.row(
            listOf(
              at(entry.atEpochMs),
              entry.sessionId,
              entry.formula,
              entry.total.toString(),
              entry.hasNaturalMax.toString(),
              valuesOf(dice.filter(StoredDie::kept)),
              valuesOf(dice.filterNot(StoredDie::kept)),
            ),
          ),
        )
      }
    }

  private fun valuesOf(dice: List<StoredDie>): String = dice.joinToString(" ") { it.value.toString() }

  /**
   * Everything, breakdown and all.
   *
   * Written through the JSON DOM rather than by serialising the data classes:
   * what goes in the file is a decision, and a `@Serializable` on
   * `HistoryEntry` would make every field of it a decision already taken —
   * including any added later.
   */
  private fun jsonOf(
    entries: List<HistoryEntry>,
    at: (Long) -> String,
  ): String =
    json.encodeToString(
      JsonArray.serializer(),
      buildJsonArray {
        entries.forEach { entry ->
          add(
            buildJsonObject {
              put("when", at(entry.atEpochMs))
              put("session", entry.sessionId)
              put("formula", entry.formula)
              put("total", entry.total)
              put("naturalMax", entry.hasNaturalMax)
              entry.savedRollId?.let { put("savedRoll", it) }
              if (entry.groups.isNotEmpty()) put("groups", JsonArray(entry.groups.map(::groupOf)))
              // What the formula added, so a file that "keeps the breakdown"
              // keeps a breakdown that adds up. Absent when there is none, so
              // a roll with no modifiers exports the object it always did.
              if (entry.adjustments.isNotEmpty()) {
                put("adjustments", JsonArray(entry.adjustments.map(::JsonPrimitive)))
              }
            },
          )
        }
      },
    )

  private fun groupOf(group: StoredGroup) =
    buildJsonObject {
      put("notation", group.notation)
      put("set", group.setId)
      // Only when it differs, because "the set you asked for" is news exactly
      // when it is not the set you got.
      if (group.fellBack) put("requestedSet", group.requestedSetId)
      put("subtotal", group.subtotal)
      put(
        "dice",
        buildJsonArray {
          group.dice.forEach { die ->
            add(
              buildJsonObject {
                put("die", die.dieId)
                put("value", die.value)
                put("label", die.label)
                put("kept", die.kept)
                if (die.naturalMax) put("naturalMax", true)
                if (die.naturalMin) put("naturalMin", true)
              },
            )
          }
        },
      )
    }
}

/**
 * RFC 4180, which is the only thing "CSV" reliably means.
 *
 * A formula is the reason this is not `joinToString(",")`: `2d6,3d8` is a
 * perfectly good thing to type and a comma in an unquoted field is a new
 * column. A session called `Tuesday "the good one"` is the other reason.
 */
internal object Csv {
  /** The characters that make a field need quoting, per the same RFC. */
  private val NEEDS_QUOTING = charArrayOf(',', '"', '\n', '\r')

  fun row(values: List<String>): String = values.joinToString(",", transform = ::field)

  private fun field(value: String): String =
    if (value.any { it in NEEDS_QUOTING }) {
      "\"" + value.replace("\"", "\"\"") + "\""
    } else {
      value
    }
}

/**
 * `Tuesday campaign` → `tuesday-campaign`.
 *
 * The same rule `core/collection` slugs a collection's name by, and for the
 * same reason: a session is named by a person and a file name is a path on
 * whatever the share sheet hands it to.
 */
internal object Slug {
  private const val FALLBACK = "rolls"
  private const val LONGEST = 60

  fun of(name: String): String {
    val slug =
      name
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .split('-')
        .filter(String::isNotEmpty)
        .joinToString("-")
    return slug.take(LONGEST).trim('-').ifEmpty { FALLBACK }
  }
}
