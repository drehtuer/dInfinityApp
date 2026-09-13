package de.drehtuer.dinfinity.dicesets.format

import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * Typed reads out of a parsed TOML table, each reporting where it went wrong.
 *
 * This is the "fixed schema" half of `docs/dice-sets.md`'s parser rule: the
 * library parses TOML, and every field the app wants is asked for by name and
 * checked by hand into a plain data class. No reflection, no deserializer, no
 * default-on-error — a downloaded file never reaches machinery that decides
 * for itself what type something ought to be.
 */
internal class TomlFields(
  private val report: Reporter,
) {
  /** The line [key] was written on, for a message to point at. */
  fun lineOf(
    table: TomlTable,
    key: String,
  ): Int? = table.inputPositionOf(key)?.line()

  /** A string, complaining when it is missing (if [required]) or is not one. */
  fun string(
    table: TomlTable,
    key: String,
    where: String,
    required: Boolean = false,
  ): String? = require(table, key, where, required).let { read(table, key, where, "text") { raw -> raw as? String } }

  /** A whole number, as TOML always reads one: a `Long`. */
  fun integer(
    table: TomlTable,
    key: String,
    where: String,
    required: Boolean = false,
  ): Long? =
    require(table, key, where, required).let {
      read(table, key, where, "a whole number") { raw -> raw as? Long }
    }

  /**
   * A number, whether the file wrote it as `0` or as `0.0`.
   *
   * TOML tells integers and floats apart and the app does not care: a
   * `metallic = 0` is as good as `metallic = 0.0`, and refusing one would be a
   * puzzle rather than a rule.
   */
  fun decimal(
    table: TomlTable,
    key: String,
    where: String,
  ): Double? =
    read(table, key, where, expected = "a number") { raw ->
      when (raw) {
        is Double -> raw
        is Long -> raw.toDouble()
        else -> null
      }
    }

  /** An array of whole numbers. */
  fun integers(
    table: TomlTable,
    key: String,
    where: String,
    required: Boolean = false,
  ): List<Long>? =
    require(table, key, where, required).let {
      array(table, key, where, "whole numbers") { raw -> raw as? Long }
    }

  /** An array of strings. */
  fun strings(
    table: TomlTable,
    key: String,
    where: String,
  ): List<String>? = array(table, key, where, expected = "text") { raw -> raw as? String }

  /**
   * Warns about keys the app does not know, which are then ignored.
   *
   * A warning rather than an error so the format can grow: a set written for a
   * later version still installs on this one, minus whatever it was that the
   * later version added (`docs/dice-sets.md`).
   */
  fun unknownKeys(
    table: TomlTable,
    known: Set<String>,
    where: String,
  ) {
    table.keySet().filterNot { it in known }.forEach { key ->
      report.warn(ValidationCode.UnknownKey, "$where has a key '$key' this version does not know", lineOf(table, key))
    }
  }

  /** Says a required key is missing, so the typed reads below need not each know. */
  private fun require(
    table: TomlTable,
    key: String,
    where: String,
    required: Boolean,
  ) {
    if (required && table.get(key) == null) {
      report.error(ValidationCode.MissingField, "$where needs a '$key'", lineOf(table, key))
    }
  }

  private fun <T> read(
    table: TomlTable,
    key: String,
    where: String,
    expected: String,
    convert: (Any?) -> T?,
  ): T? {
    val raw = table.get(key) ?: return null
    val value = convert(raw)
    if (value == null) report.error(ValidationCode.WrongType, "$where's '$key' has to be $expected", lineOf(table, key))
    return value
  }

  private fun <T> array(
    table: TomlTable,
    key: String,
    where: String,
    expected: String,
    convert: (Any?) -> T?,
  ): List<T>? {
    val raw = table.get(key) ?: return null
    val values = (raw as? TomlArray)?.let { array -> (0 until array.size()).map { convert(array.get(it)) } }
    if (values == null || values.any { it == null }) {
      report.error(
        ValidationCode.WrongType,
        "$where's '$key' has to be a list of $expected",
        lineOf(table, key),
      )
      return null
    }
    return values.filterNotNull()
  }
}
