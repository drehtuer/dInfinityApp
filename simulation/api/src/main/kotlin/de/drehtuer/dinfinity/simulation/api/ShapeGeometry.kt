package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.CoinShape
import de.drehtuer.dinfinity.core.model.DieShape

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
  /**
   * How thick a coin is, as a fraction of its width.
   *
   * The catalogue's, not this object's: a disc is the one solid whose
   * proportions are a decision rather than a name, and the volume a weight is
   * worked out from has to be the volume of the hull built here
   * ([CoinShape]).
   */
  const val COIN_THICKNESS_RATIO: Double = CoinShape.THICKNESS_RATIO

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
   * simply the unit corners times the die's own bounding radius — which is the
   * same number the table's capacity rule shares the floor out by
   * (`docs/tables.md`).
   *
   * That radius is half the die's `size_mm`, whatever shape it is: a 16 mm die
   * is 16 mm across. It used to be `size_mm` read as an *edge* length and
   * multiplied by the solid's circumradius-per-edge, which is the convention a
   * dice maker quotes but is not the one anybody means — a d12 with 16 mm
   * edges is 45 mm across, and dice that size under ordinary gravity fall
   * slowly enough to look weightless (`docs/dice-sets.md`, "Size").
   */
  fun hullOf(
    die: de.drehtuer.dinfinity.core.model.Die,
    scale: Double = 1.0,
  ): List<Vector3> {
    val radius = die.material.boundingRadiusMm * scale
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

  private const val PENTAGONAL = 5.0
  private const val ENNEAGONAL = 9.0
}
