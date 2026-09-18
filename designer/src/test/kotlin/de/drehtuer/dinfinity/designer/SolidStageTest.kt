package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The die in the hand: where its corners land, which faces are facing away,
 * what order the rest are drawn in and what of a drawing reaches them
 * (`SolidStage`).
 *
 * This is the half of the Solid tab worth testing. Whether it looks like a die
 * is a question for a phone; whether a face that is pointing away has been
 * drawn over the one in front of it is a question for here.
 */
class SolidStageTest {
  @Test
  fun `the die stays on the stage however it is turned`() {
    DieShape.entries.forEach { shape ->
      TURNS.forEach { turn ->
        val stage = SolidStage.of(Draft(die = Drawings.die(shape)), turn)
        (stage.silhouette + stage.faces.flatMap(StageFace::outline)).forEach { point ->
          assertTrue(
            "${shape.id} at $turn puts a corner at $point, off the stage",
            point.x in 0f..1f && point.y in 0f..1f,
          )
        }
      }
    }
  }

  @Test
  fun `the die keeps clear of the edge of the stage, whatever it is`() {
    // The room the die is given is worked out from the widest a corner of a
    // unit sphere can ever project to rather than tried until it stopped
    // clipping, so the margin holds for every shape at every angle rather than
    // for the ones somebody looked at.
    DieShape.entries.forEach { shape ->
      TURNS.forEach { turn ->
        SolidStage.of(Draft(die = Drawings.die(shape)), turn).silhouette.forEach { point ->
          assertTrue(
            "${shape.id} at $turn reaches $point, into the stage's margin",
            point.x in MARGIN..(1f - MARGIN) && point.y in MARGIN..(1f - MARGIN),
          )
        }
      }
    }
  }

  @Test
  fun `the silhouette holds every face inside it`() {
    DieShape.entries.forEach { shape ->
      TURNS.forEach { turn ->
        val stage = SolidStage.of(Draft(die = Drawings.die(shape)), turn)
        stage.faces.flatMap(StageFace::outline).forEach { corner ->
          assertTrue(
            "${shape.id} at $turn draws a corner at $corner, outside its own silhouette",
            inside(corner, stage.silhouette),
          )
        }
      }
    }
  }

  @Test
  fun `the faces facing away are not drawn at all`() {
    DieShape.entries.forEach { shape ->
      TURNS.forEach { turn ->
        val faces = SolidStage.of(Draft(die = Drawings.die(shape)), turn).faces
        assertTrue("${shape.id} at $turn shows nothing at all", faces.isNotEmpty())
        assertTrue(
          "${shape.id} at $turn shows ${faces.size} of ${shape.faceCount} faces at once",
          faces.size < shape.faceCount,
        )
        assertEquals("${shape.id} draws a face twice", faces.size, faces.map(StageFace::cell).distinct().size)
      }
    }
  }

  @Test
  fun `the faces are drawn furthest first`() {
    val faces = SolidStage.of(Draft(die = Drawings.die(DieShape.Icosahedron)), SolidTurn()).faces

    assertEquals("the stage is not sorted by depth", faces.sortedByDescending(StageFace::depth), faces)
    assertTrue("every face is the same distance away", faces.map(StageFace::depth).distinct().size > 1)
  }

  @Test
  fun `a face carries the number that was stamped on it, inside its own outline`() {
    val die = Drawings.die(DieShape.Icosahedron)
    val stamped = FaceStamp.fill(Draft(die = die), Drawings.INK)

    SolidStage.of(stamped, SolidTurn()).faces.forEach { face ->
      assertEquals("face ${face.cell} carries no number", 1, face.marks.size)
      face.marks.single().rings.flatten().forEach { point ->
        assertTrue("face ${face.cell} draws its number at $point, outside itself", inside(point, face.outline))
      }
    }
  }

  @Test
  fun `a stroke of the pen is not drawn, and a fill is`() {
    // A closed shape projects to a closed shape with its corners in the right
    // places; a stroke is a line of a width, and a width on a tilted face is
    // wider one way than the other. What the stage cannot draw honestly it
    // leaves out (`docs/face-designer.md`).
    val die = Drawings.die(DieShape.Cube)
    val drawn =
      Draft(die = die).onFace(0) { face ->
        face.draw(Drawings.line()).draw(Fill(dots = FaceShapes.corners(FaceOutline.Square), colorArgb = Drawings.RED))
      }

    val marks =
      SolidStage
        .of(drawn, SolidTurn())
        .faces
        .single { it.cell == 0 }
        .marks

    assertEquals("the stage drew something other than the fill", 1, marks.size)
    assertEquals("the fill lost its colour", Drawings.RED, marks.single().colorArgb)
  }

