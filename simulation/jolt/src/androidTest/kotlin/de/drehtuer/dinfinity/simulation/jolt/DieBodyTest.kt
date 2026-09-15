package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * That the die the solver collides is the die the catalogue describes
 * (`docs/physics-and-rendering.md`, "Are the dice fair"; `docs/TODO.md`,
 * Step 5.2).
 *
 * **This is the other half of the fairness claim.** `FairnessTest` throws a
 * hundred thousand dice and counts; this asks the one question that would
 * explain a count coming out wrong. A catalogue solid is *isohedral* — its
 * symmetry group carries any face onto any other — and dice start in an
 * orientation drawn evenly over every orientation there is. Those two facts
 * together make a fair die whatever the physics does in between, because
 * turning the start by one of the solid's own symmetries gives a physically
 * identical throw with the face labels permuted.
 *
 * So a shape that comes out unfair is a shape the engine is not collided as
 * the solid it was meant to be, and there is exactly one way to find out which
 * one it got: ask it. That is [JoltWorld.bodyOf], and this is what it is for.
 *
 * Three things are checked, in the order they would go wrong:
 *
 * 1. **The hull has the faces the solid has.** A hull builder merges faces it
 *    thinks are coplanar and drops points it thinks are inside; either would
 *    leave a die that is not the die, and a count is the cheapest way to see it.
 * 2. **The mass is in the middle.** A centre of mass away from the origin is
 *    the definition of a loaded die.
 * 3. **The inertia has the solid's own symmetry.** A cube, an octahedron, a
 *    dodecahedron, an icosahedron and a tetrahedron all spin the same about
 *    every axis; a trapezohedron and a coin have one axis of their own and two
 *    that match each other. An inertia tensor that does not say that is a body
 *    that is not the solid, however right its corners look.
 */
class DieBodyTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun everyShapeIsCollidedAsTheSolidItIs() {
    val wrong = mutableListOf<String>()
    val report = StringBuilder()

    DieShape.entries.forEach { shape ->
      val body = bodyOf(shape)
      val radius = Die.standard(shape.id, shape).material.boundingRadiusMm
      val moments = principalMoments(body.inertia)

      report.appendLine(
        "${shape.id}: faces=${body.faceCount} (want ${facesExpected(shape)}) " +
          "com=%.5f mm moments=%s".format(offCentre(body.centreOfMassMm), moments.map { "%.4f".format(it) }),
      )

      if (body.faceCount != facesExpected(shape)) {
        wrong += "${shape.id} is a hull of ${body.faceCount} faces, not ${facesExpected(shape)}"
      }
      // A thousandth of the die's own radius: far tighter than anything that
      // could bias a roll, and far looser than float noise.
      val offCentre = offCentre(body.centreOfMassMm) / radius
      if (offCentre > OFF_CENTRE) {
        wrong += "${shape.id} is loaded: its mass is %.4f of its radius off centre".format(offCentre)
      }
      symmetryFault(shape, moments)?.let { fault -> wrong += "${shape.id} $fault" }
    }

