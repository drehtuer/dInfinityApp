package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.Typesetter
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rounded die against the sharp one it is cut from.
 *
 * The drawn die is rounded by the radius the solver rounds its hull by, so
 * every check here is that the picture tells the truth about that solid: a
 * face is where the solid's face is, the drawn die never pokes out of the
 * sharp hull, its corners stand in by exactly what the solver's do, and what
 * is printed on a face is printed where it always was
 * (`docs/architecture.md`, decision 91).
 */
class RoundedEdgesTest {
  @Test
  fun `rounding of nought draws the sharp die exactly`() {
    DieShape.entries.forEach { shape ->
      val sharp = DieMesh.of(shape, rounding = 0.0)

      assertEquals("${shape.id} is not drawn from its faces", sharp.faces, sharp.surfaces)
      assertEquals(
        "${shape.id} rounded by nothing packs differently from the sharp die",
        GpuMesh.of(sharp.faces),
        GpuMesh.of(sharp.surfaces),
      )
    }
  }

  @Test
  fun `rounding changes how a die is drawn and not what its faces are`() {
    // The faces are what a label's room is measured on and what the face
    // designer agrees with; rounding is only the picture of them.
    DieShape.entries.forEach { shape ->
      assertEquals("${shape.id}'s faces moved", DieMesh.of(shape, 0.0).faces, rounded(shape).faces)
    }
  }

  @Test
  fun `every flat face is drawn on its own plane, only smaller`() {
    DieShape.entries.forEach { shape ->
      val mesh = rounded(shape)
      val flats = flatsOf(mesh)
      assertEquals("${shape.id} draws one flat per face", mesh.faces.size, flats.size)
      mesh.faces.zip(flats).forEach { (sharp, flat) ->
        val height = sharp.positions.first() dot sharp.normal
        assertEquals(sharp.normal, flat.normal)
        assertEquals(sharp.index, flat.index)
        assertEquals(sharp.reads, flat.reads)
        flat.positions.forEach { corner ->
          assertEquals("${shape.id} face ${sharp.index} left its plane", height, corner dot sharp.normal, PLANE)
          assertTrue(
            "${shape.id} face ${sharp.index} is drawn outside the face it is",
            inside(sharp.positions, sharp.normal, corner),
          )
        }
        assertTrue(
          "${shape.id} face ${sharp.index} was not made any smaller",
          area(flat.positions, flat.normal) < area(sharp.positions, sharp.normal),
        )
      }
    }
  }

  @Test
  fun `a flat face is painted from exactly where the sharp face was`() {
    // Fitted from the sharp face's own corners and texture coordinates, so the
    // check does not lean on the function that made them.
    DieShape.entries.forEach { shape ->
      val mesh = rounded(shape)
      mesh.faces.zip(flatsOf(mesh)).filter { it.first.index != null }.forEach { (sharp, flat) ->
        flat.positions.zip(flat.uvs).forEach { (corner, uv) ->
          val expected = affine(sharp, corner)
          assertEquals("${shape.id} face ${sharp.index} slid across its cell", expected.u, uv.u, PLANE)
          assertEquals("${shape.id} face ${sharp.index} slid down its cell", expected.v, uv.v, PLANE)
        }
      }
    }
  }

  @Test
  fun `a rounded edge is painted from its own face's cell and nobody else's`() {
    // The bend continues the face's artwork, so it samples the face's own
    // polygon in the face's own cell — never the next cell over, and never the
    // corner of the cell outside the face.
    DieShape.entries.forEach { shape ->
      val mesh = rounded(shape)
      curvesOf(mesh).filter { it.uvs.isNotEmpty() }.forEach { curve ->
        val sharp = mesh.faces.single { it.normal == curve.normal }
        curve.positions.forEach { point ->
          val onPlane = point + sharp.normal * ((sharp.positions.first() - point) dot sharp.normal)
          assertTrue(
            "${shape.id} face ${sharp.index}'s edge is painted from outside its face",
            inside(sharp.positions, sharp.normal, onPlane),
          )
        }
        curve.positions.zip(curve.uvs).forEach { (point, uv) ->
          val expected = affine(sharp, point)
          assertEquals(expected.u, uv.u, PLANE)
          assertEquals(expected.v, uv.v, PLANE)
        }
      }
    }
  }

