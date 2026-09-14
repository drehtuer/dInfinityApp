package de.drehtuer.dinfinity.core.collection

/**
 * What reading a collection came to
 * (`docs/dice-notation.md`, "Export and import").
 *
 * Errors are collected rather than thrown one at a time. A collection that
 * fails is refused **entirely** — no merge, nothing deleted, nothing half
 * imported — so there is no reason to stop at the first problem and every
 * reason not to: somebody fixing a file by hand wants the whole list.
 *
 * There is no partially loaded state, which is what makes the import rule
 * ("an import can never damage what is already there") something the types
 * enforce rather than something the screens remember to do.
 */
sealed interface CollectionResult {
  /**
   * The file reads. [warnings] may still have things in it — a formula naming
   * a dice set that is not installed is kept and flagged, because the set may
   * be installed tomorrow and rewriting what somebody wrote would be worse.
   */
  data class Loaded(
    val collection: DiceCollection,
    val warnings: List<CollectionProblem> = emptyList(),
  ) : CollectionResult

  /** The file does not read, and this is everything wrong with it. */
  data class Rejected(
    val errors: List<CollectionProblem>,
  ) : CollectionResult
}

/**
 * One line of the report.
 *
 * @param at where in the file it is, as a path a person can follow back into
 *   the JSON: `rolls[3].formula`, `groups[0].parent`, or `""` for the file
 *   itself. JSON has no line numbers once it is parsed, and a path is what a
 *   text editor's search box takes.
 */
data class CollectionProblem(
  val code: CollectionCode,
  val text: String,
  val at: String = "",
) {
  /** `rolls[3].formula: a formula that does not read: "3d" ends after "d"` */
  override fun toString(): String = if (at.isEmpty()) text else "$at: $text"
}

/**
 * What kind of thing reading found.
 *
 * The code is for tests and screens to match on; the text is for the person
 * reading the report. A screen that wants to say something of its own about a
 * particular failure — the duplicate group name, which is the one an import
 * has a whole message for — matches on the code.
 */
enum class CollectionCode {
  /** The file is bigger than [CollectionLimits.MAX_BYTES]. Not parsed at all. */
  TooLarge,

  /** Not JSON. */
  NotJson,

  /** JSON, but not an object with the fields a collection has. */
  NotACollection,

  /** A `format` this app does not read. */
  UnknownFormat,

  /** A field a collection cannot do without is not there. */
  MissingField,

  /** A field is there but is the wrong kind of thing. */
  WrongType,

  /** A name, icon or formula longer than it may be. */
  TooLong,

  /** More rolls or groups than a collection may carry. */
  TooMany,

  /** An id that is not a slug, so nothing could refer to it reliably. */
  BadId,

  /** Two groups claiming the same id. */
  DuplicateId,

  /** Two groups claiming the same name, which the app does not allow either. */
  DuplicateName,

  /** A roll filed in a group the collection does not contain. */
  UnknownGroup,

  /** A group inside a group that is itself inside one. */
  NestedTooDeep,

  /** A formula the parser refuses. */
  BadFormula,

  /** Nothing in it at all. */
  Empty,

  /** A formula naming a dice set that is not installed. A warning, never an error. */
  UnknownDiceSet,
}

/**
 * The problems a file can have that are about JSON rather than about dice.
 *
 * Beside the type they build rather than beside the code that raises them, so
 * that the words a person reads are all in one place — a report whose phrasing
 * drifts between two files is a report that reads like two programs.
 */
internal fun missing(at: String) = CollectionProblem(CollectionCode.MissingField, "\"$at\" is not there", at)

internal fun wrongType(
  at: String,
  wanted: String,
) = CollectionProblem(CollectionCode.WrongType, "\"$at\" should be $wanted", at)

internal fun tooLong(
  at: String,
  was: Int,
  max: Int,
) = CollectionProblem(CollectionCode.TooLong, "\"$at\" is $was characters; at most $max", at)

internal fun badId(
  at: String,
  text: String,
) = CollectionProblem(
  CollectionCode.BadId,
  "\"$text\" is not an id: use lower-case letters, digits, \"-\" and \"_\"",
  at,
)
