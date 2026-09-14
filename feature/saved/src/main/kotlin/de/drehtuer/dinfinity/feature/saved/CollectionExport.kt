package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.collection.CollectionWriter
import de.drehtuer.dinfinity.core.collection.Slugs
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup

/**
 * A collection on its way out of the app
 * (`docs/dice-notation.md`, "Export and import").
 *
 * @param name what to call the file, extension and all.
 * @param json what is in it.
 */
data class CollectionFile(
  val name: String,
  val json: String,
)

/**
 * Deciding what to export and what to call it.
 *
 * Kept apart from the sharing, which is the part that needs Android: this is
 * the half with the judgements in it, and so the half worth testing. What is
 * *in* the file is `core/collection`'s business; what goes into it and under
 * what name is this.
 */
object CollectionExport {
  /** What a collection file is called, so one can be recognised at a glance. */
  const val EXTENSION: String = ".dinfinity.json"

  /** The media type a collection travels as. */
  const val MEDIA_TYPE: String = "application/json"

  /**
   * Gathers a collection.
   *
   * @param only the group to export, with its subgroups, or null for
   *   everything.
   * @param called what the collection is named inside the file — the group's
   *   own name when one group is being exported, and [everything] when it is
   *   the lot.
   */
  fun of(
    groups: List<SavedRollGroup>,
    rolls: List<SavedRoll>,
    only: String? = null,
    called: String,
  ): CollectionFile {
    val collection = CollectionWriter.collect(groups = groups, rolls = rolls, only = only, name = called)
    return CollectionFile(name = fileNameOf(called), json = CollectionWriter.write(collection))
  }

  /**
   * `Curse of Strahd` → `curse-of-strahd.dinfinity.json`.
   *
   * Slugged rather than used as typed, because the name is a name and a file
   * name is a file name: a group called `D&D / 5e?` is a perfectly good group
   * and a poor path on most of the places a share sheet can send it.
   */
  fun fileNameOf(called: String): String = Slugs.of(called, fallback = "collection") + EXTENSION
}
