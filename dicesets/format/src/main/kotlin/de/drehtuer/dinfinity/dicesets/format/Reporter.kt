package de.drehtuer.dinfinity.dicesets.format

/**
 * Collects the report as the validator goes.
 *
 * Everything is collected and nothing thrown, because a set that fails is
 * rejected whole: there is no reason to stop at the first problem and every
 * reason not to. An author fixing a set wants the list, and the screen that
 * shows a failed install is built to hold it (design option 6b).
 */
internal class Reporter(
  private val file: String,
) {
  private val collected = mutableListOf<ValidationMessage>()

  /** Everything said so far, in the order it was found. */
  val messages: List<ValidationMessage> get() = collected.toList()

  /** True when the package will be rejected. */
  val rejected: Boolean get() = collected.any { it.severity == Severity.Error }

  fun error(
    code: ValidationCode,
    text: String,
    line: Int? = null,
  ) {
    collected += ValidationMessage(Severity.Error, code, text, file, line)
  }

  fun warn(
    code: ValidationCode,
    text: String,
    line: Int? = null,
  ) {
    collected += ValidationMessage(Severity.Warning, code, text, file, line)
  }
}
