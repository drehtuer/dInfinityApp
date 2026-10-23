package de.drehtuer.dinfinity.build

import java.io.File
import java.security.MessageDigest

/**
 * The physics a fairness run measured, as one number (`docs/build-setup.md`,
 * "Fairness before a release").
 *
 * A SHA-256 over every file that decides how a die lands — its relative path
 * and its contents, in path order, with line endings made one kind — so the
 * same checkout gives the same number on any machine, and any change to one of
 * them, a comment included, gives another. That strictness is the point: the
 * record is only worth anything if nothing that rolls the dice moved after it
 * was made, and telling a harmless edit from a harmful one is exactly the
 * judgement a hundred thousand throws are there to replace.
 */
object PhysicsFingerprint {
  /** The fingerprint of [files], each named by its path relative to [root]. */
  fun of(
    root: File,
    files: Collection<File>,
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")
    files
      .map { it.relativeTo(root).invariantSeparatorsPath to it }
      .sortedBy { it.first }
      .forEach { (path, file) ->
        digest.update(path.toByteArray())
        digest.update(SEPARATOR)
        digest.update(file.readText().replace("\r\n", "\n").toByteArray())
        digest.update(SEPARATOR)
      }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private val SEPARATOR = byteArrayOf(0)
}

/**
 * What one fairness run on the reference device came to, as `tools/fairness.sh`
 * writes it to `fairness/record.txt`, and what the release asks of it.
 *
 * @param fields the `key: value` lines, keys lower-case.
 * @param shapes one line per shape, as `FairnessTest` reported it.
 */
data class FairnessRecord(
  val fields: Map<String, String>,
  val shapes: List<String>,
) {
  /**
   * Why this record does not clear a release of physics whose fingerprint is
   * [fingerprint], or nothing when it does: a passing run of at least
   * [leastRolls] throws a shape, at the dice's own rounding, of exactly this
   * physics.
   */
  fun problemsFor(
    fingerprint: String,
    leastRolls: Int = LEAST_ROLLS,
  ): List<String> =
    buildList {
      val recorded = fields[FINGERPRINT]
      if (recorded != fingerprint) {
        add("the physics changed since the fairness run (recorded ${recorded ?: "none"}, now $fingerprint)")
      }
      if (fields[VERDICT] != PASS) add("the fairness run did not pass (verdict: ${fields[VERDICT] ?: "none"})")
      val rolls = fields[ROLLS]?.toIntOrNull()
      if (rolls == null || rolls < leastRolls) {
        add("the fairness run threw ${rolls ?: "no"} times a shape, not $leastRolls")
      }
      if (fields[ROUNDING] != DEFAULT_ROUNDING) {
        add("the fairness run rounded the dice by ${fields[ROUNDING] ?: "nothing it said"}, not their own")
      }
      if (shapes.isEmpty()) add("the fairness record lists no shapes")
    }

  /** The record as the file holds it. */
  fun format(): String =
    buildString {
      appendLine(HEADER)
      fields.forEach { (key, value) -> appendLine("$key: $value") }
      shapes.forEach { appendLine("$SHAPE: $it") }
    }

  companion object {
    /** What a release asks for: the hundred thousand a shape `docs/TODO.md` Step 5.2 fixes. */
    const val LEAST_ROLLS: Int = 100_000

    const val FINGERPRINT = "fingerprint"
    const val VERDICT = "verdict"
    const val ROLLS = "rolls"
    const val ROUNDING = "edge rounding"
    const val SHAPE = "shape"
    const val PASS = "pass"
    const val DEFAULT_ROUNDING = "default"

    private const val HEADER =
      "# Written by tools/fairness.sh on the reference device. Not edited by hand: the release checks it."

    /**
     * Reads [text]. A line that is not `key: value` is skipped, and so is a
     * comment, so a damaged record says what is missing rather than failing to
     * be read at all.
     */
    fun parse(text: String): FairnessRecord {
      val fields = linkedMapOf<String, String>()
      val shapes = mutableListOf<String>()
      text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
        val at = line.indexOf(": ")
        if (at <= 0) return@forEach
        val key = line.substring(0, at).trim().lowercase()
        val value = line.substring(at + 2).trim()
        if (key == SHAPE) shapes += value else fields[key] = value
      }
      return FairnessRecord(fields, shapes)
    }
  }
}
