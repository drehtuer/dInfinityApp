package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.math.hypot

/**
 * Putting the canvas on the face it was drawn for (`FaceOnSolid`).
 *
 * What has to hold is one thing said several ways: the outline the canvas
 * masks into lands **on** the real polygon, the right way up and the right way
 * round. Everything the Solid tab draws goes through the same basis, so a
 * drawing that lands inside its outline lands inside the face.
 */
class FaceOnSolidTest {
  @Test
  fun `a regular outline lands corner for corner on the face`() {
    // Every regular face of the catalogue is the shape its canvas is masked
    // into, so the fit is exact and each drawn corner is a corner of the face.
    // A kite is the exception and has its own test: the outline the designer
    // draws is a kite somebody chose the proportions of rather than the d10's
    // own.
    REGULAR.forEach { shape ->
      val outline = FaceOutline.of(shape)
      SolidFaces.of(shape).forEach { face ->
        val landed = FaceShapes.corners(outline).map { dot -> FaceOnSolid.basisOf(face, outline).pointOf(dot) }
        landed.forEach { corner ->
          assertTrue(
            "${shape.id} face ${face.index} draws a corner at $corner, which is not one of its own",
            face.corners.any { (it - corner).length < NEARLY },
          )
        }
        assertEquals(
          "${shape.id} face ${face.index} draws two corners on one",
          face.corners.size,
          landed.map { corner -> face.corners.indexOfFirst { (it - corner).length < NEARLY } }.distinct().size,
        )
      }
    }
  }

  @Test
  fun `the middle of the outline is the middle of the face`() {
    // The middle of the *outline*, which is not the middle of the canvas: a
    // kite's corners average out above the square they are drawn in, and it is
    // the outline that has to land on the face.
    DieShape.entries.forEach { shape ->
      val outline = FaceOutline.of(shape)
      val corners = FaceShapes.corners(outline)
      val drawn =
        if (corners.isEmpty()) {
          Dot(HALF, HALF)
        } else {
          Dot(corners.map(Dot::x).average().toFloat(), corners.map(Dot::y).average().toFloat())
        }
      SolidFaces.of(shape).forEach { face ->
        val middle = FaceOnSolid.basisOf(face, outline).pointOf(drawn)
        assertTrue(
          "${shape.id} face ${face.index} draws its middle at $middle",
          (middle - face.centre).length < NEARLY,
        )
      }
    }
  }

  @Test
  fun `the drawing is not turned over`() {
    // Right on the canvas is right on the face as it is looked at from
    // outside, which is what matching the corners the other way round is there
    // to get right. Down, across and the way out have to make a right-handed
    // set, or every number on the die reads in a mirror.
    DieShape.entries.forEach { shape ->
      SolidFaces.of(shape).forEach { face ->
        val basis = FaceOnSolid.basisOf(face, FaceOutline.of(shape))
        assertTrue(
          "${shape.id} face ${face.index} is drawn in a mirror",
          (cross(basis.down, basis.right) dot face.normal) > 0,
        )
      }
    }
  }

  @Test
  fun `a square face is drawn on an edge rather than on a corner`() {
    val top = SolidFaces.of(DieShape.Cube).first()

    val up = FaceOnSolid.basisOf(top, FaceOutline.Square).pointOf(Dot(HALF, 0f)) - top.centre

    val nearest = top.corners.maxOf { corner -> (corner - top.centre).normalised() dot up.normalised() }
    assertTrue("a cube's face is drawn as a diamond: a corner is $nearest of the way up", nearest < OFF_AXIS)
  }

  @Test
  fun `a drawing stands as upright as the face allows`() {
    // A triangle lies on its own face three ways and all three fit equally
    // well, so the tie is settled by which of them puts the canvas's own up
    // nearest the tray's — which is the corner the number points at.
    SolidFaces.of(DieShape.Icosahedron).forEach { face ->
      val up = FaceOnSolid.basisOf(face, FaceOutline.Triangle).pointOf(Dot(HALF, 0f)) - face.centre
      val corners = face.corners.map { it - face.centre }
      assertEquals(
        "face ${face.index} puts the drawing's top somewhere other than its most upright corner",
        corners.maxOf { it.z },
        corners.maxBy { it dot up }.z,
        NEARLY,
      )
    }
  }

