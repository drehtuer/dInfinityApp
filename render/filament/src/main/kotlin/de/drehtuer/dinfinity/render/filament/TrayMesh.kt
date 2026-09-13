package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.Exact
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import kotlin.math.PI
import kotlin.math.abs

/**
 * The tray the dice land in, as a mesh (`docs/tables.md`, "Geometry").
 *
 * The mesh never changes and no package can supply one: a table varies its
 * colours, textures, friction and sound, never its shape, so nobody can ship a
 * table with a hole in the floor. What is here is therefore a function of the
 * tray's own geometry and nothing else.
 *
 * **Only the inside is modelled** — the floor, the inner walls up to the rim,
 * and the flat band across the top of the rim. The camera looks down into the
 * tray, so the outside of the walls is never in shot; the near wall's inner
 * face points away and is culled, which is what lets the player see over it
 * into the tray rather than at the back of it.
 *
 * The walls are drawn to the **rim**, 60 mm, not to the 200 mm ceiling the
 * physics closes the box with. Those two heights are not a contradiction and
 * `docs/tables.md` says why: the collision box has a lid because dice must not
 * leave; the drawn box has a rim because a dice tray looks like a dice tray.
 */
data class TrayMesh(
  val geometry: TableGeometry,
  val surfaces: List<TraySurface>,
) {
  /** Every surface of one part, for the renderer to give one material to. */
  fun partsOf(part: TrayPart): List<TraySurface> = surfaces.filter { it.part == part }

  companion object {
    /** How wide the flat top of the wall is. */
    const val RIM_WIDTH_MM: Double = 6.0

    /**
     * How many quads each rounded corner is drawn with.
     *
     * A corner is a quarter of a 12 mm circle, about 19 mm of arc on a tray
     * 240 mm long. Six segments put a corner within a tenth of a millimetre of
     * round, which is under a pixel at any size this is ever drawn at.
     */
    const val CORNER_SEGMENTS: Int = 6

    /** The tray [geometry] wears, textured for [look]. */
    fun of(
      geometry: TableGeometry,
      look: TableLook = TableLook(id = "plain", name = "Plain"),
    ): TrayMesh {
      val outline = Outline.of(geometry)
      return TrayMesh(
        geometry = geometry,
        surfaces = listOf(floor(outline, geometry, look)) + walls(outline, geometry, look) + rim(outline, geometry),
      )
    }

    /**
     * The floor: one fan from the middle out to the rounded outline.
     *
     * A fan rather than two triangles, because the outline is not a rectangle
     * — its corners are arcs, and a rectangle would leave four slivers of
     * nothing where the dice actually land.
     */
    private fun floor(
      outline: Outline,
      geometry: TableGeometry,
      look: TableLook,
    ): TraySurface {
      val corners = listOf(Vector3.Zero) + outline.points.map(Outline.Point::position)
      return TraySurface(
        part = TrayPart.Floor,
        positions = corners,
        normal = Vector3.Up,
        // The floor's texture runs along the tray, so u increases along +x.
        tangent = Vector3(1.0, 0.0, 0.0),
        uvs = corners.map { floorUv(it, geometry, look.floorTiling) },
        triangles =
          outline.points.indices.flatMap { step ->
            listOf(0, 1 + step, 1 + (step + 1) % outline.points.size)
          },
      )
    }

    /** The texture runs along the tray, repeating as often as the look asks. */
    private fun floorUv(
      position: Vector3,
      geometry: TableGeometry,
      tiling: TableLook.Tiling,
    ): TextureCoordinate =
      TextureCoordinate(
        u = (position.x / geometry.longSideMm + HALF) * tiling.acrossLongSide,
        v = (position.y / geometry.shortSideMm + HALF) * tiling.acrossShortSide,
      )

    /**
     * The inner face of the wall, one quad per step of the outline.
     *
     * Across the wall the texture repeats as often as the side that stretch of
     * wall runs along — a wall down the long side gets `acrossLongSide`, one
     * across the short side gets `acrossShortSide`, and a corner takes the
     * rate of whichever it is nearer. The texture walks continuously around
     * the tray rather than restarting per stretch, so a change of rate shows
     * as the pattern stretching rather than as a seam. Up the wall it repeats
     * once: a rim is 60 mm and nobody tiles that.
     */
    private fun walls(
      outline: Outline,
      geometry: TableGeometry,
      look: TableLook,
    ): List<TraySurface> {
      val height = Vector3(0.0, 0.0, geometry.wallHeightMm)
      val across = outline.acrossTheWall(geometry, look.wallTiling)
      return outline.steps().map { step ->
        // u runs around the tray, which is the way this stretch of wall goes.
        val along = (step.to.position - step.from.position).normalised()
        TraySurface(
          part = TrayPart.Wall,
          // Up the near edge, along the top and back down: wound so the
          // triangles face the way the surface does, which is inwards.
          positions =
            listOf(
              step.from.position,
              step.from.position + height,
              step.to.position + height,
              step.to.position,
            ),
          // Into the tray: the face a player looking down at it can see.
          //
          // Square to this quad, not radial. On a rounded corner the chord
          // between two points of the arc is not perpendicular to either
          // point's own outward direction, and a normal that is not
          // perpendicular to its own surface lights it wrongly — by up to half
          // a segment, which is where the faceting of a six-segment corner
          // actually is.
          normal = -cross(along, Vector3.Up),
          tangent = along,
          uvs =
            listOf(
              TextureCoordinate(across[step.index], 1.0),
              TextureCoordinate(across[step.index], 0.0),
              TextureCoordinate(across[step.index + 1], 0.0),
              TextureCoordinate(across[step.index + 1], 1.0),
            ),
          triangles = QUAD_FAN,
        )
      }
    }

    /**
     * The flat top of the wall, which is what the tray reads as from above.
     *
     * It carries no texture of its own: it is the wall's material seen
     * end-on, and a band six millimetres wide is not where anybody looks.
     */
    private fun rim(
      outline: Outline,
      geometry: TableGeometry,
    ): List<TraySurface> {
      val top = Vector3(0.0, 0.0, geometry.wallHeightMm)
      return outline.steps().map { step ->
        TraySurface(
          part = TrayPart.Rim,
          // Outwards first, so the band is wound to face up like the floor.
          positions =
            listOf(
              step.from.position + top,
              step.from.position + step.from.outward * RIM_WIDTH_MM + top,
              step.to.position + step.to.outward * RIM_WIDTH_MM + top,
              step.to.position + top,
            ),
          normal = Vector3.Up,
          tangent = (step.to.position - step.from.position).normalised(),
          uvs = emptyList(),
          triangles = QUAD_FAN,
        )
      }
    }

    /** Two triangles, wound the same way round as the corners are listed. */
    private val QUAD_FAN = listOf(0, 1, 2, 0, 2, 3)

    private const val HALF = 0.5
  }
}

