package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three figures the **Physical** block shows, and the steppers that move
 * them (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 */
class DiePhysicalTest {
  private val acrylic = DieMaterial()

  @Test
  fun `a 16 mm acrylic d6 weighs what its material and its size say`() {
    // 16 mm **across the corners**, which is what `size_mm` is: a cube with
    // 9.24 mm edges, 0.788 cm³ of acrylic at 1.2 g/cm³.
    val grams = DiePhysical.gramsOf(DieShape.Cube, acrylic)

    assertEquals(0.946, grams, 0.001)
  }

  @Test
  fun `the design's 4 point 2 g is the same cube measured the way a shop measures it`() {
    // Where the design's figure comes from, and why the app does not print it:
    // a dice maker quotes the *edge*, so a "16 mm d6" is 27.7 mm across the
    // corners and holds five times as much acrylic. The app's `size_mm` is the
    // width (`docs/dice-sets.md`, "Size"), and the gram figure on the screen
    // has to be the mass of the die that is thrown.
    val shopSize = 16.0 * kotlin.math.sqrt(3.0)

    val grams = DiePhysical.gramsOf(DieShape.Cube, acrylic.copy(sizeMm = shopSize))

    // 4.9 g for a solid cube; the 4.2 g a shop quotes is that cube with its
    // corners rounded off, which is not a shape in the catalogue.
    assertEquals(4.92, grams, 0.01)
  }

  @Test
  fun `weight is density times volume, so twice as dense is twice as heavy`() {
    val brass = acrylic.copy(density = 2.4)

    assertEquals(
      2 * DiePhysical.gramsOf(DieShape.Icosahedron, acrylic),
      DiePhysical.gramsOf(DieShape.Icosahedron, brass),
      1e-12,
    )
  }

  @Test
  fun `a d4 and a d20 of one size do not weigh the same`() {
    val d4 = DiePhysical.gramsOf(Die.standard("d4", DieShape.Tetrahedron))
    val d20 = DiePhysical.gramsOf(Die.standard("d20", DieShape.Icosahedron))

    assertTrue(d4 < d20, "a d4 is the thinner solid inside the same sphere")
  }

  @Test
  fun `the average die is a hundred per cent`() {
    assertEquals(100.0, DiePhysical.sizePercentOf(DiePhysical.AVERAGE_SIZE_MM), 1e-12)
    assertEquals(DiePhysical.AVERAGE_SIZE_MM, DiePhysical.sizeMmOf(100.0), 1e-12)
    assertEquals(150.0, DiePhysical.sizePercentOf(24.0), 1e-12)
    assertEquals(8.0, DiePhysical.sizeMmOf(50.0), 1e-12)
  }

  @Test
  fun `translucency is a fraction in the model and a per cent on the screen`() {
    assertEquals(18.0, DiePhysical.translucencyPercentOf(0.18), 1e-12)
  }

  @Test
  fun `a tap of the weight stepper is a tenth of a gram on a d6`() {
    val heavier = DiePhysical.weighted(acrylic, steps = 1)

    assertEquals(
      DiePhysical.gramsOf(DieShape.Cube, acrylic) + DiePhysical.WEIGHT_STEP_G,
      DiePhysical.gramsOf(DieShape.Cube, heavier),
      1e-12,
    )
  }

  @Test
  fun `a run of taps accumulates rather than repeating the first one`() {
    val once = DiePhysical.weighted(acrylic, steps = 1)
    val thrice = DiePhysical.weighted(DiePhysical.weighted(once, steps = 1), steps = 1)

    assertEquals(
      DiePhysical.gramsOf(DieShape.Cube, acrylic) + 3 * DiePhysical.WEIGHT_STEP_G,
      DiePhysical.gramsOf(DieShape.Cube, thrice),
      1e-12,
    )
  }

  @Test
  fun `weight stops at the ends of what a die can be made of`() {
    var light = acrylic
    var heavy = acrylic
    repeat(TAPS) {
      light = DiePhysical.weighted(light, steps = -1)
      heavy = DiePhysical.weighted(heavy, steps = 1)
    }

    assertEquals(DieMaterial.DensityRange.start, light.density, 1e-12)
    assertEquals(DieMaterial.DensityRange.endInclusive, heavy.density, 1e-12)
  }

  @Test
  fun `a bigger die takes more density to move by the same tenth of a gram`() {
    // The step is a gram on a d6 *of this set's size*, so a set of 24 mm dice
    // moves less density per tap than a set of 8 mm ones.
    val large = DiePhysical.weighted(acrylic.copy(sizeMm = 24.0), steps = 1)
    val small = DiePhysical.weighted(acrylic.copy(sizeMm = 8.0), steps = 1)

    assertTrue(large.density - 1.2 < small.density - 1.2)
    assertEquals(
      DiePhysical.WEIGHT_STEP_G,
      DiePhysical.gramsOf(DieShape.Cube, large) - DiePhysical.gramsOf(DieShape.Cube, acrylic.copy(sizeMm = 24.0)),
      1e-12,
    )
  }

  @Test
  fun `translucency steps by five per cent and stops at solid and at glass`() {
    assertEquals(0.05, DiePhysical.seenThrough(acrylic, steps = 1).translucency, 1e-12)
    assertEquals(0.0, DiePhysical.seenThrough(acrylic, steps = -1).translucency, 1e-12)

    var glass = acrylic
    repeat(TAPS) { glass = DiePhysical.seenThrough(glass, steps = 1) }
    assertEquals(1.0, glass.translucency, 1e-12)
  }

  @Test
  fun `size steps by five per cent of the average and stops inside the slider's bounds`() {
    assertEquals(16.8, DiePhysical.sized(acrylic, steps = 1).sizeMm, 1e-12)

    var big = acrylic
    var small = acrylic
    repeat(TAPS) {
      big = DiePhysical.sized(big, steps = 1)
      small = DiePhysical.sized(small, steps = -1)
    }

    assertEquals(24.0, big.sizeMm, 1e-12)
    assertEquals(8.0, small.sizeMm, 1e-12)
    // And both ends are sizes the file format already accepts, so nothing the
    // stepper writes is clamped on the way out.
    assertEquals(big, big.clampedToLimits())
    assertEquals(small, small.clampedToLimits())
  }

  @Test
  fun `a set says what is true of every die in it`() {
    val dice =
      listOf(
        Die.standard("d4", DieShape.Tetrahedron),
        Die.standard("d20", DieShape.Icosahedron),
      )

    val physical = DicePhysical.of(dice) ?: error("two dice have a weight")

    assertEquals(DiePhysical.gramsOf(dice[0]), physical.weightG.low, 1e-12)
    assertEquals(DiePhysical.gramsOf(dice[1]), physical.weightG.high, 1e-12)
    // They agree about the other two, because nothing in the set overrode them.
    assertTrue(physical.sizePercent.isOne(1.0))
    assertTrue(physical.translucencyPercent.isOne(1.0))
    assertEquals(100.0, physical.sizePercent.low, 1e-12)
  }

  @Test
  fun `a set with dice of two sizes says both`() {
    val dice =
      listOf(
        Die.standard("d6", DieShape.Cube),
        Die.standard("big-d6", DieShape.Cube, material = acrylic.copy(sizeMm = 24.0)),
      )

    val physical = DicePhysical.of(dice) ?: error("two dice have a size")

    assertEquals(Span(100.0, 150.0), physical.sizePercent)
    assertTrue(!physical.sizePercent.isOne(1.0))
  }

  @Test
  fun `a package with no dice has nothing to weigh`() {
    assertNull(DicePhysical.of(emptyList()))
  }

  @Test
  fun `a span is one figure when both ends print the same`() {
    assertTrue(Span(0.91, 0.94).isOne(0.1), "both print as 0.9 g")
    assertTrue(!Span(0.94, 1.06).isOne(0.1))
    assertEquals(Span(1.0, 3.0), Span.of(listOf(2.0, 1.0, 3.0)))
  }

  private companion object {
    /** More taps than any range here has room for, so each run hits its end. */
    const val TAPS = 200
  }
}