  @Test
  fun `a face turned towards the light is brighter than one turned away`() {
    val faces = SolidStage.of(Draft(die = Drawings.die(DieShape.Cube)), SolidTurn()).faces

    faces.forEach { face -> assertTrue("face ${face.cell} is lit at ${face.light}", face.light in 0f..1f) }
    assertTrue("every face of a cube catches the same light", faces.map(StageFace::light).distinct().size > 1)
  }

  @Test
  fun `a drag turns the die and stops short of edge-on`() {
    val turn = SolidTurn()

    assertTrue("a drag across does not turn the die", turn.dragged(0.25f, 0f).yaw > turn.yaw)
    assertTrue("a drag down does not lean the die", turn.dragged(0f, 0.25f).pitch > turn.pitch)
    assertTrue("the die can be looked at edge-on", turn.dragged(0f, TOO_FAR).pitch < STRAIGHT_UP)
    assertTrue("the die can be looked at edge-on", turn.dragged(0f, -TOO_FAR).pitch > -STRAIGHT_UP)
  }

  @Test
  fun `the spin comes back round to where it started`() {
    val turn = SolidTurn(pitch = 0f, yaw = 0f)

    val round = turn.spun(SPIN_ROUND)

    assertEquals("a full turn does not come back round", 0f, round.yaw, A_LITTLE.toFloat())
    assertEquals("the spin leans the die", turn.pitch, round.pitch)
    assertTrue("a moment of spin does not move the die", turn.spun(1f).yaw > 0f)
  }

  @Test
  fun `a hull is the outline of what it is given and nothing inside it`() {
    val square =
      listOf(
        StagePoint(0f, 0f),
        StagePoint(1f, 0f),
        StagePoint(1f, 1f),
        StagePoint(0f, 1f),
        StagePoint(0.5f, 0.5f),
        StagePoint(0.5f, 0f),
      )

    val hull = SolidStage.hullOf(square)

    assertEquals("the hull of a square is its four corners", 4, hull.size)
    assertTrue("the hull swallowed a corner", hull.containsAll(square.take(4)))
  }

  @Test
  fun `two points have no hull but themselves`() {
    val line = listOf(StagePoint(0f, 0f), StagePoint(1f, 1f))

    assertEquals("a line was turned into something else", line, SolidStage.hullOf(line))
  }

  /**
   * Whether [point] is inside [outline], which the stage's polygons are convex
   * enough for.
   *
   * A projected face of a convex solid is a convex polygon, so a point is
   * inside it when it is on the same side of every one of its edges.
   */
  private fun inside(
    point: StagePoint,
    outline: List<StagePoint>,
  ): Boolean {
    val sides =
      outline.indices.map { corner ->
        val here = outline[corner]
        val next = outline[(corner + 1) % outline.size]
        (next.x - here.x) * (point.y - here.y) - (next.y - here.y) * (point.x - here.x)
      }
    return sides.all { it >= -ON_THE_LINE } || sides.all { it <= ON_THE_LINE }
  }

  private companion object {
    /** Every way up the stage has to work: flat on, leaning, and part way round. */
    val TURNS =
      listOf(
        SolidTurn(),
        SolidTurn(pitch = 5f, yaw = 0f),
        SolidTurn(pitch = 40f, yaw = 137f),
        SolidTurn(pitch = -85f, yaw = 251f),
        SolidTurn(pitch = 85f, yaw = 33f),
      )

    /** Close enough on a stage that is one unit across. */
    const val A_LITTLE = 1e-3

    /** A drag far enough to lean the die past anything it is allowed to reach. */
    const val TOO_FAR = 4f

    /** Straight up, which the lean has to stop short of. */
    const val STRAIGHT_UP = 90f

    /** How long the die takes to come all the way round on its own. */
    const val SPIN_ROUND = 16f

    /** How much of the stage the die leaves clear at its very widest. */
    const val MARGIN = 0.04f

    /** How far off an edge a point may be and still count as on it. */
    const val ON_THE_LINE = 1e-4f
  }
}