/** Which part of the tray a surface belongs to, and so which material it takes. */
enum class TrayPart {
  /** Where the dice land. Takes the look's floor colour and texture. */
  Floor,

  /** The inside of the wall, up to the rim. Takes the wall colour and texture. */
  Wall,

  /** The flat top of the wall, seen from above. Wall colour, no texture. */
  Rim,
}

/**
 * One surface of the tray: a convex polygon wound anticlockwise as seen from
 * the side its [normal] points at.
 *
 * @param tangent which way the texture runs across the surface — `u`
 *   increasing. With [normal] it is the frame a renderer needs for lighting.
 * @param uvs one per corner, or empty where the surface takes a plain colour.
 */
data class TraySurface(
  val part: TrayPart,
  override val positions: List<Vector3>,
  override val normal: Vector3,
  override val tangent: Vector3,
  override val uvs: List<TextureCoordinate>,
  override val triangles: List<Int>,
) : Surface {
  init {
    require(uvs.isEmpty() || uvs.size == positions.size) {
      "a surface has a texture coordinate per corner or none at all, not ${uvs.size} for ${positions.size}"
    }
  }
}

/**
 * The line where the floor meets the walls: a rectangle with its corners
 * rounded off, walked anticlockwise from the `+x` side.
 *
 * Anticlockwise from `+x` on purpose — it is the direction the shape
 * catalogue numbers a ring of faces in, and having one answer to "which way
 * round" in the project is worth more than either answer
 * (`docs/dice-sets.md`).
 *
 * Each point carries the direction it faces away from the tray, which is what
 * the wall's normal and the rim's outer edge are both built from. Computing it
 * here is exact for both the straight stretches and the arcs; recovering it
 * afterwards from the neighbours would not be.
 */
