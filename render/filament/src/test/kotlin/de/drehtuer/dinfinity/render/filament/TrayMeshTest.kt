package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import de.drehtuer.dinfinity.simulation.api.cross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The tray against the geometry `docs/tables.md` specifies.
 *
 * The tray is the one mesh no package can replace, so the checks here are the
 * ones that say it is a tray at all: the floor is the right size and has no
 * hole in it, the walls stand on the floor's edge and face inwards, and the
 * corners are round rather than square — which is not decoration but the
 * reason dice do not wedge (`docs/tables.md`, "Geometry").
 */
class TrayMeshTest {
  private val geometry = TableGeometry.referenceDevice()
  private val tray = TrayMesh.of(geometry)

  @Test
  fun `the tray is a floor, a wall all the way round, and a rim on top of it`() {
    TrayPart.entries.forEach { part ->
      assertTrue("the tray has no $part", tray.partsOf(part).isNotEmpty())
    }
    assertEquals("the floor is one surface", 1, tray.partsOf(TrayPart.Floor).size)
    assertEquals(
      "there is a stretch of rim above every stretch of wall",
      tray.partsOf(TrayPart.Wall).size,
      tray.partsOf(TrayPart.Rim).size,
    )
  }

  @Test
  fun `the floor lies flat at the bottom of the tray`() {
    val floor = tray.partsOf(TrayPart.Floor).single()

    assertEquals(Vector3.Up, floor.normal)
    floor.positions.forEach { assertEquals("the floor is not flat", 0.0, it.z, TOLERANCE) }
  }

  @Test
  fun `the floor reaches the walls on all four sides`() {
    val floor = tray.partsOf(TrayPart.Floor).single()

    assertEquals(geometry.longSideMm / 2, floor.positions.maxOf { it.x }, TOLERANCE)
    assertEquals(-geometry.longSideMm / 2, floor.positions.minOf { it.x }, TOLERANCE)
    assertEquals(geometry.shortSideMm / 2, floor.positions.maxOf { it.y }, TOLERANCE)
    assertEquals(-geometry.shortSideMm / 2, floor.positions.minOf { it.y }, TOLERANCE)
  }

  @Test
  fun `the corners are round, which is why dice do not wedge in them`() {
    // A square corner would put floor at the full half-diagonal. A rounded one
    // cuts it back to the arc, and by exactly the documented radius.
    val floor = tray.partsOf(TrayPart.Floor).single()
    val halfLong = geometry.longSideMm / 2
    val halfShort = geometry.shortSideMm / 2
    val centre = Vector3(halfLong - geometry.cornerRadiusMm, halfShort - geometry.cornerRadiusMm, 0.0)

    val inTheCorner = floor.positions.filter { it.x > centre.x && it.y > centre.y }

    assertTrue("no corner was rounded off", inTheCorner.isNotEmpty())
    inTheCorner.forEach {
      assertEquals(
        "the corner is not an arc of the documented radius",
        geometry.cornerRadiusMm,
        hypot(it.x - centre.x, it.y - centre.y),
        TOLERANCE,
      )
    }
  }

  @Test
  fun `the floor has no hole in it, and no sliver outside it`() {
    // The triangles of the fan add up to the area the outline encloses, which
    // is the rectangle less the four corners the rounding took off.
    // The rectangle, less the four square corners the rounding cut off, plus
    // the arcs put back — as the polygon they are actually drawn as, not as
    // the circle they stand for, so this is exact rather than approximate.
    val floor = tray.partsOf(TrayPart.Floor).single()
    val rounded = geometry.cornerRadiusMm
    val segments = CORNERS * TrayMesh.CORNER_SEGMENTS
    val drawnArcs = segments * rounded * rounded * sin(2 * Math.PI / segments) / 2
    val expected = geometry.floorAreaMm2 - CORNERS * rounded * rounded + drawnArcs

    assertEquals("the floor is not the shape the tray is", expected, areaOf(floor), AREA_TOLERANCE)
  }

  @Test
  fun `every wall stands on the floor and reaches the rim, and no further`() {
    tray.partsOf(TrayPart.Wall).forEach { wall ->
      assertEquals("a wall does not start on the floor", 0.0, wall.positions.minOf { it.z }, TOLERANCE)
      assertEquals(
        "a wall is drawn to the ceiling rather than to the rim",
        geometry.wallHeightMm,
        wall.positions.maxOf { it.z },
        TOLERANCE,
      )
    }
  }

  @Test
  fun `every wall faces into the tray`() {
    // Outwards and the player looks at the back of the near wall instead of
    // over it. It is also the difference between a tray and a plinth.
    tray.partsOf(TrayPart.Wall).forEach { wall ->
      val middle = wall.positions.reduce(Vector3::plus) * (1.0 / wall.positions.size)
      assertTrue(
        "a wall at $middle faces ${wall.normal}, which is outwards",
        (wall.normal dot Vector3(middle.x, middle.y, 0.0)) < 0,
      )
      assertEquals("a wall leans", 0.0, wall.normal.z, TOLERANCE)
    }
  }

