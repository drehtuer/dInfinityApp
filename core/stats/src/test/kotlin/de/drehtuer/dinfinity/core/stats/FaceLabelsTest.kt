package de.drehtuer.dinfinity.core.stats

import de.drehtuer.dinfinity.core.model.Face
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a histogram's bars are printed as (decision 73).
 *
 * The case this exists for is the Fudge die, whose faces are worth −1, 0 and
 * +1 and printed `−`, a blank and `+` — and which the histogram used to label
 * with a hyphen and two digits.
 */
class FaceLabelsTest {
  private fun faces(vararg pairs: Pair<Int, String>): List<Face> =
    pairs.mapIndexed { index, (value, label) -> Face(index = index, value = value, label = label) }

  @Test
  fun `a fudge die reads as its own labels`() {
    val fudge = FaceLabels.of(faces(-1 to "−", -1 to "−", 0 to "", 1 to "+", 0 to "", 1 to "+"))

    assertEquals("−", fudge.of(-1))
    assertEquals("", fudge.of(0))
    assertEquals("+", fudge.of(1))
  }

  @Test
  fun `a die whose set is gone reads as its values, with a typographic minus`() {
    assertEquals("−1", FaceLabels.None.of(-1))
    assertEquals("7", FaceLabels.None.of(7))
  }

  @Test
  fun `a value the die has no face for reads as its number`() {
    assertEquals("3", FaceLabels.of(faces(1 to "A")).of(3))
  }

  @Test
  fun `a value printed two ways reads as its number`() {
    // A skull and a crown that both score nothing: either label would be a lie
    // about the other face, and the bar counts the value.
    val labels = FaceLabels.of(faces(0 to "💀", 0 to "♛", 1 to "1"))

    assertEquals("0", labels.of(0))
    assertEquals("1", labels.of(1))
  }

  @Test
  fun `a tens d10 keeps its zero-padded print`() {
    assertEquals("00", FaceLabels.of(faces(0 to "00", 10 to "10")).of(0))
  }

  @Test
  fun `a roll-up keeps the labels the sets agree on and gives up the rest`() {
    val fudge = FaceLabels.of(faces(-1 to "−", 0 to "0", 1 to "+"))
    val d6 = FaceLabels.of(faces(1 to "1", 2 to "2"))
    val pooled = fudge + d6

    assertEquals("−", pooled.of(-1))
    assertEquals("0", pooled.of(0))
    assertEquals("1", pooled.of(1))
    assertEquals("2", pooled.of(2))
    assertEquals(fudge, fudge + FaceLabels.None)
  }

  @Test
  fun `two reads of the same faces are equal, and say what they hold`() {
    val one = FaceLabels.of(faces(1 to "A"))

    assertEquals(one, FaceLabels.of(faces(1 to "A")))
    assertEquals(one.hashCode(), FaceLabels.of(faces(1 to "A")).hashCode())
    assertNotEquals(one, FaceLabels.of(faces(1 to "B")))
    assertNotEquals<Any>(one, "A")
    assertTrue("A" in one.toString())
  }
}