  @Test
  fun `the rim of a coin and its rounded edges carry no texture`() {
    val mesh = rounded(DieShape.Coin)
    val rimNormals =
      mesh.faces
        .filter { it.index == null }
        .map { it.normal }
        .toSet()

    curvesOf(mesh).filter { it.normal in rimNormals }.forEach {
      assertTrue("a rim's bend has no cell to be painted from", it.uvs.isEmpty())
    }
  }

  @Test
  fun `every corner normal is one unit long and points out of the die`() {
    DieShape.entries.forEach { shape ->
      curvesOf(rounded(shape)).forEach { curve ->
        assertEquals("${shape.id} has a normal per corner", curve.positions.size, curve.normals.size)
        curve.normals.forEachIndexed { corner, normal ->
          assertEquals("${shape.id} has a normal that is not a direction", 1.0, normal.length, PLANE)
          assertTrue("${shape.id} has a normal pointing inwards", (normal dot curve.positions[corner]) > 0)
        }
      }
    }
  }

  @Test
  fun `every triangle of a rounded die faces outwards and agrees with its normals`() {
    DieShape.entries.forEach { shape ->
      rounded(shape).surfaces.forEach { surface ->
        surface.triangles.chunked(TRIANGLE).forEach { triangle -> checkTriangle(shape, surface, triangle) }
      }
    }
  }

  private fun checkTriangle(
    shape: DieShape,
    surface: Surface,
    triangle: List<Int>,
  ) {
    val (a, b, c) = triangle
    val p = surface.positions
    val wound = cross(p[b] - p[a], p[c] - p[a])
    assertTrue("${shape.id} has a triangle with no area", wound.length > AREA)
    val middle = (p[a] + p[b] + p[c]) * (1.0 / TRIANGLE)
    assertTrue("${shape.id} has a triangle wound inwards", (wound dot middle) > 0)
    val normals = surface.normals.ifEmpty { List(p.size) { surface.normal } }
    triangle.forEach {
      assertTrue("${shape.id} is lit from inside a triangle", (wound.normalised() dot normals[it]) > 0)
    }
  }

  @Test
  fun `a rounded die is closed, with no seam and no hole`() {
    DieShape.entries.forEach { shape ->
      val open =
        DieMesh
          .of(standard(shape), 1.0)
          .surfaces
          .flatMap(::edgesOf)
          .groupingBy { it }
          .eachCount()
          .filterValues { it != 2 }
      assertTrue("${shape.id} has ${open.size} edges not shared by two triangles", open.isEmpty())
    }
  }

  @Test
  fun `a die on a face is drawn on the felt, and never outside its own hull`() {
    // For a convex solid, how far the drawn die stops short of the sharp one
    // in a direction is the difference of how far each reaches that way —
    // which is the gap under a die resting with that side down.
    DieShape.entries.forEach { shape ->
      val hull = ShapeGeometry.verticesOf(shape)
      val mesh = rounded(shape)
      val drawn = mesh.surfaces.flatMap(Surface::positions)

      mesh.faces.forEach { face ->
        assertEquals("${shape.id} on a face is not on the felt", 0.0, gap(hull, drawn, face.normal), PLANE)
      }
      val closest = sphere().minOf { gap(hull, drawn, it) }
      assertTrue("${shape.id} is drawn poking out of its own hull by ${-closest}", closest >= -PLANE)
    }
  }

  @Test
  fun `the corners stand in from the sharp hull by what the solver's do`() {
    // The solver's die is the sharp hull rounded by the same radius, so how
    // far the drawn corners stand in from the sharp ones is how far the
    // solver's do: `gapOf`, measured here off the mesh rather than taken on
    // trust.
    DieShape.entries.forEach { shape ->
      val facets = facetsOf(shape)
      val radius = RoundedEdges.radiusFor(facets, standard(shape).material, 1.0)
      val drawn = rounded(shape).surfaces.flatMap(Surface::positions)
      val hull = ShapeGeometry.verticesOf(shape)
      val worst = (sphere() + hull.map(Vector3::normalised)).maxOf { gap(hull, drawn, it) }

      assertEquals("${shape.id}'s worst corner", RoundedEdges.gapOf(facets, radius), worst, GAP_SAMPLING)
    }
  }

