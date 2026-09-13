package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/**
 * Reads and checks a dice-set package (`docs/dice-sets.md`, "Validation").
 *
 * The same code runs for a URL install, a local folder or zip, a face designer
 * export and the bundled set. There is no privileged path — a validator the
 * built-in set skips is a validator the built-in set is never tested on, and
 * the built-in set is the one that has to work.
 *
 * A package either installs or is rejected whole, with every error in the
 * report rather than only the first (`docs/dice-sets.md`, rule 2).
 */
object DiceSetValidator {
  /** The one file a package must have. */
  const val DICE_SET_FILE: String = "diceset.toml"

  /** Checks [files] and, if it passes, hands back the set it describes. */
  fun validate(
    files: PackageFiles,
    fileName: String = DICE_SET_FILE,
  ): ValidationResult {
    val report = Reporter(fileName)
    val source = source(files, fileName, report) ?: return ValidationResult.Rejected(report.messages)
    val parsed = Toml.parse(source)
    parsed.errors().forEach { failure ->
      report.error(ValidationCode.SyntaxError, failure.message.orEmpty(), failure.position().line())
    }
    if (report.rejected) return ValidationResult.Rejected(report.messages)
    val set = read(parsed, files, report)
    return if (report.rejected || set == null) {
      ValidationResult.Rejected(report.messages)
    } else {
      ValidationResult.Valid(set, report.messages)
    }
  }

  /** The set file's text, refusing one that is missing or absurdly large. */
  private fun source(
    files: PackageFiles,
    fileName: String,
    report: Reporter,
  ): String? {
    val size = files.size(fileName)
    if (size == null) {
      report.error(ValidationCode.MissingFile, "a package needs a '$fileName' at its root")
      return null
    }
    if (size > DiceSetLimits.MAX_TOML_BYTES) {
      report.error(
        ValidationCode.FileTooLarge,
        "'$fileName' is $size bytes; a set file is at most ${DiceSetLimits.MAX_TOML_MIB} MiB",
      )
      return null
    }
    return files.read(fileName)?.decodeToString()
  }

  private fun read(
    parsed: TomlParseResult,
    files: PackageFiles,
    report: Reporter,
  ): DiceSet? {
    val fields = TomlFields(report)
    fields.unknownKeys(parsed, TOP_LEVEL_KEYS, "the set file")
    if (!checkFormat(parsed, fields, report)) return null
    val material = MaterialReader(fields, report)
    val checker = FileChecker(files, fields, report)
    val defaultsTable: TomlTable? = parsed.getTable("defaults")
    val defaults = defaultsTable?.let { material.read(it, "defaults") } ?: DieMaterial()
    defaultsTable?.let { fields.unknownKeys(it, material.keys, "defaults") }
    val dice = dice(parsed, DieReader(fields, material, checker, report), defaults, report)
    val tables = tables(parsed, TableLookReader(fields, material, checker, report), report)
    val identity = identity(parsed, fields, report) ?: return null
    warnAboutMissingStandardDice(dice, report, fields.lineOf(parsed, "set"))
    return identity.copy(dice = dice, tables = tables)
  }

  private fun checkFormat(
    parsed: TomlParseResult,
    fields: TomlFields,
    report: Reporter,
  ): Boolean {
    val format = fields.integer(parsed, "format", "the set file", required = true) ?: return false
    if (format in 1..DiceSetLimits.CURRENT_FORMAT) return true
    report.error(
      ValidationCode.UnsupportedFormat,
      "this package is written for format $format; this app reads format ${DiceSetLimits.CURRENT_FORMAT}",
      fields.lineOf(parsed, "format"),
    )
    return false
  }