  @Test
  fun `every surface is wound to match the way it faces`() {
    tray.surfaces.forEach { surface ->
      surface.triangles.chunked(TRIANGLE).forEach { (a, b, c) ->
        val wound = cross(surface.positions[b] - surface.positions[a], surface.positions[c] - surface.positions[a])
        assertTrue("a ${surface.part} triangle has no area", wound.length > TOLERANCE)
        assertTrue(
          "a ${surface.part} triangle is wound away from its own normal",
          (wound.normalised() dot surface.normal) > 0,
        )
      }
    }
  }

  @Test
  fun `the rim sits on top of the wall and looks up`() {
    tray.partsOf(TrayPart.Rim).forEach { band ->
      assertEquals(Vector3.Up, band.normal)
      band.positions.forEach {
        assertEquals("the rim is not level with the top of the wall", geometry.wallHeightMm, it.z, TOLERANCE)
      }
    }
  }

  @Test
  fun `the rim is as wide as it says it is, measured outwards`() {
    val outerReach = tray.partsOf(TrayPart.Rim).flatMap(TraySurface::positions).maxOf { it.x }

    assertEquals(geometry.longSideMm / 2 + TrayMesh.RIM_WIDTH_MM, outerReach, TOLERANCE)
  }

  @Test
  fun `the floor texture covers the floor once when the look asks for once`() {
    val floor = tray.partsOf(TrayPart.Floor).single()

    assertEquals(0.0, floor.uvs.minOf { it.u }, TOLERANCE)
    assertEquals(1.0, floor.uvs.maxOf { it.u }, TOLERANCE)
    assertEquals(0.0, floor.uvs.minOf { it.v }, TOLERANCE)
    assertEquals(1.0, floor.uvs.maxOf { it.v }, TOLERANCE)
  }

  @Test
  fun `a tiled floor repeats as often as the look asks, on the side it names`() {
    val tiled =
      TrayMesh
        .of(
          geometry,
          TableLook(
            id = "felt",
            name = "Felt",
            floorTiling = TableLook.Tiling(acrossShortSide = 3, acrossLongSide = 6),
          ),
        ).partsOf(TrayPart.Floor)
        .single()

    assertEquals(6.0, tiled.uvs.maxOf { it.u }, TOLERANCE)
    assertEquals(3.0, tiled.uvs.maxOf { it.v }, TOLERANCE)
  }

  @Test
  fun `the wall texture walks all the way round without a seam`() {
    // Computed per stretch from its own start, the texture would jump every
    // time the rate changed — eight seams around a tray that nobody could
    // explain. Walked, it only ever stretches.
    val walls = tray.partsOf(TrayPart.Wall)

    walls.zipWithNext { before, after ->
      assertEquals(
        "the wall texture jumps between two stretches that touch",
        before.uvs.maxOf { it.u },
        after.uvs.minOf { it.u },
        TOLERANCE,
      )
    }
    assertEquals("the wall texture does not start at the start", 0.0, walls.first().uvs.minOf { it.u }, TOLERANCE)
    assertTrue("the wall texture never gets anywhere", walls.last().uvs.maxOf { it.u } > 1.0)
  }

  @Test
  fun `the wall texture runs from the floor to the rim, once`() {
    tray.partsOf(TrayPart.Wall).forEach { wall ->
      assertEquals(0.0, wall.uvs.minOf { it.v }, TOLERANCE)
      assertEquals(1.0, wall.uvs.maxOf { it.v }, TOLERANCE)
    }
  }

  @Test
  fun `the rim takes a plain colour rather than a texture`() {
    tray.partsOf(TrayPart.Rim).forEach {
      assertTrue("six millimetres of rim is not where anybody looks", it.uvs.isEmpty())
    }
  }

  @Test
  fun `a narrow tray still has a floor, however narrow`() {
    // The corner radius is 12 mm and the aspect ratio is clamped, but the
    // arithmetic should not fall over if a tray were ever narrower than two
    // corners put together.
    val narrow = TrayMesh.of(TableGeometry(shortSideMm = 20.0))

    assertTrue(areaOf(narrow.partsOf(TrayPart.Floor).single()) > 0.0)
  }

  @Test
  fun `a tray is built the same way every time`() {
    assertEquals(TrayMesh.of(geometry), TrayMesh.of(geometry))
  }

  /** The area a surface's triangles cover. */
  private fun areaOf(surface: TraySurface): Double =
    surface.triangles.chunked(TRIANGLE).sumOf { (a, b, c) ->
      cross(surface.positions[b] - surface.positions[a], surface.positions[c] - surface.positions[a]).length / 2
    }

  private companion object {
    const val TOLERANCE = 1e-9
    const val TRIANGLE = 3
    const val CORNERS = 4

    /** A square millimetre in twenty-five thousand: the sum, not the shape. */
    const val AREA_TOLERANCE = 1e-6
  }
}
