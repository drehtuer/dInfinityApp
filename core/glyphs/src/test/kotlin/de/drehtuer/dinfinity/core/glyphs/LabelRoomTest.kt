package de.drehtuer.dinfinity.core.glyphs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How much room a face has for a number, and where in it the number goes.
 *
 * The polygons here are the outlines themselves rather than a mesh's, because
 * that is what both callers hand it: the tray measures a face off its mesh and
 * the designer takes it off the outline it masks a canvas into. What matters
 * is that one polygon gets one answer.
 */
class LabelRoomTest {
  private val square = polygon(sides = 4)
  private val triangle = polygon(sides = 3)

  /** A regular polygon filling the cell, wound the way a cell's corners are. */
  private fun polygon(
    sides: Int,
    radius: Double = 0.5,
  ): List<Pair<Double, Double>> =
    (0 until sides).map { corner ->
      val angle = -PI / 2 + (if (sides % 2 == 0) PI / sides else 0.0) + 2 * PI * corner / sides
      0.5 + radius * cos(angle) to 0.5 + radius * sin(angle)
    }

  @Test
  fun `puts a number in the middle of a square`() {
    val placement = assertNotNull(LabelRoom.centred(square, "6"))

    assertEquals(0.5, placement.centreX, 1e-6)
    assertEquals(0.5, placement.centreY, 1e-6)
  }

  @Test
  fun `gives a triangle less room than a square, because it has less`() {
    // A cell is the circle drawn round a face, and how much of one a face
    // fills is what polygon it is: a number sized against the cell comes out
    // right on a d6 and crowding the edges on a d20.
    val onSquare = assertNotNull(LabelRoom.centred(square, "6")).height
    val onTriangle = assertNotNull(LabelRoom.centred(triangle, "6")).height

    assertTrue(onTriangle < onSquare, "the triangle was given $onTriangle against the square's $onSquare")
  }

  @Test
  fun `keeps the number inside the face it is on`() {
    listOf(square, triangle, polygon(sides = 5)).forEach { face ->
      val placement = assertNotNull(LabelRoom.centred(face, "20"))
      val room = LabelRoom.heightAt(face, Typesetter.inkWidth("20", 1.0), placement.centreX, placement.centreY)
      assertTrue(placement.height <= room + 1e-9, "a label of ${placement.height} in room of $room")
    }
  }

  @Test
  fun `moves a number off the middle when there is more room elsewhere`() {
    // A d18's kites are long enough that the middle of the cell is not inside
    // the face at all, so a box pinned there does not fit anywhere.
    val kite = listOf(0.5 to 0.04, 0.84 to 0.38, 0.5 to 0.96, 0.16 to 0.38)
    val placement = assertNotNull(LabelRoom.centred(kite, "18"))

    assertTrue(placement.height > 0.0, "a kite has room for something")
    assertTrue(abs(placement.centreY - 0.5) > 1e-6, "the kite's number stayed in the middle of the cell")
  }

  @Test
  fun `takes the share twice, which is what the tray prints`() {
    // Once off the box the centre is solved clear of and once off what is
    // printed in it. Kept rather than corrected here because the size on the
    // phone was judged against it (`docs/TODO.md`, "Open questions").
    val room = LabelRoom.on(square, aspect = Typesetter.inkWidth("6", 1.0), share = LabelRoom.FACE_SHARE).height
    val printed = assertNotNull(LabelRoom.centred(square, "6")).height

    assertEquals(LabelRoom.FACE_SHARE * room, printed, 1e-9)
  }

  @Test
  fun `has nothing to place for an empty label`() {
    assertNull(LabelRoom.centred(square, ""))
    assertNull(LabelRoom.cornered(triangle, 0.5 to 0.04, ""))
  }

  @Test
  fun `has nowhere to place a label on a face with no room`() {
    // No catalogue solid is one, but a polygon that encloses nothing is not a
    // thing to divide by.
    val slit = listOf(0.5 to 0.5, 0.5 to 0.5, 0.5 to 0.5)

    assertNull(LabelRoom.centred(slit, "6"))
  }

