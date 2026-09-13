package de.drehtuer.dinfinity.core.notation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One test per row of the limits table in `docs/dice-notation.md`, at the
 * limit and one past it.
 *
 * These are the numbers the document publishes, so a change here is a change
 * to the document in the same pull request (`.claude/CLAUDE.md`).
 */
class NotationLimitsTest {
  @Test
  fun `the published numbers are these`() {
    assertEquals(1_000, NotationLimits.MAX_DICE_PER_FORMULA)
    assertEquals(20, NotationLimits.MAX_EXPLOSION_DEPTH)
    assertEquals(8, NotationLimits.MAX_PARENTHESIS_DEPTH)
  }

  @Test
  fun `500d6 parses, because the outcome graph has no table to fit on`() {
    assertEquals("500d6", render(parsed("500d6").root))
  }

  @Test
  fun `a group of exactly a thousand dice is fine`() {
    assertEquals("1000d6", render(parsed("1000d6").root))
  }

  @Test
  fun `a group one die over the limit is refused`() {
    val error = refused("1001d6")
    assertEquals(NotationErrorCode.GroupTooLarge, error.code)
    assertTrue("1000" in error.message, error.message)
  }

  @Test
  fun `a group of no dice at all is refused`() {
    assertEquals(NotationErrorCode.GroupTooLarge, refused("0d6").code)
  }

  @Test
  fun `a formula of exactly a thousand dice is fine`() {
    assertEquals(5, parsed("200d6 + 200d6 + 200d6 + 200d6 + 200d6").diceNodes.size)
  }

  @Test
  fun `a formula one die over a thousand is refused`() {
    val error = refused("200d6 + 200d6 + 200d6 + 200d6 + 200d6 + 1d6")
    assertEquals(NotationErrorCode.FormulaTooLarge, error.code)
    assertTrue("1000" in error.message, error.message)
  }

  @Test
  fun `a percentile counts as the two d10s it really is`() {
    assertEquals(500, parsed("500d%").diceNodes.single().count)
    assertEquals(NotationErrorCode.FormulaTooLarge, refused("500d% + 1d6").code)
  }

  @Test
  fun `the explosion depth is what bounds an exploding chain`() {
    assertEquals(20, NotationLimits.MAX_EXPLOSION_DEPTH)
  }
}
