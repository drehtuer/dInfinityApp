package de.drehtuer.dinfinity.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much solid is in a die (`docs/dice-sets.md`, "Weight, translucency and
 * size, as a person sets them").
 *
 * Every constant is checked against something that was **not** used to write
 * it: an edge-length formula out of a textbook, a tetrahedron sum over the
 * real corners, or a bound the solid cannot be outside. A volume is the sort
 * of number that is wrong by a factor of two without looking wrong at all, and
 * a test that re-derived it the way the code does would agree with the mistake.
 */
class DieVolumeTest {
  @Test
  fun `a cube of a given width is that width cubed, over root three`() {
    // The independent identity: the cube inside a sphere of radius R has edge
    // 2R/√3, and a cube's volume is its edge cubed.
    val edge = 2 / kotlin.math.sqrt(3.0)

    assertEquals(edge.pow(3), DieVolume.perCircumradiusCubed(DieShape.Cube), TIGHT)
  }

  @Test
  fun `a tetrahedron is the smallest solid in the catalogue and an octahedron is rational`() {
    val tetrahedron = DieVolume.perCircumradiusCubed(DieShape.Tetrahedron)
    val octahedron = DieVolume.perCircumradiusCubed(DieShape.Octahedron)

    // a³/(6√2) with a = R√(8/3), and 4/3 exactly.
    assertEquals((8.0 / 3).pow(1.5) / (6 * kotlin.math.sqrt(2.0)), tetrahedron, TIGHT)
    assertEquals(4.0 / 3, octahedron, TIGHT)
    assertTrue(tetrahedron < octahedron, "a d4 is the thinnest solid inside its sphere")
  }

  @Test
  fun `the two Platonic golden solids match their edge-length formulas`() {
    // Written the other way round from the code: work the edge out from the
    // circumradius, then use the textbook volume-per-edge.
    val phi = (1 + kotlin.math.sqrt(5.0)) / 2
    val dodecahedronEdge = 2 / (kotlin.math.sqrt(3.0) * phi)
    val icosahedronEdge = 4 / kotlin.math.sqrt(10 + 2 * kotlin.math.sqrt(5.0))

    assertEquals(
      (15 + 7 * kotlin.math.sqrt(5.0)) / 4 * dodecahedronEdge.pow(3),
      DieVolume.perCircumradiusCubed(DieShape.Dodecahedron),
      TIGHT,
    )
    assertEquals(
      5.0 / 12 * (3 + kotlin.math.sqrt(5.0)) * icosahedronEdge.pow(3),
      DieVolume.perCircumradiusCubed(DieShape.Icosahedron),
      TIGHT,
    )
  }

  @Test
  fun `a trapezohedron agrees with a sum of tetrahedra over its own corners`() {
    // The one pair with no textbook constant. This adds up the solid the
    // solver actually collides — the kites cut into triangles, each a
    // tetrahedron with the centre — which shares nothing with the prismatoid
    // arithmetic the code uses.
    listOf(DieShape.PentagonalTrapezohedron to 5, DieShape.EnneagonalTrapezohedron to 9).forEach { (shape, n) ->
      assertEquals(byTetrahedra(n), DieVolume.perCircumradiusCubed(shape), TIGHT, "${shape.id}")
    }
  }

  @Test
  fun `a coin is the prism its rim is collided as, not the cylinder it is drawn as`() {
    val thickness = CoinShape.THICKNESS_RATIO
    val cylinder =
      PI * (1 / (1 + thickness * thickness)) * (2 * thickness / kotlin.math.sqrt(1 + thickness * thickness))

    val coin = DieVolume.perCircumradiusCubed(DieShape.Coin)

    // A 24-gon is inside its circle, so the hull is a shade under the drawing.
    assertTrue(coin < cylinder, "a prism on a 24-gon holds less than the cylinder round it")
    assertEquals(cylinder, coin, cylinder * ONE_PER_CENT)
  }

  @Test
  fun `no solid is bigger than the sphere its corners are on`() {
    val sphere = 4.0 / 3 * PI

    DieShape.entries.forEach { shape ->
      val volume = DieVolume.perCircumradiusCubed(shape)
      assertTrue(volume > 0, "${shape.id} has no volume at all")
      assertTrue(volume < sphere, "${shape.id} is bigger than its own bounding sphere")
    }
  }

  @Test
  fun `more faces is more solid, for the same width`() {
    // Not a law of geometry in general, but it is true of this catalogue and
    // it is the thing somebody would notice was wrong: a d20 that weighed less
    // than a d4 would be visible on the screen before it was visible here.
    val ordered =
      listOf(
        DieShape.Tetrahedron,
        DieShape.Octahedron,
        DieShape.Cube,
        DieShape.PentagonalTrapezohedron,
        DieShape.EnneagonalTrapezohedron,
        DieShape.Icosahedron,
        DieShape.Dodecahedron,
      ).map(DieVolume::perCircumradiusCubed)

    assertEquals(ordered.sorted(), ordered)
  }

  @Test
  fun `volume goes as the cube of the size`() {
    val small = DieVolume.mm3(DieShape.Icosahedron, sizeMm = 10.0)
    val large = DieVolume.mm3(DieShape.Icosahedron, sizeMm = 20.0)

    assertEquals(8.0, large / small, TIGHT)
    // And the unit conversion is the one a density is quoted in.
    assertEquals(small / 1_000, DieVolume.cm3(DieShape.Icosahedron, sizeMm = 10.0), TIGHT)
  }

  @Test
  fun `a 16 mm die is a handful of cubic centimetres, not a bucket`() {
    // The sanity check with a ruler in it: a 16 mm cube across the corners has
    // 9.2 mm edges, and 0.92³ cm³ is what the arithmetic must say.
    val edgeCm = 1.6 / kotlin.math.sqrt(3.0)

    assertEquals(edgeCm.pow(3), DieVolume.cm3(DieShape.Cube, sizeMm = 16.0), TIGHT)
  }

  /**
   * The volume of an n-gonal trapezohedron of circumradius 1, summed over the
   * tetrahedra its faces make with its centre.
   *
   * The same construction `simulation/api`'s `Solids` builds the hull from,
   * written out here so the check does not go through the code it is checking.
   */
  private fun byTetrahedra(n: Int): Double {
    val apexRatio = 2 / (1 - cos(PI / n)) - 1
    val ring = 1 / kotlin.math.sqrt(apexRatio * apexRatio - 1)
    val apex = apexRatio * ring
    val top = Triple(0.0, 0.0, apex)
    val bottom = Triple(0.0, 0.0, -apex)
    val upper = (0 until n).map { corner(it.toDouble() / n, ring) }
    val lower = (0 until n).map { corner((it + 0.5) / n, -ring) }
    val total =
      (0 until n).sumOf { k ->
        val next = (k + 1) % n
        tetrahedron(top, upper[k], lower[k]) +
          tetrahedron(top, lower[k], upper[next]) +
          tetrahedron(bottom, lower[k], upper[next]) +
          tetrahedron(bottom, upper[next], lower[next])
      }
    return total / apex.pow(3)
  }

  private fun corner(
    turns: Double,
    height: Double,
  ) = Triple(cos(2 * PI * turns), sin(2 * PI * turns), height)

  /** The volume of the tetrahedron on the origin and these three corners. */
  private fun tetrahedron(
    a: Triple<Double, Double, Double>,
    b: Triple<Double, Double, Double>,
    c: Triple<Double, Double, Double>,
  ): Double =
    kotlin.math.abs(
      a.first * (b.second * c.third - b.third * c.second) -
        a.second * (b.first * c.third - b.third * c.first) +
        a.third * (b.first * c.second - b.second * c.first),
    ) / 6

  private companion object {
    const val TIGHT = 1e-12
    const val ONE_PER_CENT = 0.02
  }
}
