package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rounded die at every rounding a set may ask for, not only the default
 * (`edge_rounding`; `docs/architecture.md`, decision 94).
 *
 * `RoundedEdgesTest` holds the construction to the solver's at 3 %. Here it is
 * held to the same promises at the two ends of the range and in the middle —
 * the mesh is closed, every flat face is on its own plane and inside the
 * sharp one, a die on a face is drawn on the felt, it never pokes out of its
 * hull, its corners stand in by what the solver's do — and to the radius
 * Jolt gives a rounder die, shape by shape.
 */
class RoundedEdgesRoundnessTest {
  @Test
  fun `a rounded die is closed at every rounding a set may ask for`() {
    each { shape, mesh ->
      val open =
        mesh.surfaces
          .flatMap(::edgesOf)
          .groupingBy { it }
          .eachCount()
          .filterValues { it != 2 }
      assertTrue("${shape.id} has ${open.size} edges not shared by two triangles", open.isEmpty())
    }
  }

  @Test
  fun `every flat face stays on its own plane and inside the sharp face, at every rounding`() {
    each { shape, mesh ->
      val flats = mesh.surfaces.filterIsInstance<MeshFace>()
      assertEquals("${shape.id} draws one flat per face", mesh.faces.size, flats.size)
      mesh.faces.zip(flats).forEach { (sharp, flat) ->
        val height = sharp.positions.first() dot sharp.normal
        flat.positions.forEach { corner ->
          assertEquals("${shape.id} face ${sharp.index} left its plane", height, corner dot sharp.normal, PLANE)
          assertTrue("${shape.id} face ${sharp.index} spilled", inside(sharp.positions, sharp.normal, corner))
        }
        val area = area(flat.positions, flat.normal)
        assertTrue("${shape.id} face ${sharp.index} was rounded away to $area", area > AREA)
        assertTrue("${shape.id} face ${sharp.index} grew", area < area(sharp.positions, sharp.normal))
      }
    }
  }

  @Test
  fun `every triangle faces outwards, at every rounding`() {
    each { shape, mesh ->
      mesh.surfaces.forEach { surface ->
        surface.triangles.chunked(TRIANGLE).forEach { (a, b, c) ->
          val p = surface.positions
          val wound = cross(p[b] - p[a], p[c] - p[a])
          assertTrue("${shape.id} has a triangle with no area", wound.length > AREA)
          assertTrue("${shape.id} has a triangle wound inwards", (wound dot (p[a] + p[b] + p[c])) > 0)
        }
      }
    }
  }

  @Test
  fun `a die on a face is on the felt and inside its hull, with its corners where the solver's are`() {
    each { shape, mesh ->
      val hull = ShapeGeometry.verticesOf(shape)
      val drawn = mesh.surfaces.flatMap(Surface::positions)
      mesh.faces.forEach { face ->
        assertEquals("${shape.id} on a face is not on the felt", 0.0, gap(hull, drawn, face.normal), PLANE)
      }
      val directions = sphere() + hull.map(Vector3::normalised)
      assertTrue("${shape.id} pokes out of its hull", directions.minOf { gap(hull, drawn, it) } >= -PLANE)
    }
    ROUNDINGS.forEach { rounding ->
      DieShape.entries.forEach { shape ->
        val facets = facetsOf(shape)
        val radius = RoundedEdges.radiusFor(facets, materialOf(rounding), 1.0)
        val drawn = DieMesh.of(dieOf(shape, rounding), 1.0).surfaces.flatMap(Surface::positions)
        val hull = ShapeGeometry.verticesOf(shape)
        val worst = (sphere() + hull.map(Vector3::normalised)).maxOf { gap(hull, drawn, it) }
        assertEquals("${shape.id} at $rounding", RoundedEdges.gapOf(facets, radius), worst, GAP_SAMPLING)
      }
    }
  }

  @Test
  fun `at the roundest a 16 mm die is rounded by what Jolt gives it`() {
    // 0.96 mm asked for. A d4 is cut to half of it, as it is at every rounding
    // (its spike stands `2 r` inside the sharp one and the error Jolt allows
    // grows with the share). Every other solid gets what it asked for — the
    // coin too, which is thick enough to take twice that across.
    val boundingRadiusMm = 8.0
    val expected =
      mapOf(
        DieShape.Coin to 0.96,
        DieShape.Tetrahedron to 0.5,
        DieShape.Cube to 0.96,
        DieShape.Octahedron to 0.96,
        DieShape.PentagonalTrapezohedron to 0.96,
        DieShape.Dodecahedron to 0.96,
        DieShape.EnneagonalTrapezohedron to 0.96,
        DieShape.Icosahedron to 0.96,
      )
    val found =
      DieShape.entries.associateWith { shape ->
        RoundedEdges.radiusFor(facetsOf(shape), materialOf(ROUNDEST), 1.0) * boundingRadiusMm
      }
    expected.forEach { (shape, mm) -> assertEquals("${shape.id}: $found", mm, found.getValue(shape), MILLIMETRE) }
  }