  /** The `[set]` table: who made this package and what it is called. */
  private fun identity(
    parsed: TomlParseResult,
    fields: TomlFields,
    report: Reporter,
  ): DiceSet? {
    val table: TomlTable? = parsed.getTable("set")
    if (table == null) {
      report.error(ValidationCode.MissingField, "a package needs a [set] table", fields.lineOf(parsed, "set"))
      return null
    }
    fields.unknownKeys(table, SET_KEYS, "[set]")
    val names = names(table, fields, report) ?: return null
    return DiceSet(
      id = names.id,
      name = names.name,
      version = names.version,
      author = fields.string(table, "author", "[set]"),
      license = fields.string(table, "license", "[set]"),
      description = fields.string(table, "description", "[set]"),
      homepage = homepage(table, fields, report),
    )
  }

  /** The three things a package cannot do without. */
  private data class Names(
    val id: String,
    val name: String,
    val version: String,
  )

  private fun names(
    table: TomlTable,
    fields: TomlFields,
    report: Reporter,
  ): Names? {
    val id = fields.string(table, "id", "[set]", required = true)
    val name = fields.string(table, "name", "[set]", required = true)
    val version = fields.string(table, "version", "[set]", required = true)
    if (id == null || name == null || version == null) return null
    if (Slug.isValid(id, DiceSetLimits.SET_ID_LENGTH)) return Names(id, name, version)
    report.error(
      ValidationCode.BadSlug,
      "'$id' is not ${Slug.describe(DiceSetLimits.SET_ID_LENGTH)}",
      fields.lineOf(table, "id"),
    )
    return null
  }

  /**
   * `homepage` is shown as text and opened only when the user taps it, so the
   * only thing that matters here is that it cannot be plain `http`, or
   * anything else a tap could hand to the system
   * (`docs/dice-sets.md`, "Fields").
   */
  private fun homepage(
    table: TomlTable,
    fields: TomlFields,
    report: Reporter,
  ): String? {
    val url = fields.string(table, "homepage", "[set]") ?: return null
    if (url.startsWith("${DiceSetLimits.HOMEPAGE_SCHEME}://")) return url
    report.error(
      ValidationCode.BadFileReference,
      "[set]'s 'homepage' has to start with ${DiceSetLimits.HOMEPAGE_SCHEME}://",
      fields.lineOf(table, "homepage"),
    )
    return null
  }

  private fun dice(
    parsed: TomlParseResult,
    reader: DieReader,
    defaults: DieMaterial,
    report: Reporter,
  ): List<Die> {
    val entries = parsed.getArray("die") ?: return emptyList()
    val read = (0 until entries.size()).mapNotNull { index -> reader.read(entries.getTable(index), index, defaults) }
    val seen = mutableSetOf<String>()
    read.forEach { die ->
      if (!seen.add(die.id)) report.error(ValidationCode.DuplicateId, "this set has two dice called '${die.id}'")
    }
    return read.distinctBy(Die::id)
  }

  private fun tables(
    parsed: TomlParseResult,
    reader: TableLookReader,
    report: Reporter,
  ): List<TableLook> {
    val entries = parsed.getArray("table") ?: return emptyList()
    val read = (0 until entries.size()).mapNotNull { index -> reader.read(entries.getTable(index), index) }
    val seen = mutableSetOf<String>()
    read.forEach { look ->
      if (!seen.add(look.id)) {
        report.error(ValidationCode.DuplicateId, "this package has two tables called '${look.id}'")
      }
    }
    return read.distinctBy(TableLook::id)
  }

  /**
   * A set need not define every standard die; notation falls back to the
   * bundled set for the ones it lacks. The author is told anyway, because it
   * is far more often an oversight than a decision.
   */
  private fun warnAboutMissingStandardDice(
    dice: List<Die>,
    report: Reporter,
    line: Int?,
  ) {
    if (dice.isEmpty()) return
    val present = dice.map(Die::id).toSet()
    val missing = DiceSet.StandardDieIds.filterNot { it in present }
    if (missing.isEmpty()) return
    report.warn(
      ValidationCode.StandardDieMissing,
      "this set has no ${missing.joinToString(", ")}; notation will use the bundled set for those",
      line,
    )
  }

  private val TOP_LEVEL_KEYS = setOf("format", "set", "defaults", "die", "table")
  private val SET_KEYS = setOf("id", "name", "version", "author", "license", "description", "homepage")
}