  @Test
  fun `a 16 mm die is rounded by the radius the solver gives it`() {
    // Jolt's rule over a 16 mm die: 0.48 mm asked for, which every solid gets
    // but the d4, whose spike would stand more than half a millimetre inside
    // the sharp corner and is cut to 0.25 mm. The second figure is that
    // standing-in, the one `docs/physics-and-rendering.md` quotes.
    val boundingRadiusMm = 8.0
    val expected =
      mapOf(
        DieShape.Coin to (0.48 to 0.202),
        DieShape.Tetrahedron to (0.25 to 0.500),
        DieShape.Cube to (0.48 to 0.351),
        DieShape.Octahedron to (0.48 to 0.351),
        DieShape.PentagonalTrapezohedron to (0.48 to 0.238),
        DieShape.Dodecahedron to (0.48 to 0.124),
        DieShape.EnneagonalTrapezohedron to (0.48 to 0.210),
        DieShape.Icosahedron to (0.48 to 0.124),
      )
    expected.forEach { (shape, figures) ->
      val facets = facetsOf(shape)
      val radius = RoundedEdges.radiusFor(facets, standard(shape).material, 1.0)
      assertEquals("${shape.id}'s radius", figures.first, radius * boundingRadiusMm, MILLIMETRE)
      val corner = RoundedEdges.gapOf(facets, radius) * boundingRadiusMm
      assertEquals("${shape.id}'s corner", figures.second, corner, MILLIMETRE)
    }
  }

  @Test
  fun `a die shrunk by the capacity rule keeps the radius it asked for, until it is too thin for it`() {
    // The solver is handed the nominal size's radius whatever the scale, so a
    // half-size d6 is rounded by twice the share of itself. A coin shrunk far
    // enough would be thinner than twice that, and the radius stops at half
    // its thickness — Jolt's other limit, which no die the capacity rule
    // allows reaches (a coin at its floor of 0.4 is rounded by 0.15 of its
    // reach against a limit of 0.24), so it is asked about at a fifth.
    val d6 = facetsOf(DieShape.Cube)
    val material = standard(DieShape.Cube).material
    assertEquals(
      2 * RoundedEdges.radiusFor(d6, material, 1.0),
      RoundedEdges.radiusFor(d6, material, HALF),
      PLANE,
    )

    val coin = DieMesh.of(DieShape.Coin).faces
    val thickness = coin[0].positions.first().z - coin[1].positions.first().z
    val thin = RoundedEdges.radiusFor(facetsOf(DieShape.Coin), standard(DieShape.Coin).material, FIFTH)
    assertEquals("a shrunk coin is rounded by half its thickness", abs(thickness) / 2, thin, PLANE)
  }

  @Test
  fun `each shape is drawn in this many triangles and corners`() {
    // The rendered harness's budget is a hundred dice (`docs/physics-and-
    // rendering.md`, "Performance"). A hundred of the dearest, the coin, is a
    // hundred and five thousand triangles; a hundred d6 are twenty thousand.
    val expected =
      mapOf(
        DieShape.Coin to Counts(92, 144, 1052, 1680),
        DieShape.Tetrahedron to Counts(4, 12, 100, 156),
        DieShape.Cube to Counts(12, 24, 204, 312),
        DieShape.Octahedron to Counts(8, 24, 200, 312),
        DieShape.PentagonalTrapezohedron to Counts(20, 40, 260, 440),
        DieShape.Dodecahedron to Counts(36, 60, 516, 780),
        DieShape.EnneagonalTrapezohedron to Counts(36, 72, 468, 792),
        DieShape.Icosahedron to Counts(20, 60, 260, 540),
      )
    expected.forEach { (shape, counts) ->
      val sharp = GpuMesh.of(DieMesh.of(shape, 0.0).surfaces)
      val rounded = GpuMesh.of(rounded(shape).surfaces)
      assertEquals(
        shape.id,
        counts,
        Counts(sharp.triangleCount, sharp.vertexCount, rounded.triangleCount, rounded.vertexCount),
      )
    }
  }

  @Test
  fun `a printed number fits on the flat part of its face`() {
    // The number is sized against the sharp face, as it always was; the flat
    // that is left once the edges are rounded has to hold its ink, or the
    // rounding has bent a numeral over an edge. Every solid's numbers stay on
    // the flat with room to spare but one, measured: a d4's numbers are held
    // right up against its edges (`LabelRoom.cornered`), and their tips reach
    // [SPILL] of a cell — 0.19 mm on a 16 mm die — onto the start of the
    // bend. They are painted there rather than cut off, because the bend
    // samples its face's own cell.
    val furthest =
      DieShape.entries.associateWith { shape ->
        val mesh = rounded(shape)
        val grid = ShapeAtlas.gridFor(shape)
        val flats = flatsOf(mesh)
        DieNumbers.plan(standard(shape), mesh).maxOf { cell ->
          val polygon = FaceRoom.cornersOf(flats.single { it.index == cell.index }, grid, cell.column to cell.row)
          val ink = cell.marks.flatMap { Typesetter.lay(it.text, it.placement) }.flatMap { it.toList().chunked(2) }
          ink.maxOf { (x, y) -> outside(polygon, x, y) }
        }
      }
    furthest.forEach { (shape, cells) ->
      val allowed = if (shape == DieShape.Tetrahedron) SPILL else 0.0
      assertTrue("${shape.id}'s numbers run $cells of a cell off the flat", cells <= allowed)
    }
  }