private class Outline(
  val points: List<Point>,
) {
  /** One point of the outline, on the floor. */
  data class Point(
    val position: Vector3,
    val outward: Vector3,
  )

  /** One stretch of wall: from one point of the outline to the next. */
  data class Step(
    val index: Int,
    val from: Point,
    val to: Point,
  )

  /** Every step from one point to the next, closing the loop. */
  fun steps(): List<Step> = points.indices.map { Step(it, points[it], points[(it + 1) % points.size]) }

  /**
   * How far around the wall texture each point of the outline is, walked from
   * the start.
   *
   * It is a walk rather than a formula because the rate changes: a stretch of
   * wall running along the long side repeats as often as the look asks for
   * that side, one along the short side as often as it asks for that. Walked,
   * the texture is continuous everywhere and merely stretches where the rate
   * changes. Computed per stretch from its own start, it would jump — eight
   * visible seams around a tray nobody would be able to explain.
   *
   * The array is one longer than the outline: the last entry closes the loop.
   */
  fun acrossTheWall(
    geometry: TableGeometry,
    tiling: TableLook.Tiling,
  ): DoubleArray {
    val walked = DoubleArray(points.size + 1)
    steps().forEach { step ->
      val alongLong = abs(step.from.outward.y) > abs(step.from.outward.x)
      val side = if (alongLong) geometry.longSideMm else geometry.shortSideMm
      val repeats = if (alongLong) tiling.acrossLongSide else tiling.acrossShortSide
      val length = (step.to.position - step.from.position).length
      walked[step.index + 1] = walked[step.index] + length * repeats / side
    }
    return walked
  }

  companion object {
    /** Shorter than this and two points of the outline are one point. */
    private const val NOTHING = 1e-9

    /** The outline of [geometry]'s floor. */
    fun of(geometry: TableGeometry): Outline {
      val halfLong = geometry.longSideMm / 2
      val halfShort = geometry.shortSideMm / 2
      val radius = geometry.cornerRadiusMm.coerceAtMost(minOf(halfLong, halfShort))
      val centres =
        listOf(
          Vector3(halfLong - radius, halfShort - radius, 0.0),
          Vector3(-halfLong + radius, halfShort - radius, 0.0),
          Vector3(-halfLong + radius, -halfShort + radius, 0.0),
          Vector3(halfLong - radius, -halfShort + radius, 0.0),
        )
      return Outline(distinct(centres.flatMapIndexed { quarter, centre -> arc(centre, radius, quarter) }))
    }

    /**
     * [points] with any that landed on top of each other removed.
     *
     * On a tray narrow enough that its two corner arcs meet, the straight
     * stretch between them has no length at all, so the end of one arc and the
     * start of the next are the same point. Left in, that is a wall quad with
     * no width — no winding, and no direction for its texture to run in — and
     * a floor triangle with no area.
     */
    private fun distinct(points: List<Point>): List<Point> =
      points.filterIndexed { index, point ->
        val before = points[(index - 1 + points.size) % points.size]
        (point.position - before.position).length > NOTHING
      }

    /**
     * One rounded corner, both ends included.
     *
     * The straight stretches are not generated at all: each is the step from
     * the last point of one corner to the first point of the next, which
     * `steps` produces for free and which cannot then disagree with the arcs
     * about where a corner ends.
     */
    private fun arc(
      centre: Vector3,
      radius: Double,
      quarter: Int,
    ): List<Point> {
      val from = quarter * QUARTER_TURN
      return (0..TrayMesh.CORNER_SEGMENTS).map { step ->
        val angle = from + QUARTER_TURN * step / TrayMesh.CORNER_SEGMENTS
        val outward = Vector3(Exact.cos(angle), Exact.sin(angle), 0.0)
        Point(position = centre + outward * radius, outward = outward)
      }
    }

    private val QUARTER_TURN = PI / 2
  }
}