  @Test
  fun `a kite is drawn with its short tip on the face's own tip`() {
    // A kite is the one outline here whose corners are not all the same
    // distance from the middle, so there is no tie to settle: the tip can only
    // land on the end of the face's own symmetry axis that is a tip, and the
    // long point on the other end of it.
    val kite = FaceShapes.corners(FaceOutline.Kite)
    SolidFaces.of(DieShape.PentagonalTrapezohedron).forEach { face ->
      val basis = FaceOnSolid.basisOf(face, FaceOutline.Kite)
      val tip = nearestTo(face, basis.pointOf(kite[TIP]))
      val point = nearestTo(face, basis.pointOf(kite[LONG_POINT]))

      assertEquals(
        "face ${face.index} does not put the kite's tip across from its long point",
        ACROSS_A_KITE,
        (point - tip + face.corners.size) % face.corners.size,
      )
      assertEquals(
        "face ${face.index} puts the kite's tip on its long point",
        face.corners.indexOf(face.corners.maxBy { (it - face.centre).length }),
        point,
      )
    }
  }

  @Test
  fun `a canvas with no corners is laid on the face's own frame`() {
    val face: SolidFace = SolidFaces.of(DieShape.Coin).first()

    val basis = FaceOnSolid.basisOf(face, FaceOutline.Circle)

    assertEquals("the disc is not laid flat on the face", 0.0, basis.right dot face.normal, NEARLY)
    assertEquals(
      "the disc does not reach the face's own circle",
      face.radius,
      (basis.pointOf(Dot(1f, HALF)) - face.centre).length,
      NEARLY,
    )
  }

  @Test
  fun `an outline with the wrong number of corners falls back rather than failing`() {
    // Nothing in the catalogue asks for this — every shape's outline has the
    // corners its faces have — but a mismatch should be a drawing put on the
    // wrong die rather than a crash.
    val face = SolidFaces.of(DieShape.Cube).first()

    val basis = FaceOnSolid.basisOf(face, FaceOutline.Pentagon)

    assertTrue(
      "the fallback is not the face's own frame",
      basis.right.normalised().approximates(face.along, NEARLY),
    )
  }

  @Test
  fun `a cell fit puts the outline on the polygon the mesh samples`() {
    // The exporter's half of the same answer, and the one that was missing.
    // A cell is sampled in the face's own frame, so this is the check that a
    // drawing masked into the canvas's outline is painted where the die will
    // look for it (`FaceOnSolid.cellFitOf`).
    REGULAR.forEach { shape ->
      val outline = FaceOutline.of(shape)
      SolidFaces.of(shape).forEach { face ->
        val fit = FaceOnSolid.cellFitOf(face, outline)
        val landed = FaceShapes.corners(outline).map(fit::of)
        val wanted = face.corners.map(face::cellOf)
        landed.forEach { corner ->
          assertTrue(
            "${shape.id} face ${face.index} paints a corner at $corner, which is not one of $wanted",
            wanted.any { away(it, corner) < NEARLY },
          )
        }
      }
    }
  }

  @Test
  fun `and covers every face, kites included`() {
    // A kite is the one outline that is not the face's own shape, so no turn
    // and no size lands it exactly. What it must still do is *cover*: a mask
    // that falls short leaves bare resin round the edge of the face with the
    // printed label showing through it, which is the fault a device session
    // reported as "does not show the face colour".
    DieShape.entries.forEach { shape ->
      val outline = FaceOutline.of(shape)
      val canvas = FaceShapes.corners(outline)
      if (canvas.isEmpty()) return@forEach
      SolidFaces.of(shape).forEach { face ->
        val fit = FaceOnSolid.cellFitOf(face, outline)
        val mask = canvas.map(fit::of)
        face.corners.forEach { corner ->
          val (u, v) = face.cellOf(corner)
          assertTrue(
            "${shape.id} face ${face.index} shows $u,$v and the mask $mask does not cover it",
            Polygon.contains(mask, Dot(u.toFloat(), v.toFloat())) || onEdgeOf(mask, Dot(u.toFloat(), v.toFloat())),
          )
        }
      }
    }
  }

