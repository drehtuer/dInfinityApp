package de.drehtuer.dinfinity.core.collection

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Reading one field out of a collection file, and saying what is wrong with it.
 *
 * Kept apart from [CollectionReader] because it is a different kind of code:
 * this is about JSON, and that is about what a collection may contain. The
 * split is the same one `dicesets/format` makes between `TomlFields` and the
 * readers above it.
 *
 * Every one of these takes the list of problems and adds to it rather than
 * throwing, because a collection that fails is refused entirely and somebody
 * fixing a file by hand wants the whole list, not the first line of it.
 */

/** The elements of a list field, or nothing — with a problem raised if it is not a list. */
internal fun JsonObject.array(
  key: String,
  errors: MutableList<CollectionProblem>,
): List<JsonElement> {
  val value = this[key] ?: return emptyList()
  if (value is JsonArray) return value
  errors += wrongType(key, "a list")
  return emptyList()
}

/** A whole number, or null with the reason recorded. */
internal fun JsonObject.int(
  key: String,
  at: String,
  errors: MutableList<CollectionProblem>,
): Int? {
  val raw = this[key]
  val value = (raw as? JsonPrimitive)?.content?.toIntOrNull()
  val problem =
    when {
      raw == null -> missing(at)
      value == null -> wrongType(at, "a whole number")
      else -> null
    }
  if (problem != null) errors += problem
  return value
}

/**
 * Text no longer than [max], or null with the reason recorded.
 *
 * The length limit is here rather than at the call site because it is the same
 * question every time — "is this a name, or is it a payload?" — and a limit
 * that has to be remembered is a limit that gets forgotten once.
 */
internal fun JsonObject.text(
  key: String,
  at: String,
  errors: MutableList<CollectionProblem>,
  max: Int,
): String? {
  val raw = this[key]
  val value = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content
  val problem =
    when {
      raw == null -> missing(at)
      value == null -> wrongType(at, "text")
      value.length > max -> tooLong(at, value.length, max)
      else -> null
    }
  if (problem != null) errors += problem
  return value?.takeIf { problem == null }
}

/** Text that may simply not be there, which is not a problem. */
internal fun JsonObject.optionalText(
  key: String,
  at: String,
  errors: MutableList<CollectionProblem>,
  max: Int,
): String = if (this[key] == null) "" else text(key, at, errors, max).orEmpty()

/** An id, or null with the reason recorded. */
internal fun JsonObject.slug(
  key: String,
  at: String,
  errors: MutableList<CollectionProblem>,
): String? {
  val value = text(key, at, errors, CollectionLimits.MAX_NAME) ?: return null
  if (Slugs.valid(value)) return value
  errors += badId(at, value)
  return null
}

/**
 * An id that may be absent or null.
 *
 * The two mean the same thing — a group at the top level — because the
 * specification's own example writes `"parent": null` and a hand-written file
 * will leave the key out.
 */
internal fun JsonObject.optionalSlug(
  key: String,
  at: String,
  errors: MutableList<CollectionProblem>,
): String? {
  val value = this[key]
  if (value == null || value is JsonNull) return null
  return slug(key, at, errors)
}
