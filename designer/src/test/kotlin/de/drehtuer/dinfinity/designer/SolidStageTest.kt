package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
  fun `a drag across swings the die and a drag down tips it`() {
    val turn = SolidTurn()

    // A swing about the stage's upright moves nothing up or down: every
    // corner keeps the height it was at and travels round. A tip about the
    // stage's horizontal is the same statement a quarter turn away — nothing
    // moves left or right. Between them that is the whole of what the two
    // directions of a drag mean, and neither is a claim about the die's own
    // axes, which is the point.
    POINTS.forEach { point ->
      assertEquals(
        "a drag across moved a corner up or down",
        turn.turnedTo(point).z,
        turn.dragged(0.25f, 0f).turnedTo(point).z,
        A_LITTLE,
      )
      assertEquals(
        "a drag down moved a corner sideways",
        turn.turnedTo(point).x,
        turn.dragged(0f, 0.25f).turnedTo(point).x,
        A_LITTLE,
      )
    }
    assertTrue("a drag across did not move the die", moved(turn, turn.dragged(0.25f, 0f)))
    assertTrue("a drag down did not move the die", moved(turn, turn.dragged(0f, 0.25f)))
  }

  @Test
  fun `a drag does the same thing wherever the die has been left`() {
    // What the die's own axes could not promise. A sideways drag used to be a
    // yaw applied *before* the lean, so on a die already tipped it rolled the
    // die instead of swinging it. Here the axis is the stage's, so a sideways
    // drag is a swing from every pose there is.
    val poses = listOf(SolidTurn(), SolidTurn().spun(4f), SolidTurn().dragged(0.4f, -0.3f))
    poses.forEach { pose ->
      POINTS.forEach { point ->
        assertEquals(
          "a drag across moved a corner up or down",
          pose.turnedTo(point).z,
          pose.dragged(0.3f, 0f).turnedTo(point).z,
          A_LITTLE,
        )
      }
    }
  }

  @Test
  fun `the spin comes back round to where it started`() {
    val turn = SolidTurn()

    val round = turn.spun(SPIN_ROUND)

    POINTS.forEach { point -> assertSamePoint(turn.turnedTo(point), round.turnedTo(point)) }
    assertTrue("a moment of spin does not move the die", moved(turn, turn.spun(1f)))
  }

  @Test
  fun `the spin is about the reader's upright, whatever the die is doing`() {
    // The fault this is the fix for: the spin used to be a yaw applied before
    // a lean, which is an axis the die carries with it. Tip the die towards
    // you and its spin axis tipped too, so a die looked at edge-on span like a
    // coin on a table rather than turning in the hand.
    val tipped = SolidTurn().dragged(0f, 0.45f)

    POINTS.forEach { point ->
      assertEquals(
        "the spin moved a corner up or down, so it is not turning about the upright",
        tipped.turnedTo(point).z,
        tipped.spun(3f).turnedTo(point).z,
        A_LITTLE,
      )
    }
    assertTrue("the spin did not turn the die", moved(tipped, tipped.spun(3f)))
  }

  @Test
  fun `the spin carries on from where a drag left the die`() {
    val dragged = SolidTurn().dragged(0.3f, 0.2f)
    val spun = dragged.spun(SPIN_ROUND)

    // A whole turn of the spin lands back on the dragged pose rather than on
    // some pose of the spin's own.
    POINTS.forEach { point -> assertSamePoint(dragged.turnedTo(point), spun.turnedTo(point)) }
  }

  @Test
  fun `turning the die keeps its shape`() {
    val turn = SolidTurn().dragged(0.4f, 0.25f).spun(2.5f)
    val a = Vector3(0.3, -0.7, 0.5)
    val b = Vector3(-0.2, 0.4, 0.9)

    // A rotation and nothing else: lengths and angles survive it, which is
    // what says the composition has not drifted into a scale or a shear.
    assertEquals(a.length, turn.turnedTo(a).length, A_LITTLE)
    assertEquals(a dot b, turn.turnedTo(a) dot turn.turnedTo(b), A_LITTLE)
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

  /** The same direction, to within a hair of a unit stage. */
  private fun assertSamePoint(
    expected: Vector3,
    actual: Vector3,
  ) {
    assertEquals("$actual is not $expected", expected.x, actual.x, A_LITTLE)
    assertEquals("$actual is not $expected", expected.y, actual.y, A_LITTLE)
    assertEquals("$actual is not $expected", expected.z, actual.z, A_LITTLE)
  }

  /** True when [after] puts some direction somewhere [before] does not. */
  private fun moved(
    before: SolidTurn,
    after: SolidTurn,
  ): Boolean =
    POINTS.any { point ->
      val was = before.turnedTo(point)
      val now = after.turnedTo(point)
      abs(was.x - now.x) > A_LITTLE || abs(was.y - now.y) > A_LITTLE || abs(was.z - now.z) > A_LITTLE
    }

  private companion object {
    /** Every way up the stage has to work: flat on, leaning, and part way round. */
    val TURNS =
      listOf(
        SolidTurn(),
        SolidTurn().dragged(0f, 0.03f),
        SolidTurn().dragged(0.78f, 0.23f),
        SolidTurn().dragged(0.43f, -0.57f),
        SolidTurn().spun(9f).dragged(0.1f, 0.4f),
      )

    /** A handful of directions to take a turn through. */
    val POINTS =
      listOf(
        Vector3.Up,
        Vector3(1.0, 0.0, 0.0),
        Vector3(0.0, 1.0, 0.0),
        Vector3(0.4, -0.6, 0.7),
      )

    /** Close enough on a stage that is one unit across. */
    const val A_LITTLE = 1e-3

    /** How long the die takes to come all the way round on its own. */
    const val SPIN_ROUND = 16f

    /** How much of the stage the die leaves clear at its very widest. */
    const val MARGIN = 0.04f

    /** How far off an edge a point may be and still count as on it. */
    const val ON_THE_LINE = 1e-4f
  }
}