  @Test
  fun `every shape is rounded in proportion to the share it asks for`() {
    // The error Jolt allows grows with the share, so a d4 at twice the share
    // is rounded by twice as much — the default's proportions, kept.
    DieShape.entries.filter { it != DieShape.Coin }.forEach { shape ->
      val facets = facetsOf(shape)
      val standard = RoundedEdges.radiusFor(facets, DieMaterial(), 1.0)
      val twice = RoundedEdges.radiusFor(facets, materialOf(DieMaterial.DEFAULT_EDGE_ROUNDING * 2), 1.0)
      assertEquals(shape.id, 2 * standard, twice, PLANE)
    }
  }

  @Test
  fun `a die that does not say is rounded exactly as it was`() {
    // Bit for bit: the default path through every limit gives the radius the
    // renderer drew before a set could ask for another.
    DieShape.entries.forEach { shape ->
      val facets = facetsOf(shape)
      val radius = RoundedEdges.radiusFor(facets, DieMaterial(), 1.0)
      assertEquals(radius, RoundedEdges.radiusFor(facets, materialOf(DieMaterial.DEFAULT_EDGE_ROUNDING), 1.0), 0.0)
    }
  }

  @Test
  fun `a rounder die is a different mesh, worked out once`() {
    val standard = DieMesh.of(dieOf(DieShape.Cube, DieMaterial.DEFAULT_EDGE_ROUNDING), 1.0)
    val round = DieMesh.of(dieOf(DieShape.Cube, ROUNDEST), 1.0)
    assertNotSame(standard, round)
    assertTrue(round === DieMesh.of(dieOf(DieShape.Cube, ROUNDEST), 1.0))
  }

  private fun each(check: (DieShape, DieMesh) -> Unit) {
    ROUNDINGS.forEach { rounding ->
      DieShape.entries.forEach { shape -> check(shape, DieMesh.of(dieOf(shape, rounding), 1.0)) }
    }
  }

  private fun materialOf(rounding: Double): DieMaterial = DieMaterial(edgeRounding = rounding)

  private fun dieOf(
    shape: DieShape,
    rounding: Double,
  ): Die = Die.standard("d${shape.faceCount}", shape, materialOf(rounding))

  private fun facetsOf(shape: DieShape): List<RoundedEdges.Facet> =
    DieMesh.of(shape, 0.0).faces.map { RoundedEdges.Facet(it, null) }

  private fun gap(
    hull: List<Vector3>,
    drawn: List<Vector3>,
    direction: Vector3,
  ): Double = hull.maxOf { it dot direction } - drawn.maxOf { it dot direction }

  private fun sphere(): List<Vector3> =
    (0 until DIRECTIONS).map { step ->
      val z = 1 - 2 * (step + HALF) / DIRECTIONS
      val ring = sqrt(1 - z * z)
      val turn = step * GOLDEN_ANGLE
      Vector3(ring * cos(turn), ring * sin(turn), z)
    }

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

  private fun area(
    polygon: List<Vector3>,
    normal: Vector3,
  ): Double =
    polygon.indices.sumOf { side ->
      cross(polygon[side], polygon[(side + 1) % polygon.size]) dot normal
    } / 2

  private fun edgesOf(surface: Surface): List<Pair<String, String>> =
    surface.triangles.chunked(TRIANGLE).flatMap { triangle ->
      triangle.indices.map { step ->
        val from = key(surface.positions[triangle[step]])
        val to = key(surface.positions[triangle[(step + 1) % TRIANGLE]])
        if (from <= to) from to to else to to from
      }
    }

  private fun key(point: Vector3): String =
    "${Math.round(point.x * KEY)},${Math.round(point.y * KEY)},${Math.round(point.z * KEY)}"

  private companion object {
    /** The least a set may ask for, the default, and the most. */
    val ROUNDINGS = listOf(0.015, 0.03, 0.06)
    const val ROUNDEST = 0.06
    const val TRIANGLE = 3
    const val HALF = 0.5
    const val PLANE = 1e-12
    const val AREA = 1e-12
    const val DIRECTIONS = 20_000
    val GOLDEN_ANGLE = PI * (3 - sqrt(5.0))
    const val GAP_SAMPLING = 1e-6
    const val MILLIMETRE = 5e-4
    const val KEY = 1e9
  }
}
