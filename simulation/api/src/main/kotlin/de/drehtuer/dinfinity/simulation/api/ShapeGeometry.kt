package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.sqrt

/**
 * The geometry of the catalogue solids: which way each readable position
 * faces, and how big the solid is for its nominal size.
 *
 * Two things depend on this and nothing else may re-derive them. Reading a die
 * asks which position is nearest up (`docs/physics-and-rendering.md`); the
 * table's capacity rule asks how much floor a die covers (`docs/tables.md`).
 * Both have to agree with the mesh the renderer draws and the hull the solver
 * collides, which is why these are closed forms rather than measurements of a
 * model somebody exported.
 *
 * **Face order.** Positions are numbered from the top of the reference
 * orientation downwards, and anticlockwise around each ring starting from the
 * `+x` side. That is a choice, not a law, but it is a *fixed* choice: it is
 * the order a set file's `faces` list is read in and the order the texture
 * atlas fills its cells (`docs/dice-sets.md`, "Shape catalogue"). Changing it
 * would silently repaint every die of every set ever published.
 */
object ShapeGeometry {
  /** How thick a coin is, as a fraction of its width. */
  const val COIN_THICKNESS_RATIO: Double = 0.25

  /**
   * The outward direction of every readable position of [shape], in the
   * catalogue's face order.
   *
   * For a shape read [de.drehtuer.dinfinity.core.model.FaceRead.VertexUp]
   * these are vertex directions rather than face normals — a tetrahedron
   * resting on a face has no face pointing up, so its numbers belong to its
   * corners (`docs/physics-and-rendering.md`).
   */
  fun directionsOf(shape: DieShape): List<Vector3> = SOLIDS.getValue(shape).directions

  /**
   * The radius of the sphere that contains a die of this shape whose nominal
   * size is one millimetre.
   *
   * Nominal size is what a dice maker quotes: the edge length for a
   * polyhedron, the diameter for the coin. A "16 mm d6" is therefore a cube
   * with 16 mm edges, whose bounding sphere has a radius of 16·√3/2 ≈ 13.9 mm
   * — the number every worked figure in `docs/tables.md`'s capacity table is
   * built on.
   */
  fun boundingRadiusPerSize(shape: DieShape): Double = RADII.getValue(shape)

  private val RADII: Map<DieShape, Double> =
    mapOf(
      // A coin's furthest point from its middle is the corner of its rim.
      DieShape.Coin to sqrt(QUARTER + COIN_THICKNESS_RATIO * COIN_THICKNESS_RATIO / FOUR),
      DieShape.Tetrahedron to sqrt(SIX) / FOUR,
      DieShape.Cube to sqrt(THREE) / TWO,
      DieShape.Octahedron to sqrt(TWO) / TWO,
      // A trapezohedron is fixed by its own two conditions — flat kite faces
      // and every corner on one sphere — and its radius falls out of them
      // rather than out of a closed form worth writing down. See `Solids`.
      DieShape.PentagonalTrapezohedron to Solids.trapezohedronRadiusPerEdge(PENTAGONAL.toInt()),
      DieShape.Dodecahedron to (sqrt(THREE) / FOUR) * (1 + sqrt(FIVE)),
      DieShape.EnneagonalTrapezohedron to Solids.trapezohedronRadiusPerEdge(ENNEAGONAL.toInt()),
      DieShape.Icosahedron to sqrt(TEN + TWO * sqrt(FIVE)) / FOUR,
    )

  /**
   * The corners of [shape], as unit vectors in the same orientation as
   * [directionsOf].
   *
   * This is the convex hull the solver collides and the renderer draws. It and
   * the face directions are built from the same construction and turned by the
   * same rotation, so the face a die is read from and the face it landed on
   * are the same face by construction rather than by inspection — three
   * descriptions of one solid being three chances to be a hundredth of a
   * degree apart.
   */
  fun verticesOf(shape: DieShape): List<Vector3> = SOLIDS.getValue(shape).vertices

