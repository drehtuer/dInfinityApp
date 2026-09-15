package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A roll's breakdown, as it is stored (`docs/statistics.md`, "Storage").
 *
 * Written whole rather than normalised into rows, and the reason is not
 * convenience. **A roll's breakdown means what it meant then.** Normalising it
 * would let a set uninstalled last week quietly rewrite last week's rolls, and
 * a history that changes when you install something is not a history.
 *
 * So everything the history screen shows is *in here*: the labels the faces
 * carried, which dice were dropped, which came from an explosion, which showed
 * a natural maximum. Nothing has to be looked up to draw a past roll, which
 * also means nothing can be looked up wrong.
 *
 * The seed is not part of it. Determinism is for tests and bug reports, not a
 * feature (`docs/architecture.md`, decision 13).
 */
object Breakdown {
  private val json = Json { ignoreUnknownKeys = true }

  /** Turns a finished roll's groups into the text stored beside it. */
  fun of(result: RollResult): String =
    json.encodeToString(
      JsonElement.serializer(),
      JsonObject(
        buildMap {
          put("total", JsonPrimitive(result.total))
          put("rounding", JsonPrimitive(result.rounding.name))
          result.label?.let { put("label", JsonPrimitive(it)) }
          put("groups", JsonArray(result.groups.map(::groupOf)))
          // Only when there are any, so a roll with no modifiers is the same
          // text it has always been.
          result.adjustments
            .takeIf { it.isNotEmpty() }
            ?.let { put("adjustments", JsonArray(it.map(::JsonPrimitive))) }
        },
      ),
    )

  /**
   * Reads one back.
   *
   * Lenient on purpose: a breakdown written by an older version is still a
   * record of a roll somebody made, and a history screen that threw away
   * everything it could not parse perfectly would be a history screen that
   * loses rolls when the app is updated. Anything unreadable comes back empty,
   * and the row's total — which is stored in its own column — still draws.
   *
   * **A roll written before the modifiers were recorded has none**, and that
   * is the truth about it rather than a gap to paper over: nobody knows what
   * `3d6 + 4` added, because at the time nothing wrote it down. Its rows will
   * not add up to its total, exactly as they do not today.
   */
  fun read(text: String): StoredBreakdown =
    runCatching {
      val root = json.parseToJsonElement(text).jsonObject
      StoredBreakdown(
        groups =
          root["groups"]
            ?.jsonArray
            ?.map { group(it.jsonObject) }
            .orEmpty(),
        adjustments =
          root["adjustments"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
            .orEmpty(),
      )
    }.getOrDefault(StoredBreakdown())

  private fun groupOf(group: RolledGroup): JsonObject =
    JsonObject(
      buildMap {
        put("notation", JsonPrimitive(group.notation))
        put("set", JsonPrimitive(group.setId))
        if (group.fellBack) put("asked", JsonPrimitive(group.requestedSetId))
        put("subtotal", JsonPrimitive(group.subtotal))
        put("dice", JsonArray(group.dice.map(::dieOf)))
      },
    )

  private fun dieOf(die: RolledDie): JsonObject =
    JsonObject(
      buildMap {
        put("die", JsonPrimitive(die.dieId))
        put("value", JsonPrimitive(die.value))
        // Only when it differs, because on most dice it is the value again and
        // fifty rolls a session is a lot of rows to store twice.
        if (die.label != die.value.toString()) put("label", JsonPrimitive(die.label))
        if (die.naturalMax) put("max", JsonPrimitive(true))
        if (die.naturalMin) put("min", JsonPrimitive(true))
        if (die.notes.isNotEmpty()) put("notes", JsonArray(die.notes.map { JsonPrimitive(it.name) }))
      },
    )

  private fun group(obj: JsonObject): StoredGroup =
    StoredGroup(
      notation = obj.text("notation"),
      setId = obj.text("set"),
      requestedSetId = obj["asked"]?.jsonPrimitive?.content ?: obj.text("set"),
      subtotal = obj["subtotal"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
      dice = obj["dice"]?.jsonArray?.map { die(it.jsonObject) }.orEmpty(),
    )

  private fun die(obj: JsonObject): StoredDie {
    val value = obj["value"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    return StoredDie(
      dieId = obj.text("die"),
      value = value,
      label = obj["label"]?.jsonPrimitive?.content ?: value.toString(),
      naturalMax = obj.flag("max"),
      naturalMin = obj.flag("min"),
      notes =
        obj["notes"]
          ?.jsonArray
          ?.mapNotNull { note -> DieNote.entries.firstOrNull { it.name == note.jsonPrimitive.content } }
          ?.toSet()
          .orEmpty(),
    )
  }

  private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

  private fun JsonObject.flag(key: String): Boolean =
    this[key]
      ?.jsonPrimitive
      ?.content
      ?.toBooleanStrictOrNull() ?: false
}

/**
 * A roll's breakdown as it was written down.
 *
 * The groups and the numbers the formula added, which together are what the
 * history screen draws under the total — the same two things the result sheet
 * shows the moment a roll lands (`docs/dice-notation.md`, "Evaluation",
 * step 7).
 *
 * [adjustments] is empty both for a formula that added nothing and for a roll
 * recorded before they were written down. Those are not the same thing, and
 * nothing here pretends to tell them apart: the second is a roll whose
 * modifiers nobody knows, and the honest answer is the one the rows give — a
 * breakdown that does not add up to its total.
 */
data class StoredBreakdown(
  val groups: List<StoredGroup> = emptyList(),
  val adjustments: List<Long> = emptyList(),
)

/**
 * One group of a stored breakdown.
 *
 * Its own type rather than [RolledGroup], because it is not the same thing: a
 * `RolledGroup` is part of a roll that just happened and carries live `Die`
 * identities, and this is a record of one that happened before, which may name
 * dice nothing can look up any more.
 */
data class StoredGroup(
  val notation: String,
  val setId: String,
  val requestedSetId: String,
  val subtotal: Long,
  val dice: List<StoredDie>,
) {
  /** True when this group's dice did not come from the set the formula asked for. */
  val fellBack: Boolean get() = setId != requestedSetId

  /** The dice that counted towards [subtotal]. */
  val kept: List<StoredDie> get() = dice.filter(StoredDie::kept)
}

/** One die of a stored breakdown, as it landed then. */
data class StoredDie(
  val dieId: String,
  val value: Int,
  val label: String,
  val naturalMax: Boolean = false,
  val naturalMin: Boolean = false,
  val notes: Set<DieNote> = emptySet(),
) {
  /** False for a die that was dropped, which the history shows struck through. */
  val kept: Boolean get() = DieNote.Dropped !in notes
}