  @Test
  fun `a rounded edge samples no ink, so a light line along it is not a number`() {
    // Light dashed lines along the edges of white-numbered resin dice looked
    // like ink picked up by the bends. They are not: sampled the way the GPU
    // samples it — bilinear, clamped — anywhere on any triangle of any bend,
    // the printed field stays short of the half that is the edge of a numeral.
    // The worst is a d10's 0.09 and a d18's 0.30; a d12's and a d20's are
    // nought. The d4 is the one exception, by design: its numbers spill onto
    // the start of the bend ([SPILL]) and are painted on round it. (The lines
    // were the lacquer's glint, thinner than a pixel — `DiceMaterial`'s
    // specular anti-aliasing.)
    DieShape.entries.filter { it != DieShape.Tetrahedron }.forEach { shape ->
      val die = standard(shape)
      val mesh = rounded(shape)
      val field = DieNumbers.fieldOf(die, mesh) ?: return@forEach
      val worst =
        curvesOf(mesh).filter { it.uvs.isNotEmpty() }.maxOf { curve ->
          curve.triangles.chunked(TRIANGLE).maxOf { (a, b, c) ->
            barycentric().maxOf { (s, t) ->
              val w = 1 - s - t
              sample(
                field,
                curve.uvs[a].u * w + curve.uvs[b].u * s + curve.uvs[c].u * t,
                curve.uvs[a].v * w + curve.uvs[b].v * s + curve.uvs[c].v * t,
              )
            }
          }
        }
      assertTrue("${shape.id}'s rounded edges sample ink: $worst", worst < HALF)
    }
  }

  @Test
  fun `a rounded mesh is worked out once per shape and radius`() {
    assertSame(rounded(DieShape.Icosahedron), rounded(DieShape.Icosahedron))
    assertSame(DieMesh.of(DieShape.Icosahedron), DieMesh.of(DieShape.Icosahedron, 0.0))
  }

  @Test
  fun `a die cannot be rounded by less than nothing`() {
    assertThrows(IllegalArgumentException::class.java) { RoundedEdges.of(facetsOf(DieShape.Cube), -0.01) }
  }

  @Test
  fun `a corner whose faces would not meet again once pulled in is refused`() {
    // Four faces meeting at one corner only stay meeting at one corner when
    // each is pulled in by the same amount if they lean evenly round it — every
    // catalogue solid's do. A lopsided pyramid's apex does not, and rounding
    // it would tear the apex into an edge this construction cannot draw.
    val apex = Vector3(0.0, 0.0, 1.0)
    val base = listOf(Vector3(2.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(-1.0, 0.0, 0.0), Vector3(0.0, -3.0, 0.0))
    val sides = base.indices.map { listOf(base[it], base[(it + 1) % base.size], apex) }
    val facets = (sides + listOf(base.reversed())).map { RoundedEdges.Facet(faceOf(it), null) }

    assertThrows(IllegalStateException::class.java) { RoundedEdges.of(facets, RADIUS) }
  }

  @Test
  fun `faces that all turn about one axis meet in no corner`() {
    val flat = listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(-1.0, 0.0, 0.0))

    assertThrows(IllegalArgumentException::class.java) { RoundedEdges.reachOf(flat) }
  }

  @Test
  fun `a corner is fanned from its own reach, or from the mean of its faces where that is outside them`() {
    val even = listOf(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0))
    val reach = RoundedEdges.reachOf(even)
    assertTrue(RoundedEdges.centreOf(even, reach).approximates(reach.normalised(), PLANE))
    assertTrue(RoundedEdges.centreOf(even.reversed(), reach).approximates(reach.normalised(), PLANE))

