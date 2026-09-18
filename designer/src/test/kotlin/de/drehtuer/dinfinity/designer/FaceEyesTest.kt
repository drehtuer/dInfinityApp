package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pips, on a d6 and only on a d6 (`docs/face-designer.md`, "Fill all with
 * eyes").
 *
 * All of it is arithmetic over the stored dots, so all of it is tested here
 * rather than through a canvas — which is also why a pipped die comes out
 * pipped in the export and in the strip without either of them being told what
 * a pip is.
 */
class FaceEyesTest {
  private val d6 = Drawings.die(DieShape.Cube)
  private val d20 = Drawings.die(DieShape.Icosahedron)

  private val fudge = cube("df", listOf(-1, -1, 0, 1, 0, 1))

  @Test
  fun `a d6 can be pipped and nothing else can`() {
    assertTrue(FaceEyes.canBePipped(d6))
    assertFalse(FaceEyes.canBePipped(d20))
    assertFalse(FaceEyes.canBePipped(Drawings.die(DieShape.Coin)))
  }

  @Test
  fun `a fudge die is a cube and is not a d6`() {
    // There is no pip pattern for a minus, so the offer is withheld rather
    // than made and refused.
    assertFalse(FaceEyes.canBePipped(fudge))
  }

  @Test
  fun `a cube labelled like a d20's worst pair is still not a d6`() {
    assertFalse(FaceEyes.canBePipped(cube("odd", listOf(1, 2, 3, 4, 6, 9))))
  }

  @Test
  fun `each face carries as many pips as it scores`() {
    (1..FaceEyes.MOST).forEach { value ->
      assertEquals("a $value", value, FaceEyes.spots(value).size)
      assertEquals("a $value", value, pips(value).rings.size)
    }
  }

  @Test
  fun `a value no pattern writes gets no pips at all`() {
    listOf(0, -1, 7, 20).forEach { value ->
      assertTrue("a $value", FaceEyes.spots(value).isEmpty())
      assertNull("a $value", FaceEyes.of(value, Drawings.INK))
    }
  }

  @Test
  fun `every pip sits on the three by three grid the design authored`() {
    val grid = setOf(FaceEyes.NEAR, FaceEyes.MIDDLE, FaceEyes.FAR)
    (1..FaceEyes.MOST).forEach { value ->
      FaceEyes.spots(value).forEach { (across, down) ->
        assertTrue("a $value has a pip at $across across", across in grid)
        assertTrue("a $value has a pip at $down down", down in grid)
      }
    }
    assertEquals(0.3, FaceEyes.NEAR, 1e-9)
    assertEquals(0.5, FaceEyes.MIDDLE, 1e-9)
    assertEquals(0.7, FaceEyes.FAR, 1e-9)
    assertEquals(0.075, FaceEyes.RADIUS, 1e-9)
  }

  @Test
  fun `no two pips of a face land on the same spot`() {
    (1..FaceEyes.MOST).forEach { value ->
      assertEquals("a $value has two pips in one place", value, FaceEyes.spots(value).toSet().size)
    }
  }

  @Test
  fun `the patterns are the ones a moulded die carries`() {
    // One in the middle, two on a diagonal, three on that diagonal, four in
    // the corners, five those and the middle, six in two columns of three.
    assertEquals(listOf(FaceEyes.MIDDLE to FaceEyes.MIDDLE), FaceEyes.spots(1))
    assertTrue(FaceEyes.spots(3).containsAll(FaceEyes.spots(2)))
    assertTrue(FaceEyes.spots(5).containsAll(FaceEyes.spots(4)))
    assertTrue((FaceEyes.MIDDLE to FaceEyes.MIDDLE) in FaceEyes.spots(5))
    assertFalse((FaceEyes.MIDDLE to FaceEyes.MIDDLE) in FaceEyes.spots(FaceEyes.MOST))
    assertEquals(setOf(FaceEyes.NEAR, FaceEyes.FAR), FaceEyes.spots(FaceEyes.MOST).map { it.first }.toSet())
  }

  @Test
  fun `a pip is a ring round its spot, the size the design asked for`() {
    val pip = pips(1).rings.single()
    val across = (pip.minOf { it.x } + pip.maxOf { it.x }) / 2
    val width = pip.maxOf { it.x } - pip.minOf { it.x }

    assertEquals(FaceEyes.MIDDLE, across.toDouble(), 1e-4)
    assertTrue("a pip came out $width across", abs(width - 2 * FaceEyes.RADIUS) < 0.005)
    assertTrue("a pip of ${pip.size} dots encloses nothing", pip.size >= 3)
  }

  @Test
  fun `filling puts one mark on every face, in the ink in the pen`() {
    val filled = FaceEyes.fill(Draft(die = d6), Drawings.RED)

    d6.faces.indices.forEach { cell ->
      val eyes =
        filled
          .face(cell)
          .marks
          .filterIsInstance<Eyes>()
          .single()
      assertEquals("cell $cell", d6.faces[cell].value, eyes.rings.size)
      assertEquals(Drawings.RED, eyes.colorArgb)
    }
  }

  @Test
  fun `filling is one undoable step per face, not one for the die`() {
    val filled = FaceEyes.fill(Draft(die = d6), Drawings.INK)

    d6.faces.indices.forEach { cell ->
      assertEquals("cell $cell", 1, filled.face(cell).past.size)
      assertTrue("cell $cell did not come off in one press", filled.face(cell).undo().blank)
    }
  }

  @Test
  fun `pressing it twice changes nothing`() {
    val once = FaceEyes.fill(Draft(die = d6), Drawings.INK)

    assertEquals(once, FaceEyes.fill(once, Drawings.INK))
  }

  @Test
  fun `the pips land over a drawing rather than taking it away`() {
    val filled = FaceEyes.fill(Drawings.drawn(d6, 0), Drawings.INK)

    assertTrue(filled.face(0).marks.any { it is Stroke })
    assertTrue(filled.face(0).marks.any { it is Eyes })
  }

  @Test
  fun `filling with eyes takes the numerals off, because a face is one or the other`() {
    val pipped = FaceEyes.fill(FaceStamp.fill(Draft(die = d6), Drawings.INK), Drawings.INK)

    d6.faces.indices.forEach { cell ->
      assertFalse("cell $cell", pipped.face(cell).marks.any { it is Stamp })
      assertEquals("cell $cell", 1, pipped.face(cell).marks.count { it is Eyes })
    }
  }

  @Test
  fun `filling with numbers takes the pips off, for the same reason`() {
    val numbered = FaceStamp.fill(FaceEyes.fill(Draft(die = d6), Drawings.INK), Drawings.INK)

    d6.faces.indices.forEach { cell ->
      assertFalse("cell $cell", numbered.face(cell).marks.any { it is Eyes })
      assertEquals("cell $cell", 1, numbered.face(cell).marks.count { it is Stamp })
    }
  }

  @Test
  fun `the swap is one step, so one press of undo puts the numerals back`() {
    val numbered = FaceStamp.fill(Draft(die = d6), Drawings.INK)
    val pipped = FaceEyes.fill(numbered, Drawings.INK)

    assertEquals(numbered.face(0).marks, pipped.face(0).undo().marks)
  }

  @Test
  fun `clearing takes the pips off and keeps the drawing round them`() {
    val cleared = FaceEyes.clear(FaceEyes.fill(Drawings.drawn(d6, 0), Drawings.INK))

    assertFalse(FaceEyes.pipped(cleared))
    assertTrue(cleared.face(0).marks.any { it is Stroke })
  }

  @Test
  fun `clearing an unpipped die is not a row of steps that changed nothing`() {
    val drawn = Drawings.drawn(d6, 0, 1)

    assertEquals(drawn, FaceEyes.clear(drawn))
  }

  @Test
  fun `a die that cannot be pipped is left alone`() {
    val drawn = Drawings.drawn(d20, 0)

    assertEquals(drawn, FaceEyes.fill(drawn, Drawings.INK))
    assertFalse(FaceEyes.pipped(drawn))
  }

  @Test
  fun `pipped says whether there is anything to clear`() {
    assertFalse(FaceEyes.pipped(Draft(die = d6)))
    assertTrue(FaceEyes.pipped(FaceEyes.fill(Draft(die = d6), Drawings.INK)))
  }

  @Test
  fun `a face of pips is turned and mirrored whole, like any other mark`() {
    val eyes = pips(2)
    val mirrored = eyes.at(eyes.dots.map { Dot(x = 1f - it.x, y = it.y) })

    // Ring by ring rather than dot by dot: a mirrored face of pips is still a
    // face of pips, with every pip whole.
    assertEquals(eyes.rings.map { it.size }, mirrored.rings.map { it.size })
    assertEquals(1f - eyes.rings[0][0].x, mirrored.rings[0][0].x, 1e-6f)
  }

  @Test
  fun `dots of the wrong number are not this mark moved, so they are refused`() {
    val eyes = pips(2)

    assertEquals(eyes, eyes.at(eyes.dots.drop(1)))
  }

  @Test
  fun `pips cut back out of a flat list are the pips that went in`() {
    val eyes = pips(5)

    assertEquals(eyes, Eyes.of(eyes.rings.map { it.size }, eyes.dots, Drawings.INK))
    assertNull(Eyes.of(listOf(2, 2), eyes.dots, Drawings.INK))
    assertNull(Eyes.of(emptyList(), eyes.dots, Drawings.INK))
    assertNull(Eyes.of(listOf(4), eyes.dots, Drawings.INK))
  }

  @Test
  fun `pips count as one mark against the face's limit`() {
    assertEquals(
      1,
      FaceEyes
        .fill(Draft(die = d6), Drawings.INK)
        .face(0)
        .marks.size,
    )
  }

  @Test
  fun `a face with no room left refuses the pips whole rather than in part`() {
    val full = (0 until FaceDrawing.MAX_MARKS).fold(FaceDrawing()) { drawing, _ -> drawing.draw(Drawings.line()) }
    val filled = FaceEyes.fill(Draft(die = d6, faces = mapOf(0 to full)), Drawings.INK)

    assertEquals(FaceDrawing.MAX_MARKS, filled.face(0).marks.size)
    assertFalse(filled.face(0).marks.any { it is Eyes })
  }

  private fun pips(value: Int): Eyes = requireNotNull(FaceEyes.of(value, Drawings.INK))

  private fun cube(
    id: String,
    values: List<Int>,
  ): Die =
    Die(
      id = id,
      shape = DieShape.Cube,
      faces = values.mapIndexed { index, value -> Face.labelled(index = index, value = value) },
    )
}
