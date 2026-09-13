package de.drehtuer.dinfinity.core.collection

import de.drehtuer.dinfinity.core.notation.FormulaParser
import de.drehtuer.dinfinity.core.notation.ParseResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Reads a collection file, and says everything wrong with it
 * (`docs/dice-notation.md`, "Export and import").
 *
 * Through the JSON DOM rather than a deserializer, for the same reason
 * `dicesets/format` reads TOML that way: a file from a stranger is checked
 * field by field with a message naming what is wrong and where, and a
 * deserializer's own exception is not that message.
 *
 * Nothing here writes anything. The reader's whole job is to turn a file into
 * either a [DiceCollection] that is known to be sound or a list of reasons it
 * is not, so that whatever *does* write has no judgement left to make — which
 * is what "an import can never damage what is already there" comes down to in
 * code.
 */
object CollectionReader {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Reads [text] as a collection.
   *
   * @param installedSets the ids of the dice sets that are installed, so a
   *   formula naming one that is not can be flagged. A formula is never
   *   rewritten and never refused for this: the set may be installed tomorrow.
   *   Pass null when it is not known yet, and no such warning is raised.
   */
  fun read(
    text: String,
    installedSets: Set<String>? = null,
  ): CollectionResult {
    // Counted in bytes rather than characters, because the limit is on the
    // file and a name in Japanese is three bytes a character. Checked before
    // anything parses it: a file over the limit is a file nobody asked us to
    // read.
    val size = text.toByteArray(Charsets.UTF_8).size
    if (size > CollectionLimits.MAX_BYTES) {
      return rejected(
        CollectionCode.TooLarge,
        "this file is $size bytes; a collection may be at most ${CollectionLimits.MAX_BYTES}",
      )
    }
    return readJson(text, installedSets)
  }

  private fun readJson(
    text: String,
    installedSets: Set<String>?,
  ): CollectionResult {
    val root =
      runCatching { json.parseToJsonElement(text) }.getOrElse { failure ->
        return rejected(CollectionCode.NotJson, "this is not JSON: ${failure.message.orEmpty()}")
      }
    if (root !is JsonObject) {
      return rejected(CollectionCode.NotACollection, "a collection is a JSON object; this file is not")
    }
    return readObject(root, installedSets)
  }

  private fun readObject(
    root: JsonObject,
    installedSets: Set<String>?,
  ): CollectionResult {
    val errors = mutableListOf<CollectionProblem>()
    val warnings = mutableListOf<CollectionProblem>()

    checkFormat(root, errors)
    val name = root.text("name", "name", errors, CollectionLimits.MAX_NAME).orEmpty()
    val groups = root.array("groups", errors).mapIndexed { i, e -> group(e, "groups[$i]", errors) }
    val rolls = root.array("rolls", errors).mapIndexed { i, e -> roll(e, "rolls[$i]", errors) }

    checkCounts(groups.size, rolls.size, errors)
    val known = checkGroups(groups, errors)
    checkRolls(rolls, known, installedSets, errors, warnings)

    if (errors.isNotEmpty()) return CollectionResult.Rejected(errors)
    val collection =
      DiceCollection(name = name, groups = groups.filterNotNull(), rolls = rolls.filterNotNull())
    if (collection.groups.isEmpty() && collection.rolls.isEmpty()) {
      return rejected(CollectionCode.Empty, "there is nothing in this collection")
    }
    return CollectionResult.Loaded(collection, warnings)
  }

  /**
   * The version of the format this file claims to be.
   *
   * Read on its own and refused when it is not ours, rather than read as best
   * we can: a format that reads a newer file anyway is a format that silently
   * drops whatever the newer version added.
   */
  private fun checkFormat(
    root: JsonObject,
    errors: MutableList<CollectionProblem>,
  ) {
    val format = root.int("format", "format", errors) ?: return
    if (format == CollectionLimits.FORMAT) return
    errors +=
      CollectionProblem(
        CollectionCode.UnknownFormat,
        "this file says format $format; this version reads format ${CollectionLimits.FORMAT}",
        "format",
      )
  }

