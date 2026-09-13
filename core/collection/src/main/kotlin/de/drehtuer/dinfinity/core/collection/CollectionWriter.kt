package de.drehtuer.dinfinity.core.collection

import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns saved rolls into a collection file
 * (`docs/dice-notation.md`, "Export and import").
 *
 * Two jobs, kept apart because only one of them is about JSON. [collect] takes
 * what the database holds and decides what travels; [write] turns that into
 * text. The first is where every judgement is — which is also why it is the
 * one with the tests that matter.
 *
 * What travels is what somebody wrote: names, formulas, marks, which group
 * things are in, and which rolls are favourites. What does not is everything
 * the app made of it — use counts, timestamps, colour tags of the app's own
 * palette, table pins naming packages the other phone has never heard of, and
 * ids that mean nothing outside this database. A collection somebody opens in
 * a text editor should read like a list of their rolls, not like a dump.
 */
object CollectionWriter {
  private val json = Json { prettyPrint = true }

  /**
   * Gathers [groups] and [rolls] into a collection.
   *
   * @param only the group to export, with its subgroups, or null for
   *   everything. A subgroup exported on its own is lifted to the top level:
   *   its parent is not in the file, and a parent id pointing at nothing would
   *   be refused by the reader that has to take it back.
   * @param name what the collection is called — a file name, a character, a
   *   campaign.
   */
  fun collect(
    groups: List<SavedRollGroup>,
    rolls: List<SavedRoll>,
    only: String? = null,
    name: String,
  ): DiceCollection {
    val taking = taken(groups, only)
    // Ids are re-slugged from names rather than carried over, because the
    // database's ids are whatever made them — a UUID from the editor, a slug
    // from an earlier import — and a collection should read the same either way.
    val slugs = mutableMapOf<String, String>()
    val used = mutableSetOf<String>()
    taking.forEach { group ->
      val slug = Slugs.of(group.name, used)
      used += slug
      slugs[group.id] = slug
    }
    val roots = taking.map { it.id }.toSet()
    return DiceCollection(
      name = name.take(CollectionLimits.MAX_NAME),
      groups =
        taking.map { group ->
          CollectionGroup(
            id = slugs.getValue(group.id),
            name = group.name.take(CollectionLimits.MAX_NAME),
            icon = group.icon.take(CollectionLimits.MAX_ICON),
            // A parent outside what is being exported is dropped rather than
            // written as a dangling id.
            parent = group.parentId?.takeIf { it in roots }?.let(slugs::getValue),
          )
        },
      rolls =
        rolls
          .filter { it.groupId in roots }
          .map { roll ->
            CollectionRoll(
              group = slugs.getValue(roll.groupId),
              name = roll.name.take(CollectionLimits.MAX_NAME),
              formula = roll.formula.take(CollectionLimits.MAX_FORMULA),
              icon = roll.icon.take(CollectionLimits.MAX_ICON),
              favourite = roll.favourite,
            )
          },
    )
  }

  /**
   * The JSON, pretty-printed.
   *
   * Pretty rather than compact because a collection is a file people read and
   * edit — the format exists so a community can keep a repository of them —
   * and the size limit is a megabyte, which no hand-written collection will
   * ever approach.
   */
  fun write(collection: DiceCollection): String =
    json.encodeToString(
      JsonElement.serializer(),
      JsonObject(
        buildMap {
          put("format", JsonPrimitive(collection.format))
          put("name", JsonPrimitive(collection.name))
          put("groups", JsonArray(collection.groups.map(::groupOf)))
          put("rolls", JsonArray(collection.rolls.map(::rollOf)))
        },
      ),
    )

  /**
   * The groups being exported: one group with its children, or all of them.
   *
   * Exporting a parent takes its children with it, because a group that names
   * a character inside a campaign is not much use without the campaign — and
   * `docs/dice-notation.md` says so in as many words: "export a group (with
   * its subgroups)".
   */
  private fun taken(
    groups: List<SavedRollGroup>,
    only: String?,
  ): List<SavedRollGroup> {
    if (only == null) return groups
    val root = groups.firstOrNull { it.id == only } ?: return emptyList()
    return listOf(root) + groups.filter { it.parentId == only }
  }

  private fun groupOf(group: CollectionGroup): JsonObject =
    JsonObject(
      buildMap {
        put("id", JsonPrimitive(group.id))
        put("name", JsonPrimitive(group.name))
        if (group.icon.isNotEmpty()) put("icon", JsonPrimitive(group.icon))
        put("parent", group.parent?.let(::JsonPrimitive) ?: JsonNull)
      },
    )

  private fun rollOf(roll: CollectionRoll): JsonObject =
    JsonObject(
      buildMap {
        put("group", JsonPrimitive(roll.group))
        put("name", JsonPrimitive(roll.name))
        if (roll.icon.isNotEmpty()) put("icon", JsonPrimitive(roll.icon))
        put("formula", JsonPrimitive(roll.formula))
        if (roll.favourite) put("favourite", JsonPrimitive(true))
      },
    )
}