    // Three faces leaning so unevenly that the point equally far from all of
    // them is outside the corner they make: a spherical triangle with an
    // obtuse angle has its circumcentre outside it.
    val lopsided =
      listOf(
        Vector3(-1.0, 0.0, STEEP).normalised(),
        Vector3(1.0, 0.0, STEEP).normalised(),
        Vector3(0.0, NUDGE, STEEP).normalised(),
      )
    val mean = lopsided.reduce(Vector3::plus).normalised()
    assertTrue(RoundedEdges.centreOf(lopsided, RoundedEdges.reachOf(lopsided)).approximates(mean, PLANE))
  }

  @Test
  fun `an arc keeps its ends exactly and turns evenly between them`() {
    val from = Vector3(1.0, 0.0, 0.0)
    val to = Vector3(0.0, 1.0, 0.0)

    assertSame(from, RoundedEdges.arc(from, to, 0, 3))
    assertSame(to, RoundedEdges.arc(from, to, 3, 3))
    val third = RoundedEdges.arc(from, to, 1, 3)
    assertEquals(1.0, third.length, PLANE)
    assertEquals(PI / 6, acos(third dot from), PLANE)
  }

  @Test
  fun `a bend is drawn in steps of no more than thirty degrees`() {
    val up = Vector3(0.0, 0.0, 1.0)
    assertEquals(1, RoundedEdges.stepsBetween(up, up))
    assertEquals(2, RoundedEdges.stepsBetween(up, Vector3(1.0, 0.0, 0.0)))
    assertEquals(1, RoundedEdges.stepsBetween(up, Vector3(sin(PI / 3), 0.0, cos(PI / 3))))
  }

  /** A plain face through [corners], wound anticlockwise from outside. */
  private fun faceOf(corners: List<Vector3>): MeshFace {
    val normal = cross(corners[1] - corners[0], corners[2] - corners[0]).normalised()
    return MeshFace(
      index = null,
      positions = corners,
      normal = normal,
      tangent = (corners[1] - corners[0]).normalised(),
      uvs = emptyList(),
      triangles = (1 until corners.size - 1).flatMap { listOf(0, it, it + 1) },
    )
  }

  /** Triangles and corners, sharp and rounded. */
  private data class Counts(
    val sharpTriangles: Int,
    val sharpCorners: Int,
    val roundedTriangles: Int,
    val roundedCorners: Int,
  )

  /** The catalogue die of [shape], at the built-in size. */
  private fun standard(shape: DieShape): Die = Die.standard("d${shape.faceCount}", shape)

  /** What [shape] is drawn from, at the built-in size and full scale. */
  private fun rounded(shape: DieShape): DieMesh = DieMesh.of(standard(shape), scale = 1.0)

  /** The drawn flat faces, in the sharp faces' order. */
  private fun flatsOf(mesh: DieMesh): List<MeshFace> = mesh.surfaces.filterIsInstance<MeshFace>()

  private fun curvesOf(mesh: DieMesh): List<Curved> = mesh.surfaces.filterIsInstance<Curved>()

  private fun facetsOf(shape: DieShape): List<RoundedEdges.Facet> =
    DieMesh.of(shape, 0.0).faces.map { RoundedEdges.Facet(it, null) }

  /** How much further the hull reaches in [direction] than the drawn die does. */
  private fun gap(
    hull: List<Vector3>,
    drawn: List<Vector3>,
    direction: Vector3,
  ): Double = hull.maxOf { it dot direction } - drawn.maxOf { it dot direction }

  /** Evenly spread directions, a Fibonacci sphere's worth. */
  private fun sphere(): List<Vector3> =
    (0 until DIRECTIONS).map { step ->
      val z = 1 - 2 * (step + 0.5) / DIRECTIONS
      val ring = sqrt(1 - z * z)
      val turn = step * GOLDEN_ANGLE
      Vector3(ring * cos(turn), ring * sin(turn), z)
    }

  /** The sharp face's texture coordinate at [point], fitted from three of its corners. */
  private fun affine(
    sharp: MeshFace,
    point: Vector3,
  ): TextureCoordinate {
    val (a, b, c) = sharp.positions
    val (ua, ub, uc) = sharp.uvs
    val e1 = b - a
    val e2 = c - a
    val offset = point - a
    val d11 = e1 dot e1
    val d12 = e1 dot e2
    val d22 = e2 dot e2
    val determinant = d11 * d22 - d12 * d12
    val s = ((offset dot e1) * d22 - (offset dot e2) * d12) / determinant
    val t = ((offset dot e2) * d11 - (offset dot e1) * d12) / determinant
    return TextureCoordinate(
      u = ua.u + s * (ub.u - ua.u) + t * (uc.u - ua.u),
      v = ua.v + s * (ub.v - ua.v) + t * (uc.v - ua.v),
    )
  }

  /** Points spread over a triangle, as the weights of its second and third corners. */
  private fun barycentric(): List<Pair<Double, Double>> =
    (0..SAMPLES).flatMap { s -> (0..SAMPLES - s).map { t -> s.toDouble() / SAMPLES to t.toDouble() / SAMPLES } }

  /**
   * [field] at `(u, v)` as a `LINEAR`, `CLAMP_TO_EDGE` sampler reads it:
   * nought for none of the ink, one for the middle of a stroke.
   */
  private fun sample(
    field: NumberField,
    u: Double,
    v: Double,
  ): Double {
    val x = u * field.width - HALF
    val y = v * field.height - HALF
    val left = floor(x).toInt()
    val top = floor(y).toInt()
    val across = x - left
    val down = y - top

    fun at(
      column: Int,
      row: Int,
    ): Double {
      val byte = field.pixels[row.coerceIn(0, field.height - 1) * field.width + column.coerceIn(0, field.width - 1)]
      return (byte.toInt() and BYTE) / BYTE.toDouble()
    }
    return at(left, top) * (1 - across) * (1 - down) + at(left + 1, top) * across * (1 - down) +
      at(left, top + 1) * (1 - across) * down + at(left + 1, top + 1) * across * down
  }

  /** Whether [point], on the plane of [polygon], is inside it. */
  private fun inside(
    polygon: List<Vector3>,
    normal: Vector3,
    point: Vector3,
  ): Boolean =
    polygon.indices.all { side ->
      val from = polygon[side]
      val to = polygon[(side + 1) % polygon.size]
      (cross(to - from, point - from) dot normal) >= -PLANE
    }

  /** How far a point of a cell lies outside a polygon of that cell, wound either way; negative inside. */
  private fun outside(
    polygon: List<Pair<Double, Double>>,
    x: Double,
    y: Double,
  ): Double {
    val sides = polygon.indices.map { polygon[it] to polygon[(it + 1) % polygon.size] }
    val winding = sign(sides.sumOf { (from, to) -> from.first * to.second - to.first * from.second })
    return sides.maxOf { (from, to) ->
      val length = hypot(to.first - from.first, to.second - from.second)
      -winding * ((to.first - from.first) * (y - from.second) - (to.second - from.second) * (x - from.first)) / length
    }
  }

  private fun area(
    polygon: List<Vector3>,
    normal: Vector3,
  ): Double =
    polygon.indices.sumOf { side ->
      cross(polygon[side], polygon[(side + 1) % polygon.size]) dot normal
    } / 2

  /** Every triangle edge of [surface], each written the same way round both times. */
  private fun edgesOf(surface: Surface): List<Pair<String, String>> =
    surface.triangles.chunked(TRIANGLE).flatMap { triangle ->
      triangle.indices.map { step ->
        val from = key(surface.positions[triangle[step]])
        val to = key(surface.positions[triangle[(step + 1) % TRIANGLE]])
        if (from <= to) from to to else to to from
      }
    }

  private fun key(point: Vector3): String =
    "${Math.round(point.x * ROUNDING)},${Math.round(point.y * ROUNDING)},${Math.round(point.z * ROUNDING)}"

  private companion object {
    const val TRIANGLE = 3
    const val RADIUS = 0.01
    const val HALF = 0.5

    /** Smaller than the capacity rule ever shrinks a die, to reach Jolt's thickness limit. */
    const val FIFTH = 0.2
    const val PLANE = 1e-12
    const val AREA = 1e-12
    const val DIRECTIONS = 20_000
    val GOLDEN_ANGLE = PI * (3 - sqrt(5.0))

    /** How close a sampled worst gap comes to the analytic one. */
    const val GAP_SAMPLING = 1e-6

    /** The figures in the docs are quoted to a thousandth of a millimetre. */
    const val MILLIMETRE = 5e-4

    const val ROUNDING = 1e9

    /** How far, in cells, a d4's number reaches past the flat onto the bend: 0.0125, measured. */
    const val SPILL = 0.013

    /** Steps along each side of a bend's triangle the printed field is sampled at. */
    const val SAMPLES = 16
    const val BYTE = 0xFF

    /** A corner whose third face sits nearly on the line between the other two. */
    const val STEEP = 5.0
    const val NUDGE = 0.1
  }
}