  /**
   * The hull of [die] in millimetres, at [scale].
   *
   * Every catalogue solid has all its corners on one sphere, so the hull is
   * the unit corners times the bounding radius — which is the same number the
   * table's capacity rule shares the floor out by (`docs/tables.md`).
   */
  fun hullOf(
    die: de.drehtuer.dinfinity.core.model.Die,
    scale: Double = 1.0,
  ): List<Vector3> {
    val radius = boundingRadiusPerSize(die.shape) * die.material.sizeMm * scale
    return verticesOf(die.shape).map { it * radius }
  }

  /** One solid: what it is read from, and what it is made of. */
  private data class Solid(
    val directions: List<Vector3>,
    val vertices: List<Vector3>,
  )

  /**
   * A solid turned so its first readable position points straight up, faces
   * and corners together.
   *
   * Turning them separately would be two chances to get it right. Built out of
   * cube corners a tetrahedron has no corner up and an octahedron no face up,
   * so without this the "reference orientation" would be a die balanced on an
   * edge, which is not a thing a die is ever in.
   */
  private fun upright(
    directions: List<Vector3>,
    vertices: List<Vector3>,
  ): Solid {
    val rotation = uprightRotation(directions)
    return Solid(
      directions = directions.turnedAndOrdered(rotation),
      vertices = vertices.turnedAndOrdered(rotation),
    )
  }

  private val TETRAHEDRON_CORNERS =
    listOf(
      Vector3(1.0, 1.0, 1.0),
      Vector3(1.0, -1.0, -1.0),
      Vector3(-1.0, 1.0, -1.0),
      Vector3(-1.0, -1.0, 1.0),
    )

  private val CUBE_FACES =
    listOf(
      Vector3(1.0, 0.0, 0.0),
      Vector3(-1.0, 0.0, 0.0),
      Vector3(0.0, 1.0, 0.0),
      Vector3(0.0, -1.0, 0.0),
      Vector3(0.0, 0.0, 1.0),
      Vector3(0.0, 0.0, -1.0),
    )

  private val SOLIDS: Map<DieShape, Solid> =
    mapOf(
      DieShape.Coin to
        upright(
          directions = listOf(Vector3.Up, -Vector3.Up),
          vertices = coinVertices(COIN_THICKNESS_RATIO),
        ),
      // Read from a vertex, so its directions are its corners: the same list.
      DieShape.Tetrahedron to
        upright(
          directions = TETRAHEDRON_CORNERS,
          vertices = TETRAHEDRON_CORNERS,
        ),
      DieShape.Cube to
        upright(
          directions = CUBE_FACES,
          vertices = Solids.signs(1.0, 1.0, 1.0),
        ),
      DieShape.Octahedron to
        upright(
          directions = Solids.signs(1.0, 1.0, 1.0),
          vertices = CUBE_FACES,
        ),
      DieShape.PentagonalTrapezohedron to
        upright(
          directions = Solids.trapezohedron(PENTAGONAL.toInt()),
          vertices = Solids.trapezohedronVertices(PENTAGONAL.toInt()),
        ),
      // A dodecahedron's faces point at an icosahedron's corners, and the
      // other way round: each is the other's dual.
      DieShape.Dodecahedron to
        upright(
          directions = Solids.icosahedronVertices(),
          vertices = Solids.dodecahedronVertices(),
        ),
      DieShape.EnneagonalTrapezohedron to
        upright(
          directions = Solids.trapezohedron(ENNEAGONAL.toInt()),
          vertices = Solids.trapezohedronVertices(ENNEAGONAL.toInt()),
        ),
      DieShape.Icosahedron to
        upright(
          directions = Solids.dodecahedronVertices(),
          vertices = Solids.icosahedronVertices(),
        ),
    )

  private const val QUARTER = 0.25
  private const val TWO = 2.0
  private const val THREE = 3.0
  private const val FOUR = 4.0
  private const val FIVE = 5.0
  private const val SIX = 6.0
  private const val TEN = 10.0
  private const val PENTAGONAL = 5.0
  private const val ENNEAGONAL = 9.0
}
