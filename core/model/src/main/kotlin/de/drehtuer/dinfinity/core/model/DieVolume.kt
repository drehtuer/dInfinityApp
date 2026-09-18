package de.drehtuer.dinfinity.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How much solid a die of a given size is made of
 * (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
 * them").
 *
 * A set file says how *dense* a die is, and nobody holds a die and estimates
 * its grams per cubic centimetre. Turning that into the gram figure a dice
 * shop quotes needs the volume, and the volume is the interesting half: the
 * solver computes one for a body's mass, out of the hull it collides, and
 * nothing on the JVM has ever been able to ask it for one.
 *
 * It does not have to. Every solid in the catalogue is a *known* one, so its
 * volume is arithmetic rather than a measurement. Each is written here as a
 * multiple of the cube of the die's **circumradius**, because that is what
 * `size_mm` is — half the diameter of the sphere the corners sit on
 * (`docs/dice-sets.md`, "Size") — and because the same radius is what
 * `ShapeGeometry.hullOf` scales the collided hull by. The two therefore agree
 * by construction: the grams on the screen are the mass of the body that is
 * thrown, not an estimate of it.
 *
 * Every constant below is derived from the solid's own geometry in its KDoc.
 * None of them is a number somebody measured off a model, and a test holds
 * each one against an independent identity ([DieVolumeTest]).
 */
object DieVolume {
  /**
   * The plain numbers the closed forms below are written out of.
   *
   * Named rather than written as digits because every one of them is a term in
   * a formula the KDoc states, and because a constant initialised from another
   * has to be declared before it.
   */
  private const val PENTAGONAL = 5
  private const val ENNEAGONAL = 9
  private const val MM_PER_RADIUS = 2.0
  private const val MM3_PER_CM3 = 1_000.0
  private const val CUBED = 3.0
  private const val THREE_HALVES = 1.5
  private const val ONE = 1.0
  private const val TWO = 2.0
  private const val THREE = 3.0
  private const val FOUR = 4.0
  private const val FIVE = 5.0
  private const val SEVEN = 7.0
  private const val EIGHT = 8.0
  private const val NINE = 9.0
  private const val TEN = 10.0
  private const val TWELVE = 12.0
  private const val FIFTEEN = 15.0
  private const val SIXTY_FOUR = 64.0

  /**
   * The volume of [shape] for a die whose circumradius is 1.
   *
   * Multiply by the cube of the real circumradius and the answer is in
   * whatever unit that radius was in ([mm3], [cm3]).
   */
  fun perCircumradiusCubed(shape: DieShape): Double =
    when (shape) {
      DieShape.Coin -> COIN
      DieShape.Tetrahedron -> TETRAHEDRON
      DieShape.Cube -> CUBE
      DieShape.Octahedron -> OCTAHEDRON
      DieShape.PentagonalTrapezohedron -> PENTAGONAL_TRAPEZOHEDRON
      DieShape.Dodecahedron -> DODECAHEDRON
      DieShape.EnneagonalTrapezohedron -> ENNEAGONAL_TRAPEZOHEDRON
      DieShape.Icosahedron -> ICOSAHEDRON
    }

  /** The volume in cubic millimetres of a [shape] that is [sizeMm] across. */
  fun mm3(
    shape: DieShape,
    sizeMm: Double,
  ): Double = perCircumradiusCubed(shape) * (sizeMm / MM_PER_RADIUS).pow(CUBED)

  /**
   * The same volume in cubic centimetres, which is the unit `density` is
   * quoted in and therefore the one a weight is worked out in.
   */
  fun cm3(
    shape: DieShape,
    sizeMm: Double,
  ): Double = mm3(shape, sizeMm) / MM3_PER_CM3

  /** The golden ratio, which is most of the geometry of a d12 and a d20. */
  private val PHI = (ONE + sqrt(FIVE)) / TWO

  /**
   * A cylinder [CoinShape.RIM_SEGMENTS] sides round, of the proportions the
   * solver collides.
   *
   * Not `π r² h`: the hull is a prism on a regular polygon, because a cylinder
   * is not a polyhedron and the rim has to be collided as *something*
   * (`ShapeGeometry`). Taking the polygon's area rather than the circle's is
   * the difference between the volume of the die that is thrown and the volume
   * of the die it is drawn as, and it is about a per cent.
   *
   * Its corners are the rim, so for a disc `t` times as thick as it is wide
   * the circumradius is `√(1 + t²) / 2` of that width. Scaled to a
   * circumradius of 1 the disc is `2/√(1 + t²)` across and `2t/√(1 + t²)`
   * thick.
   */
  private val COIN =
    (CoinShape.RIM_SEGMENTS / TWO) * sin(TWO * PI / CoinShape.RIM_SEGMENTS) *
      (ONE / (ONE + CoinShape.THICKNESS_RATIO * CoinShape.THICKNESS_RATIO)) *
      (TWO * CoinShape.THICKNESS_RATIO / sqrt(ONE + CoinShape.THICKNESS_RATIO * CoinShape.THICKNESS_RATIO))

  /**
   * `8 / (9√3)`.
   *
   * A regular tetrahedron of edge `a` has volume `a³ / (6√2)` and circumradius
   * `a √(3/8)`, so `a = R √(8/3)` and the volume is `R³ (8/3)^(3/2) / (6√2)`,
   * which reduces to this. It is the smallest of the catalogue by a long way:
   * a d4 and a d20 of the same `size_mm` share a bounding sphere, and the d4
   * is the thinner solid inside it (`docs/dice-sets.md`, "Size").
   */
  private val TETRAHEDRON = EIGHT / (NINE * sqrt(THREE))

  /**
   * `8 / (3√3)`.
   *
   * A cube of edge `a` has circumradius `a√3 / 2`, so `a = 2R/√3` and its
   * volume `a³` is `8R³ / (3√3)`.
   */
  private val CUBE = EIGHT / (THREE * sqrt(THREE))

  /**
   * `4 / 3`.
   *
   * A regular octahedron of edge `a` has volume `a³√2 / 3` and circumradius
   * `a/√2`, so `a = R√2` and the volume is `4R³/3` — the one constant in the
   * catalogue that is rational.
   */
  private const val OCTAHEDRON = FOUR / THREE

  /**
   * `2(15 + 7√5) / (3√3 φ³)`, where `φ` is the golden ratio.
   *
   * A regular dodecahedron of edge `a` has volume `a³(15 + 7√5)/4` and
   * circumradius `a√3 φ/2`. The fattest solid in the catalogue for its
   * bounding sphere, which is why a d12 is the heaviest die in a set whose
   * dice are all one size.
   */
  private val DODECAHEDRON = TWO * (FIFTEEN + SEVEN * sqrt(FIVE)) / (THREE * sqrt(THREE) * PHI.pow(CUBED))

  /**
   * `(5/12)(3 + √5) · 64 / (10 + 2√5)^(3/2)`.
   *
   * A regular icosahedron of edge `a` has volume `5a³(3 + √5)/12` and
   * circumradius `a√(10 + 2√5)/4`.
   */
  private val ICOSAHEDRON =
    (FIVE / TWELVE) * (THREE + sqrt(FIVE)) * SIXTY_FOUR / (TEN + TWO * sqrt(FIVE)).pow(THREE_HALVES)

  /** The d10. */
  private val PENTAGONAL_TRAPEZOHEDRON = trapezohedron(PENTAGONAL)

  /** The d18. */
  private val ENNEAGONAL_TRAPEZOHEDRON = trapezohedron(ENNEAGONAL)

  /**
   * The volume of an `n`-gonal trapezohedron of circumradius 1.
   *
   * **The two trapezohedra are the shapes with no textbook constant**, and
   * they are still exact rather than measured. The solid is fixed by the two
   * conditions `simulation/api`'s `Solids` solves — the kite faces are planar,
   * and every corner is on one sphere — which put the two rings of corners at
   * `±h` on a unit circle and the apexes at `±a·h`, with
   * `a = 2/(1 − cos(π/n)) − 1` and `h = 1/√(a² − 1)`.
   *
   * From there it is cut into pieces that do have closed forms. The hull of
   * the two rings alone is an antiprism, and an antiprism is a prismatoid — so
   * the prismatoid rule `(height/6)(bottom + 4·middle + top)` gives its volume
   * *exactly*, not approximately, because a prismatoid's cross-section is
   * quadratic in the height. Each ring is a regular `n`-gon of radius 1; the
   * cross-section halfway between them is the regular `2n`-gon through the
   * midpoints of the slanted edges, whose radius is `cos(π/2n)`. Adding an
   * apex over each ring adds one pyramid each, and a pyramid's volume is a
   * third of its base times its height.
   *
   * The same numbers a different way — summing tetrahedra over the triangulated
   * kites — comes out at the same value to fifteen digits, which is what
   * [DieVolumeTest] checks.
   */
  private fun trapezohedron(n: Int): Double {
    val apexRatio = TWO / (ONE - cos(PI / n)) - ONE
    val ring = ONE / sqrt(apexRatio * apexRatio - ONE)
    val apex = apexRatio * ring
    val ends = n / TWO * sin(TWO * PI / n)
    val waist = n * cos(PI / (TWO * n)).pow(TWO) * sin(PI / n)
    val antiprism = ring / THREE * (TWO * ends + FOUR * waist)
    val caps = TWO / THREE * ends * (apex - ring)
    return (antiprism + caps) / apex.pow(CUBED)
  }
}
