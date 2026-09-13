package de.drehtuer.dinfinity.dicesets.format

import de.drehtuer.dinfinity.core.model.DiceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shape of the report itself — what a screen and a log line get to show. */
class ValidationReportTest {
  @Test
  fun `a message prints as file, line, severity and text`() {
    val message =
      ValidationMessage(
        severity = Severity.Error,
        code = ValidationCode.UnknownShape,
        text = "no shape called 'sphere'",
        file = "diceset.toml",
        line = 14,
      )
    assertEquals("diceset.toml:14: error: no shape called 'sphere'", message.toString())
  }

  @Test
  fun `a message about the file as a whole has no line to print`() {
    val message =
      ValidationMessage(Severity.Warning, ValidationCode.UnknownKey, "a key nobody knows", "diceset.toml")
    assertEquals("diceset.toml: warning: a key nobody knows", message.toString())
  }

  @Test
  fun `a rejection separates the errors from the warnings`() {
    val rejected =
      ValidationResult.Rejected(
        listOf(
          ValidationMessage(Severity.Warning, ValidationCode.Clamped, "brought back", "diceset.toml"),
          ValidationMessage(Severity.Error, ValidationCode.BadSlug, "not a slug", "diceset.toml"),
        ),
      )
    assertEquals(listOf(ValidationCode.BadSlug), rejected.errors.map(ValidationMessage::code))
    assertEquals(listOf(ValidationCode.Clamped), rejected.warnings.map(ValidationMessage::code))
    assertEquals(2, rejected.messages.size)
  }

  @Test
  fun `a set that passes with nothing to say has an empty report`() {
    val valid = ValidationResult.Valid(DiceSet(id = "x-set", name = "X", version = "1.0.0"))
    assertTrue(valid.messages.isEmpty())
    assertTrue(valid.warnings.isEmpty())
  }

  @Test
  fun `a set that passes with a warning still says so`() {
    val warning = ValidationMessage(Severity.Warning, ValidationCode.Clamped, "brought back", "diceset.toml", 3)
    val valid = ValidationResult.Valid(DiceSet(id = "x-set", name = "X", version = "1.0.0"), listOf(warning))
    assertEquals(listOf(warning), valid.warnings)
  }

  @Test
  fun `only errors and warnings exist, because only one of them stops an install`() {
    assertEquals(listOf("Error", "Warning"), Severity.entries.map(Severity::name))
  }
}