  private fun checkCounts(
    groups: Int,
    rolls: Int,
    errors: MutableList<CollectionProblem>,
  ) {
    if (groups > CollectionLimits.MAX_GROUPS) {
      errors +=
        CollectionProblem(
          CollectionCode.TooMany,
          "$groups groups; a collection may carry ${CollectionLimits.MAX_GROUPS}",
          "groups",
        )
    }
    if (rolls > CollectionLimits.MAX_ROLLS) {
      errors +=
        CollectionProblem(
          CollectionCode.TooMany,
          "$rolls rolls; a collection may carry ${CollectionLimits.MAX_ROLLS}",
          "rolls",
        )
    }
  }

  /**
   * The group rules, and the ids the rolls may refer to.
   *
   * Duplicate *names* are refused here rather than only at import, so a file
   * that could never be imported says so when it is read: the app does not
   * allow two groups one name either (`docs/dice-notation.md`).
   */
  private fun checkGroups(
    groups: List<CollectionGroup?>,
    errors: MutableList<CollectionProblem>,
  ): Set<String> {
    val ids = mutableSetOf<String>()
    val names = mutableSetOf<String>()
    groups.forEachIndexed { i, group ->
      if (group == null) return@forEachIndexed
      val at = "groups[$i]"
      if (!ids.add(group.id)) {
        errors += CollectionProblem(CollectionCode.DuplicateId, "two groups claim the id \"${group.id}\"", at)
      }
      if (!names.add(group.name.lowercase())) {
        errors += CollectionProblem(CollectionCode.DuplicateName, "two groups are called \"${group.name}\"", at)
      }
    }
    checkNesting(groups, errors)
    return ids
  }

  /** One level, the same rule the app keeps: a deeper tree has no control to draw it. */
  private fun checkNesting(
    groups: List<CollectionGroup?>,
    errors: MutableList<CollectionProblem>,
  ) {
    val parents = groups.filterNotNull().associate { it.id to it.parent }
    groups.forEachIndexed { i, group ->
      val parent = group?.parent ?: return@forEachIndexed
      val at = "groups[$i].parent"
      if (parent !in parents) {
        errors +=
          CollectionProblem(
            CollectionCode.UnknownGroup,
            "\"${group.name}\" is inside \"$parent\", which this collection does not contain",
            at,
          )
      } else if (parents[parent] != null) {
        errors +=
          CollectionProblem(
            CollectionCode.NestedTooDeep,
            "\"${group.name}\" is two groups deep; groups nest one level",
            at,
          )
      }
    }
  }

  private fun checkRolls(
    rolls: List<CollectionRoll?>,
    known: Set<String>,
    installedSets: Set<String>?,
    errors: MutableList<CollectionProblem>,
    warnings: MutableList<CollectionProblem>,
  ) {
    rolls.forEachIndexed { i, roll ->
      if (roll == null) return@forEachIndexed
      if (roll.group !in known) {
        errors +=
          CollectionProblem(
            CollectionCode.UnknownGroup,
            "\"${roll.name}\" is filed in \"${roll.group}\", which this collection does not contain",
            "rolls[$i].group",
          )
      }
      checkFormula(roll, "rolls[$i].formula", installedSets, errors, warnings)
    }
  }

  /**
   * Every formula goes through the same parser the formula field uses.
   *
   * A collection that could carry a formula the app cannot read would be a
   * file that imports and then fails when somebody presses it, in the middle
   * of a game, weeks later.
   *
   * A dice set that is not installed is a *warning*. The set may be installed
   * tomorrow, and rewriting what somebody wrote would be worse than carrying
   * it as written (`docs/dice-notation.md`).
   */
  private fun checkFormula(
    roll: CollectionRoll,
    at: String,
    installedSets: Set<String>?,
    errors: MutableList<CollectionProblem>,
    warnings: MutableList<CollectionProblem>,
  ) {
    val parsed = FormulaParser.parse(roll.formula)
    if (parsed is ParseResult.Failed) {
      errors += CollectionProblem(CollectionCode.BadFormula, parsed.error.message, at)
      return
    }
    if (installedSets == null) return
    (parsed as ParseResult.Parsed)
      .formula
      .diceNodes
      .mapNotNull { it.setRef }
      .distinct()
      .filterNot { it in installedSets }
      .forEach { missing ->
        warnings +=
          CollectionProblem(
            CollectionCode.UnknownDiceSet,
            "\"${roll.name}\" uses the dice set \"$missing\", which is not installed. " +
              "It is kept as written and falls back to the built-in dice when rolled",
            at,
          )
      }
  }

  private fun rejected(
    code: CollectionCode,
    text: String,
  ) = CollectionResult.Rejected(listOf(CollectionProblem(code, text)))
}
