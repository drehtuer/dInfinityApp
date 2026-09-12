package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DiceSet

/**
 * What the validator found, as lines a person can read
 * (`docs/dice-sets.md`, "Validation"; design option 6b).
 *
 * Errors are collected rather than thrown one at a time. A set that fails is
 * rejected entirely, so there is no reason to stop at the first problem and
 * every reason not to: an author fixing a set wants the whole list, and the
 * screen that shows a failed install has room for it.
 */
sealed interface ValidationResult {
  /** Anything the validator wants to say, whether or not the set installs. */
  val messages: List<ValidationMessage>

  /** The warnings, which a valid set may perfectly well have. */
  val warnings: List<ValidationMessage> get() = messages.filter { it.severity == Severity.Warning }

  /**
   * The package installs. [warnings] may still have things in it — a clamped
   * physics value, a missing standard die, a truncated label.
   */
  data class Valid(
    val set: DiceSet,
    override val messages: List<ValidationMessage> = emptyList(),
  ) : ValidationResult

  /**
   * The package does not install, and this is everything wrong with it. There
   * is no partially installed state (`docs/dice-sets.md`, rule 2).
   */
  data class Rejected(
    override val messages: List<ValidationMessage>,
  ) : ValidationResult {
    val errors: List<ValidationMessage> get() = messages.filter { it.severity == Severity.Error }
  }
}

/** One line of the report. */
data class ValidationMessage(
  val severity: Severity,
  val code: ValidationCode,
  val text: String,
  val file: String,
  val line: Int? = null,
) {
  /** `diceset.toml:14: error: no shape called "rhombic-triacontahedron"` */
  override fun toString(): String =
    buildString {
      append(file)
      line?.let { append(":$it") }
      append(": ")
      append(severity.name.lowercase())
      append(": ")
      append(text)
    }
}

/** Whether a message stops the install. */
enum class Severity {
  /** The package is rejected. */
  Error,

  /** The package installs, and the user is told. */
  Warning,
}

/**
 * What kind of thing the validator found.
 *
 * The code is for tests and screens to match on; the text is for the person
 * reading the report. Every entry here is a row of the lists in
 * `docs/dice-sets.md`, "Validation".
 */
enum class ValidationCode {
  /** No `diceset.toml` where one was expected. */
  MissingFile,

  /** The file is not TOML, or not TOML this parser can read. */
  SyntaxError,

  /** `format` is missing, is not a number, or names a schema newer than this app. */
  UnsupportedFormat,

  /** A required key is not there. */
  MissingField,

  /** A key is there but is the wrong kind of thing. */
  WrongType,

  /** An id that is not a slug of the right length. */
  BadSlug,

  /** Two dice, or two tables, with the same id. */
  DuplicateId,

  /** A shape outside the catalogue, `mesh` included (`docs/dice-sets.md`). */
  UnknownShape,

  /** `faces` does not have one entry per face of the shape. */
  FaceCountMismatch,

  /** A face value outside the range a set file may use. */
  FaceValueOutOfRange,

  /** A `read`, `sound` or `light` naming something the app does not have. */
  UnknownPreset,

  /** A file reference that leaves the package, is absolute, or has the wrong extension. */
  BadFileReference,

  /** A referenced file is not in the package. */
  ReferencedFileMissing,

  /** A file, or the package's textures together, over the byte limit. */
  FileTooLarge,

  /** A texture wider or taller than the limit, read from its header. */
  TextureTooLarge,

  /** A texture whose bytes are not a picture of the kind its name claims. */
  TextureUnreadable,

  /** A physics or material number that is not finite, which the solver must never see. */
  NotFinite,

  /** A number outside its range, brought back inside it. Always a warning. */
  Clamped,

  /** A key the app does not know, ignored so the format can grow. Always a warning. */
  UnknownKey,

  /** A label longer than a face can show, cut down. Always a warning. */
  LabelTruncated,

  /** A standard die the set does not define; notation will fall back. Always a warning. */
  StandardDieMissing,

  /** A texture whose cells would not come out square in the atlas grid. Always a warning. */
  AtlasNotSquare,
}
