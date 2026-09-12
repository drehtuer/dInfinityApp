package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.DieShape
import kotlin.math.PI
import kotlin.math.cos
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
  fun directionsOf(shape: DieShape): List<Vector3> = DIRECTIONS.getValue(shape)

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
      // A trapezohedron with every vertex on one sphere and every edge the
      // same length — a real d10 — works out at exactly cos(π / 2n).
      DieShape.PentagonalTrapezohedron to cos(PI / (TWO * PENTAGONAL)),
      DieShape.Dodecahedron to (sqrt(THREE) / FOUR) * (1 + sqrt(FIVE)),
      DieShape.EnneagonalTrapezohedron to cos(PI / (TWO * ENNEAGONAL)),
      DieShape.Icosahedron to sqrt(TEN + TWO * sqrt(FIVE)) / FOUR,
    )

  private val DIRECTIONS: Map<DieShape, List<Vector3>> =
    mapOf(
      DieShape.Coin to order(listOf(Vector3.Up, -Vector3.Up)),
      // Read from a vertex, so these are the corners rather than the faces.
      DieShape.Tetrahedron to
        order(
          listOf(
            Vector3(1.0, 1.0, 1.0),
            Vector3(1.0, -1.0, -1.0),
            Vector3(-1.0, 1.0, -1.0),
            Vector3(-1.0, -1.0, 1.0),
          ),
        ),
      DieShape.Cube to
        order(
          listOf(
            Vector3(1.0, 0.0, 0.0),
            Vector3(-1.0, 0.0, 0.0),
            Vector3(0.0, 1.0, 0.0),
            Vector3(0.0, -1.0, 0.0),
            Vector3(0.0, 0.0, 1.0),
            Vector3(0.0, 0.0, -1.0),
          ),
        ),
      DieShape.Octahedron to order(Solids.signs(1.0, 1.0, 1.0)),
      DieShape.PentagonalTrapezohedron to order(Solids.trapezohedron(PENTAGONAL.toInt())),
      DieShape.Dodecahedron to order(Solids.icosahedronVertices()),
      DieShape.EnneagonalTrapezohedron to order(Solids.trapezohedron(ENNEAGONAL.toInt())),
      DieShape.Icosahedron to order(Solids.dodecahedronVertices()),
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