    println(report)
    assertTrue("$report\nwrong: $wrong", wrong.isEmpty())
  }

  @Test
  fun everyFaceOfAShapeIsTheSameDistanceOut() {
    // An isohedral solid has one inradius, and every face sits on it. A face
    // further out than its neighbours is a face the die lands on more often,
    // which is the shape a loaded die takes when nothing about its *mass* is
    // wrong (`docs/physics-and-rendering.md`).
    val report = StringBuilder()
    val wrong = mutableListOf<String>()

    DieShape.entries.filterNot { it == DieShape.Coin }.forEach { shape ->
      val distances = bodyOf(shape).faces.map(FacePlane::distanceMm)
      val smallest = distances.min()
      val largest = distances.max()
      val spread = (largest - smallest) / largest

      report.appendLine("${shape.id}: inradius %.5f..%.5f mm, spread %.6f".format(smallest, largest, spread))
      if (spread > FACE_SPREAD) {
        wrong += "${shape.id} has faces %.6f apart in how far out they sit".format(spread)
      }
    }

    println(report)
    assertTrue("$report\nwrong: $wrong", wrong.isEmpty())
  }

  /** The body Jolt built for one die of [shape], thrown nowhere. */
  private fun bodyOf(shape: DieShape): DieBody {
    val die = Die.standard(shape.id, shape)
    val world = requireNotNull(JoltWorld.open(geometry, table, maxDice = 1))
    return world.use {
      world.addDie(
        hull = ShapeGeometry.hullOf(die),
        material = die.material,
        placement =
          Placement(
            position = Vector3(0.0, 0.0, geometry.wallHeightMm),
            rotation = Quaternion(w = 1.0, x = 0.0, y = 0.0, z = 0.0),
            linearVelocity = Vector3.Zero,
            angularVelocity = Vector3.Zero,
          ),
      )
      world.finish()
      world.bodyOf(0)
    }
  }

  /**
   * How many faces the hull of [shape] has.
   *
   * The same as the number of readable positions, except for the two shapes
   * that are not read from their faces: a tetrahedron is read from its corners
   * and has four faces anyway, and a coin is a cylinder whose rim is drawn as
   * a polygon (`ShapeGeometry`).
   */
  private fun facesExpected(shape: DieShape): Int =
    when (shape) {
      DieShape.Coin -> COIN_FACES
      else -> shape.faceCount
    }

  private fun offCentre(centre: Vector3): Double = sqrt(centre.x * centre.x + centre.y * centre.y + centre.z * centre.z)

  /**
   * What is wrong with [moments] for [shape], or null when nothing is.
   *
   * A solid with more than one three-fold axis — every Platonic one — has an
   * inertia tensor that is a multiple of the identity: it spins the same way
   * about every axis there is. A trapezohedron and a coin have a single axis
   * of their own, so two of their moments match and the third does not.
   */
  private fun symmetryFault(
    shape: DieShape,
    moments: List<Double>,
  ): String? {
    val (low, middle, high) = moments
    return when (shape) {
      DieShape.Tetrahedron, DieShape.Cube, DieShape.Octahedron, DieShape.Dodecahedron, DieShape.Icosahedron ->
        "spins differently about different axes: %s"
          .format(moments)
          .takeIf { (high - low) / high > MOMENT_TOLERANCE }

      // The two that match are the ones across the solid's own axis; which of
      // the three is the odd one out depends on which way up the catalogue
      // turned it, so it is the *closest pair* that has to agree.
      else ->
        "has no axis of its own: %s"
          .format(moments)
          .takeIf { minOf(middle - low, high - middle) / high > MOMENT_TOLERANCE }
    }
  }

  /**
   * The three principal moments of a 3x3 inertia tensor, smallest first.
   *
   * By Jacobi rotations, which is nine lines and exact enough for a symmetric
   * 3x3 — the alternative is a cubic root formula that loses precision on
   * exactly the near-equal roots this test is about.
   */
  private fun principalMoments(inertia: List<Double>): List<Double> {
    val matrix = Array(SIZE) { row -> DoubleArray(SIZE) { column -> inertia[row * SIZE + column] } }
    repeat(SWEEPS) {
      for (p in 0 until SIZE - 1) {
        for (q in p + 1 until SIZE) {
          if (abs(matrix[p][q]) < TINY) continue
          val theta = (matrix[q][q] - matrix[p][p]) / (2 * matrix[p][q])
          val sign = if (theta >= 0) 1.0 else -1.0
          val t = sign / (abs(theta) + sqrt(theta * theta + 1))
          val c = 1 / sqrt(t * t + 1)
          val s = t * c
          rotate(matrix, p, q, c, s)
        }
      }
    }
    return (0 until SIZE).map { matrix[it][it] }.sorted()
  }

  private fun rotate(
    matrix: Array<DoubleArray>,
    p: Int,
    q: Int,
    c: Double,
    s: Double,
  ) {
    val rows = (0 until SIZE).map { row -> doubleArrayOf(matrix[row][p], matrix[row][q]) }
    rows.forEachIndexed { row, pair ->
      matrix[row][p] = c * pair[0] - s * pair[1]
      matrix[row][q] = s * pair[0] + c * pair[1]
    }
    val columns = (0 until SIZE).map { column -> doubleArrayOf(matrix[p][column], matrix[q][column]) }
    columns.forEachIndexed { column, pair ->
      matrix[p][column] = c * pair[0] - s * pair[1]
      matrix[q][column] = s * pair[0] + c * pair[1]
    }
  }

  @Test
  fun theBodyReaderReportsSomethingAtAll() {
    // The guard on the guard. Every assertion above is about numbers coming
    // back from a native call, and a call that quietly wrote nothing would
    // make all of them pass on zeros.
    val body = bodyOf(DieShape.Cube)

    assertTrue("the hull came back with no faces", body.faceCount > 0)
    assertEquals("a cube's faces", CUBE_FACES, body.faceCount)
    assertTrue("the inertia tensor came back empty", body.inertia.any { it != 0.0 })
    assertTrue("no face planes came back", body.faces.isNotEmpty())
  }

  private companion object {
    /** The rim's segments plus its two ends (`ShapeGeometry`, `coinVertices`). */
    const val COIN_FACES = 26
    const val CUBE_FACES = 6

    /** How far a die's mass may sit from its centre, as a share of its radius. */
    const val OFF_CENTRE = 0.001

    /** How far apart two faces of one solid may sit, as a share of the inradius. */
    const val FACE_SPREAD = 0.001

    /** How far two principal moments that should match may be apart, as a share. */
    const val MOMENT_TOLERANCE = 0.001

    const val SIZE = 3
    const val SWEEPS = 16
    const val TINY = 1e-12
  }
}
