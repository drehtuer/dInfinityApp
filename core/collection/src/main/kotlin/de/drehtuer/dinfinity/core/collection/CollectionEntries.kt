package de.drehtuer.dinfinity.core.collection

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * One group, and one roll, read out of a collection file.
 *
 * The layer between the JSON field readers and the rules: this says what
 * fields a group and a roll have, and nothing about whether the collection
 * they are in makes sense. Both return null when the entry cannot be read at
 * all, having recorded why — a null entry is dropped, and the collection is
 * then refused because the problem list is not empty rather than because
 * something quietly went missing from it.
 */

/** One group, or null with every reason it could not be read recorded. */
internal fun group(
  element: JsonElement,
  at: String,
  errors: MutableList<CollectionProblem>,
): CollectionGroup? {
  val obj = element as? JsonObject
  if (obj == null) {
    errors += wrongType(at, "an object")
    return null
  }
  // Both are read before either is judged, so a group with two things wrong
  // with it reports both.
  val id = obj.slug("id", "$at.id", errors)
  val name = obj.text("name", "$at.name", errors, CollectionLimits.MAX_NAME)
  if (id == null || name == null) return null
  return CollectionGroup(
    id = id,
    name = name,
    icon = obj.optionalText("icon", "$at.icon", errors, CollectionLimits.MAX_ICON),
    parent = obj.optionalSlug("parent", "$at.parent", errors),
  )
}

/** One saved roll, or null with every reason it could not be read recorded. */
internal fun roll(
  element: JsonElement,
  at: String,
  errors: MutableList<CollectionProblem>,
): CollectionRoll? {
  val obj = element as? JsonObject
  if (obj == null) {
    errors += wrongType(at, "an object")
    return null
  }
  val group = obj.slug("group", "$at.group", errors)
  val name = obj.text("name", "$at.name", errors, CollectionLimits.MAX_NAME)
  val formula = obj.text("formula", "$at.formula", errors, CollectionLimits.MAX_FORMULA)
  if (group == null || name == null || formula == null) return null
  return CollectionRoll(
    group = group,
    name = name,
    formula = formula,
    icon = obj.optionalText("icon", "$at.icon", errors, CollectionLimits.MAX_ICON),
    favourite = obj.flag("favourite"),
  )
}
