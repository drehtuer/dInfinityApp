package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.CoinShape
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.LongDieShape

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
   * catalogue's face order: the normal of every face a value is read from.
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
   * The outward normals of the faces of [shape] that carry no value: the
   * eight triangles of a long d4's two ends, which it never rests on
   * ([LongDieShape]). Empty for every other shape — a coin's rim is a prism
   * standing in for a cylinder and is built where it is drawn.
   */
  fun bareDirectionsOf(shape: DieShape): List<Vector3> = SOLIDS.getValue(shape).bare

  /**
   * Which readable position of [shape] faces the opposite way to each of
   * them, in the catalogue's face order, or null for a position that has no
   * opposite at all.
   *
   * What pairs the faces of a die that is numbered the way a real one is: 2
   * across from 5 on a d6, 1 across from 20 on a d20
   * (`docs/dice-sets.md`, "Numbering"). It is read off the same directions
   * the die is scored from rather than written down as a table per shape,
   * because a table would be a second description of the face order and the
   * two would come apart the first time either was touched.
   *
   * Every solid in the catalogue has an opposite for every position: the
   * long d4's four long faces are two pairs, so it is numbered 1 across from
   * 4 and 2 across from 3, as a real one is.
   */
  fun oppositesOf(shape: DieShape): List<Int?> {
    val directions = directionsOf(shape).map(Vector3::normalised)
    return directions.map { direction ->
      directions.indices.firstOrNull { (directions[it] + direction).length < FACING_BACK }
    }
  }

  /**
   * The hull of [die] in millimetres, at [scale].
   *
   * Every catalogue solid has its furthest corners on one sphere of radius
   * one, so the hull is simply those corners times the die's own bounding
   * radius — which is the same number the table's capacity rule shares the
   * floor out by (`docs/tables.md`). The long d4's points are on that sphere
   * and its prism corners inside it ([LongDieShape]).
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

  /** One solid: what it is read from, what it is made of, and its faces that read nothing. */
  private data class Solid(
    val directions: List<Vector3>,
    val vertices: List<Vector3>,
    val bare: List<Vector3> = emptyList(),
  )

  /**
   * A solid turned so its first readable position points straight up, faces
   * and corners together.
   *
   * Turning them separately would be two chances to get it right. Built out of
   * cube corners an octahedron has no face up, so without this the "reference
   * orientation" would be a die balanced on an edge, which is not a thing a
   * die is ever in.
   *
   * A solid's corners are put on the unit sphere on the way, which is where
   * every corner of the Platonic solids and the trapezohedra is — unless
   * [onSphere] is false, for the long d4, whose prism corners are inside it.
   */
  private fun upright(
    directions: List<Vector3>,
    vertices: List<Vector3>,
    bare: List<Vector3> = emptyList(),
    onSphere: Boolean = true,
  ): Solid {
    val rotation = uprightRotation(directions)
    return Solid(
      directions = directions.turnedAndOrdered(rotation),
      vertices = if (onSphere) vertices.turnedAndOrdered(rotation) else vertices.map(rotation::rotate),
      bare = bare.turnedAndOrdered(rotation),
    )
  }

  /**
   * The long d4 lying along `x`, a long face up: its four long faces, its
   * two points and eight prism corners, and the eight triangles of its ends
   * ([LongDieShape]). An end's triangle over the side `d` at the end `s` faces
   * `d·h + x·s·w` — the end's length `h` across, its half-width `w` along —
   * which is the plane through the point and the prism's edge on that side.
   */
  private val LONG_DIE: Solid =
    run {
      val w = LongDieShape.HALF_WIDTH
      val b = LongDieShape.BODY_HALF_LENGTH
      val h = 1.0 - b
      val sides =
        listOf(Vector3(0.0, 0.0, 1.0), Vector3(0.0, 0.0, -1.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, -1.0, 0.0))
      val ends = listOf(1.0, -1.0)
      upright(
        directions = sides,
        vertices =
          ends.map { s -> Vector3(s, 0.0, 0.0) } +
            ends.flatMap { s ->
              Solids.signs(1.0, 1.0, 1.0).filter { it.x > 0 }.map { Vector3(s * b, it.y * w, it.z * w) }
            },
        bare = ends.flatMap { s -> sides.map { side -> side * h + Vector3(s * w, 0.0, 0.0) } },
        onSphere = false,
      )
    }

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
      DieShape.ElongatedSquareBipyramid to LONG_DIE,
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

  /**
   * How near two directions have to be to opposite before they are called
   * opposite.
   *
   * Every catalogue solid either has an exact opposite for each position or
   * nothing within half a radian of one, so the number only has to be small
   * enough to be arithmetic slack rather than a judgement about geometry.
   */
  private const val FACING_BACK = 1e-6

  private const val PENTAGONAL = 5.0
  private const val ENNEAGONAL = 9.0
}