  @Test
  fun `turns a corner number to face its own corner`() {
    val top = assertNotNull(LabelRoom.cornered(triangle, triangle[0], "4"))
    val left = assertNotNull(LabelRoom.cornered(triangle, triangle[1], "4"))

    assertEquals(0.0, top.turns, 1e-9, "the number at the top of the cell is upright")
    assertTrue(abs(left.turns) > 1e-3, "the number at a side corner was left upright")
  }

  @Test
  fun `keeps a corner number inside the triangle it is on`() {
    triangle.forEach { corner ->
      val placement = assertNotNull(LabelRoom.cornered(triangle, corner, "4"))
      val room = LabelRoom.heightAt(triangle, Typesetter.inkWidth("4", 1.0), placement.centreX, placement.centreY)
      assertTrue(placement.height <= room + 1e-9, "a corner label of ${placement.height} in room of $room")
      assertTrue(placement.height <= LabelRoom.CORNER_HEIGHT, "three to a triangle and one of them ${placement.height}")
    }
  }

  @Test
  fun `pulls a corner in towards the middle, and leaves the middle alone`() {
    assertEquals(0.5 to 0.5, LabelRoom.inside(0.5 to 0.5))

    val (x, y) = LabelRoom.inside(0.0 to 1.0)
    assertEquals(0.5 - 0.5 * LabelRoom.CORNER_REACH, x, 1e-9)
    assertEquals(0.5 + 0.5 * LabelRoom.CORNER_REACH, y, 1e-9)
  }

  @Test
  fun `says how tall a label may be at a point, which is nothing outside the face`() {
    assertTrue(LabelRoom.heightAt(triangle, aspect = 0.5, centreX = 0.5, centreY = 0.5) > 0.0)
    assertTrue(LabelRoom.heightAt(triangle, aspect = 0.5, centreX = 0.02, centreY = 0.98) < 0.0)
  }

  @Test
  fun `is unmoved by which way round the corners are given`() {
    // The normals are turned outwards by the ring's own winding, so a face
    // wound the other way is the same face.
    val forwards = assertNotNull(LabelRoom.centred(triangle, "6"))
    val backwards = assertNotNull(LabelRoom.centred(triangle.reversed(), "6"))

    assertEquals(forwards.height, backwards.height, 1e-9)
    assertEquals(forwards.centreX, backwards.centreX, 1e-9)
    assertEquals(forwards.centreY, backwards.centreY, 1e-9)
  }

  @Test
  fun `marks the label when it is asked to, and not otherwise`() {
    assertTrue(assertNotNull(LabelRoom.centred(square, "6", marked = true)).marked)
    assertTrue(!assertNotNull(LabelRoom.centred(square, "6")).marked)
    assertTrue(assertNotNull(LabelRoom.cornered(triangle, triangle[0], "6", marked = true)).marked)
  }

  @Test
  fun `a marked numeral is given room for its dot rather than sized as a bare one`() {
    // `6.` is wider than `6`, so it has to be measured with the dot on — a
    // numeral sized as though it were bare would hang its dot over the edge
    // (`docs/dice-sets.md`, "Labels").
    val bare = assertNotNull(LabelRoom.centred(triangle, "6")).height
    val marked = assertNotNull(LabelRoom.centred(triangle, "6", marked = true)).height
    assertTrue(marked < bare, "a marked 6 was given $marked, the same room as a bare one's $bare")
    assertEquals(assertNotNull(LabelRoom.centred(triangle, "6.")).height, marked, 1e-9)
  }

  @Test
  fun `a marked number at a corner is measured with its dot too`() {
    val bare = assertNotNull(LabelRoom.cornered(triangle, triangle[0], "6"))
    val marked = assertNotNull(LabelRoom.cornered(triangle, triangle[0], "6", marked = true))
    assertTrue(marked.height <= bare.height, "${marked.height} against ${bare.height}")
    assertEquals(bare.centreX, marked.centreX, 1e-9)
  }

  @Test
  fun `gives a narrow label no more room than a wide one`() {
    // A `1` sits well inside its advance, and a face that sized itself to the
    // ink would print a `1` twice the height of the `8` beside it.
    val one = assertNotNull(LabelRoom.centred(square, "1")).height
    val eight = assertNotNull(LabelRoom.centred(square, "8")).height

    assertEquals(eight, one, 1e-9)
  }
}
