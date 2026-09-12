package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceTest {
  @Test
  fun `a face with no label prints its own value`() {
    assertEquals(Face(index = 0, value = 7, label = "7"), Face.labelled(index = 0, value = 7))
  }

  @Test
  fun `a negative value prints its sign, as a fudge die needs`() {
    assertEquals("-1", Face.labelled(index = 0, value = -1).label)
  }

  @Test
  fun `the documented value range is what set files may use`() {
    assertTrue(-9999 in Face.ValueRange)
    assertTrue(9999 in Face.ValueRange)
    assertTrue(10000 !in Face.ValueRange)
  }

  @Test
  fun `a label longer than four characters is not legible on a phone`() {
    assertEquals(4, Face.MAX_LABEL_LENGTH)
    assertTrue("00".length <= Face.MAX_LABEL_LENGTH)
  }

  @Test
  fun `a face carries a symbol just as well as a number`() {
    val skull = Face(index = 0, value = 1, label = "💀")
    assertEquals(1, skull.value)
    assertEquals("💀", skull.label)
  }
}
