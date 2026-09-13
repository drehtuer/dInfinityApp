package de.drehtuer.dinfinity.core.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One test per error the parser can produce, checking both the code and the
 * characters it points at.
 *
 * The range matters as much as the message: it is what design option 9c draws
 * a squiggle over, and a message under a squiggle in the wrong place is worse
 * than no squiggle at all.
 */
class NotationErrorTest {
  @Test
  fun `an empty formula asks for one`() {
    val error = refused("   ")
    assertEquals(NotationErrorCode.Empty, error.code)
    assertTrue("3d6" in error.message, error.message)
  }

  @Test
  fun `a formula that stops in the middle says so`() {
    val error = refused("3d6 +")
    assertEquals(NotationErrorCode.UnexpectedEnd, error.code)
  }

  @Test
  fun `a character that cannot start a value is pointed at`() {
    val error = refused("3d6 + @")
    assertEquals(NotationErrorCode.UnexpectedCharacter, error.code)
    assertEquals(6..6, error.range)
  }

  @Test
  fun `leftover text at the end is pointed at`() {
    val error = refused("3d6 6")
    assertEquals(NotationErrorCode.UnexpectedCharacter, error.code)
    assertEquals(4..4, error.range)
  }

  @Test
  fun `an unclosed bracket points at the bracket that opened it`() {
    val error = refused("2 * (1d8 + 3")
    assertEquals(NotationErrorCode.UnclosedParenthesis, error.code)
    assertEquals(4..4, error.range)
  }

  @Test
  fun `a closing bracket with nothing to close is pointed at`() {
    val error = refused("1d6 + 2)")
    assertEquals(NotationErrorCode.UnmatchedParenthesis, error.code)
    assertEquals(7..7, error.range)
  }

  @Test
  fun `a closing bracket where a value was expected is pointed at`() {
    assertEquals(NotationErrorCode.UnmatchedParenthesis, refused("1d6 + )").code)
  }

  @Test
  fun `brackets nested past the limit are refused`() {
    val tooDeep = NotationLimits.MAX_PARENTHESIS_DEPTH + 1
    val error = refused("(".repeat(tooDeep) + "1d6" + ")".repeat(tooDeep))
    assertEquals(NotationErrorCode.ParenthesesTooDeep, error.code)
    assertTrue("${NotationLimits.MAX_PARENTHESIS_DEPTH}" in error.message, error.message)
  }

  @Test
  fun `an unclosed label points at the bracket that opened it`() {
    val error = refused("1d6 [Fire")
    assertEquals(NotationErrorCode.UnclosedLabel, error.code)
    assertEquals(4..4, error.range)
  }

  @Test
  fun `a label longer than the limit is refused`() {
    val error = refused("1d6 [" + "a".repeat(NotationLimits.MAX_LABEL_LENGTH + 1) + "]")
    assertEquals(NotationErrorCode.LabelTooLong, error.code)
    assertTrue("${NotationLimits.MAX_LABEL_LENGTH}" in error.message, error.message)
  }

  @Test
  fun `a label of exactly the limit is fine`() {
    assertEquals("a".repeat(NotationLimits.MAX_LABEL_LENGTH), parsed("1d6 [" + "a".repeat(60) + "]").label)
  }

  @Test
  fun `dice written with spaces are refused, with the despaced formula offered`() {
    val error = refused("3 d 6")
    assertEquals(NotationErrorCode.SpacedDice, error.code)
    assertEquals("3d6", error.suggestion)
  }

  @Test
  fun `a space on either side of the d is enough to trigger the suggestion`() {
    assertEquals("3d6 + 2", refused("3 d6 + 2").suggestion)
    assertEquals("3d6 + 2", refused("3d 6 + 2").suggestion)
  }

  @Test
  fun `a d with nothing usable after it is refused`() {
    val error = refused("3d")
    assertEquals(NotationErrorCode.UnexpectedCharacter, error.code)
    assertNull(error.suggestion)
  }

  @Test
  fun `a bare d is not a die`() {
    assertEquals(NotationErrorCode.UnexpectedCharacter, refused("d").code)
  }

  @Test
  fun `a die with no sides is refused`() {
    assertEquals(NotationErrorCode.SidesOutOfRange, refused("1d0").code)
  }

  @Test
  fun `a die with more sides than any set could define is refused`() {
    val error = refused("1d${NotationLimits.MAX_SIDES + 1}")
    assertEquals(NotationErrorCode.SidesOutOfRange, error.code)
  }

  @Test
  fun `a setref with no die after it is refused`() {
    val error = refused("brass:")
    assertEquals(NotationErrorCode.MissingSides, error.code)
    assertTrue("brass:1d20" in error.message, error.message)
  }

  @Test
  fun `a word that is neither a set reference nor a value is refused`() {
    val error = refused("1d6 + fireball")
    assertEquals(NotationErrorCode.UnexpectedCharacter, error.code)
    assertTrue("fireball" in error.message, error.message)
  }

  @Test
  fun `a modifier with no number after it is refused`() {
    val error = refused("2d20kh")
    assertEquals(NotationErrorCode.UnexpectedEnd, error.code)
    assertTrue("kh1" in error.message, error.message)
  }

  @Test
  fun `a reroll with no threshold is refused`() {
    assertEquals(NotationErrorCode.UnexpectedEnd, refused("4d6r").code)
  }

  @Test
  fun `the same modifier twice is refused rather than quietly taking one`() {
    val error = refused("4d6dl1dl1")
    assertEquals(NotationErrorCode.DuplicateModifier, error.code)
    assertEquals(6..8, error.range)
  }

  @Test
  fun `keeping and dropping in one group is refused`() {
    val error = refused("4d6kh1dl1")
    assertEquals(NotationErrorCode.DuplicateModifier, error.code)
    assertTrue("once" in error.message, error.message)
  }

  @Test
  fun `exploding twice is refused`() {
    assertEquals(NotationErrorCode.DuplicateModifier, refused("8d6!!").code)
  }

  @Test
  fun `a number too long to be one is refused`() {
    val error = refused("1d6 + ${"9".repeat(40)}")
    assertEquals(NotationErrorCode.NumberOutOfRange, error.code)
  }

  @Test
  fun `a number above the literal limit is refused`() {
    val error = refused("1d6 + ${NotationLimits.MAX_LITERAL + 1}")
    assertEquals(NotationErrorCode.NumberOutOfRange, error.code)
    assertTrue("${NotationLimits.MAX_LITERAL}" in error.message, error.message)
  }

  @Test
  fun `a modifier's number is bounded too`() {
    assertEquals(NotationErrorCode.NumberOutOfRange, refused("4d6min${"9".repeat(40)}").code)
  }

  @Test
  fun `every error code the parser can raise is reachable from a formula`() {
    val raised =
      listOf("   ", "3d6 +", "3d6 + @", "(1d6", "1d6)", "1d6 [x", "3 d 6", "1d0", "1d6 + ${"9".repeat(40)}")
        .map { refused(it).code }
        .toSet()
    assertTrue(NotationErrorCode.Empty in raised)
    assertTrue(NotationErrorCode.SpacedDice in raised)
  }
}