  @Test
  fun `a canvas with no corners is copied into its cell as it is`() {
    // The disc, whose circle is the cell's circle whichever way the face is
    // turned — so there is nothing to turn it by and nothing to grow it by.
    val face = SolidFaces.of(DieShape.Coin).first()

    val fit = FaceOnSolid.cellFitOf(face, FaceOutline.Circle)

    assertEquals(1.0, fit.scale, NEARLY)
    assertEquals(1.0, fit.across, NEARLY)
    assertEquals(0.0, fit.twist, NEARLY)
    assertEquals(Dot(HALF, HALF), fit.origin)
    listOf(Dot(0.25f, 0.75f), Dot(0.9f, 0.1f), Dot(0f, 1f)).forEach { dot ->
      assertEquals(dot.x.toDouble(), fit.of(dot).x.toDouble(), NEARLY)
      assertEquals(dot.y.toDouble(), fit.of(dot).y.toDouble(), NEARLY)
    }
  }

  @Test
  fun `a fit that turns nothing still grows the canvas onto the cell`() {
    // A d6's square is upright already, so the whole of its fit is the size:
    // the canvas draws its outline at 0.48 of the canvas and the cell's own
    // half is 0.5, which is the thin bare rim every shape used to have.
    val face = SolidFaces.of(DieShape.Cube).first()

    val fit = FaceOnSolid.cellFitOf(face, FaceOutline.Square)

    assertEquals(0.0, fit.twist, NEARLY)
    assertEquals(1 / 0.96, fit.scale, NEARLY)
  }

  @Test
  fun `covering does not care which way round the boundary is wound`() {
    // The catalogue's own outlines are all wound one way, so the other way is
    // a claim in a comment unless something asks. An outward normal picked
    // the wrong way round would make every ratio negative and every mask
    // collapse to nothing.
    val square = listOf(1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0, -1.0 to -1.0)
    val corners = listOf(2.0 to 0.0, 0.0 to -2.0)

    assertEquals(2.0, FaceOnSolid.covering(square, corners), NEARLY)
    assertEquals(2.0, FaceOnSolid.covering(square.reversed(), corners), NEARLY)
  }

  @Test
  fun `and an edge through the middle of its own polygon is skipped, not divided by`() {
    // No outline of any catalogue face is degenerate, and a divisor that
    // reaches nought is still worth not dividing by: what comes back is the
    // answer the remaining edges give rather than an infinity.
    val flat = listOf(1.0 to 0.0, -1.0 to 0.0, 0.0 to 1.0)

    val needed = FaceOnSolid.covering(flat, listOf(0.0 to 2.0))

    assertTrue("a degenerate edge was divided by: $needed", needed.isFinite())
  }

  /** How far a place in a cell is from a point on the canvas put into one. */
  private fun away(
    place: Pair<Double, Double>,
    point: Dot,
  ): Double = hypot(place.first - point.x, place.second - point.y)

  /** Whether [point] is on the boundary of [mask], which "inside" does not count. */
  private fun onEdgeOf(
    mask: List<Dot>,
    point: Dot,
  ): Boolean =
    mask.indices.any { at ->
      val from = mask[at]
      val to = mask[(at + 1) % mask.size]
      val cross = (to.x - from.x) * (point.y - from.y) - (to.y - from.y) * (point.x - from.x)
      cross.absoluteValue < ON_THE_LINE
    }

  /** Which corner of [face] is nearest [point]. */
  private fun nearestTo(
    face: SolidFace,
    point: Vector3,
  ): Int = face.corners.indices.minBy { (face.corners[it] - point).length }

  private companion object {
    const val HALF = 0.5f

    /** Close enough to be the same point on a solid one unit across. */
    const val NEARLY = 1e-6

    /**
     * How near the boundary of a mask counts as on it.
     *
     * A corner of the face lands exactly on a corner of the mask wherever
     * the two are the same shape, and "exactly" in doubles is a few parts in
     * a million of a cell after a turn and a divide.
     */
    const val ON_THE_LINE = 1e-6

    /**
     * How nearly a corner may line up with the canvas's own up before the face
     * is being drawn as a diamond.
     *
     * A square's corners sit at 45° from the middle of its edges, so an edge-up
     * square scores 0.707 here and a corner-up one scores 1.
     */
    const val OFF_AXIS = 0.8

    /** The kite's short tip, and the long point across from it (`FaceShapes`). */
    const val TIP = 0
    const val LONG_POINT = 2
    const val ACROSS_A_KITE = 2

    /** The shapes whose faces are the regular polygon their canvas is masked into. */
    val REGULAR =
      listOf(
        DieShape.Tetrahedron,
        DieShape.Cube,
        DieShape.Octahedron,
        DieShape.Dodecahedron,
        DieShape.Icosahedron,
      )
  }
}
