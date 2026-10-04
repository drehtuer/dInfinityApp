package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Vector3
import kotlin.math.sqrt

/**
 * The room a tray is lit by, as numbers (`docs/physics-and-rendering.md`,
 * "Rendering").
 *
 * Two directional lights and nothing else leave every surface facing away from
 * both at exactly black, so there has always been an ambient — but it was *one*
 * spherical-harmonic band, the same irradiance from every direction, which is
 * a room with no ceiling and no floor. A real table is lit from above by
 * something bright and from below by what the light bounced off, and a die's
 * top face is brighter than its sides for that reason rather than because a
 * lamp happens to point at it.
 *
 * So the room here is two colours and a direction: [SKY] overhead, [GROUND]
 * underfoot, blended by how far up a surface looks. It feeds Filament twice —
 * once as [irradiance], which is what a matte surface integrates, and once as a
 * small cubemap ([face]), which is what a glossy one reflects. Without the
 * second, a die has no environment to catch the light in and its polish comes
 * from the two lamps alone, which is what a die under two lamps in a void looks
 * like.
 *
 * **Nothing here touches Filament.** It is arithmetic over directions and
 * colours, which is the half worth testing on a JVM: whether a die looks right
 * needs a phone, but whether the gradient runs the right way up does not.
 */
object RoomLight {
  /** A cubemap has six of them, in Filament's order: +x, −x, +y, −y, +z, −z. */
  const val FACES: Int = 6

  /** How big one face is. A gradient needs no more, and this is 24 KiB in all. */
  const val SIZE: Int = 32

  /**
   * How many levels it carries: every one down to a single pixel, `log2(32)`
   * and one more.
   *
   * A reflection's blur follows the surface's roughness by reading a coarser
   * level of the environment, so a polished die reads the sharp gradient and
   * felt reads its average. Only the sharp level is drawn here ([faces]);
   * Filament works out the rest itself, prefiltered for its own lighting
   * model (`Texture.generatePrefilterMipmap`), which is both more correct
   * than a blur of our own and the only way this Filament uploads them at
   * all: its JNI sizes a level's buffer with the region's height shifted by
   * the level a second time, so a level-one upload of 16 × 16 × 6 is checked
   * against 3,072 bytes instead of 6,144 and refused
   * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
   */
  const val LEVELS: Int = 6

  /** Overhead: the cool bright half of a room with a window in it. */
  val SKY: Colour = Colour(red = 0.78, green = 0.85, blue = 1.0, alpha = 1.0)

  /** Underfoot: what the light came back off, which is warmer and much dimmer. */
  val GROUND: Colour = Colour(red = 0.34, green = 0.30, blue = 0.26, alpha = 1.0)

  /**
   * The irradiance, as Filament's two-band spherical harmonics: four
   * coefficients, three floats each, in the order the shader adds them up.
   *
   * Filament evaluates band one as `sh[1] * n.y + sh[2] * n.z + sh[3] * n.x`,
   * so the coefficient that carries a gradient about **this app's up** — which
   * is `+z`, in the tray, in the physics and in the renderer — is the third.
   * The other two are nought, because a room is not brighter to the north.
   *
   * The two coefficients that are not nought are the half-sum and the
   * half-difference, which is the only pair that makes the evaluation come out
   * at [sky] looking straight up and at [ground] looking straight down. Every
   * direction between them is the linear blend, which is what a two-band
   * harmonic can say and the whole of what this needs to.
   */
  fun irradiance(
    sky: Colour = SKY,
    ground: Colour = GROUND,
  ): FloatArray =
    floatArrayOf(
      // Band 0: the average, which is what the old one-band room was all of.
      ((sky.red + ground.red) / 2).toFloat(),
      ((sky.green + ground.green) / 2).toFloat(),
      ((sky.blue + ground.blue) / 2).toFloat(),
      // Band 1, y: nothing.
      0.0f,
      0.0f,
      0.0f,
      // Band 1, z: up, which is where the difference between the two goes.
      ((sky.red - ground.red) / 2).toFloat(),
      ((sky.green - ground.green) / 2).toFloat(),
      ((sky.blue - ground.blue) / 2).toFloat(),
      // Band 1, x: nothing.
      0.0f,
      0.0f,
      0.0f,
    )

  /** How many bands [irradiance] fills in. Filament takes 1, 2 or 3. */
  const val BANDS: Int = 2

  /**
   * How bright this room is on average, relative to a flat white one.
   *
   * The intensity Filament is given multiplies every coefficient, so a room
   * whose average is 0.6 is a room 40 % darker than the one it replaces at the
   * same setting. Dividing the old intensity by this keeps the tray exactly as
   * bright as it was and changes only *where the light comes from*, which is
   * the whole of what this file is for.
   */
  fun averageBrightness(
    sky: Colour = SKY,
    ground: Colour = GROUND,
  ): Double = (luminance(sky) + luminance(ground)) / 2

  /**
   * The world direction the middle of pixel ([x], [y]) of [face] looks along.
   *
   * The standard cube mapping, with `u` running across a face and `v` down it,
   * which is the way every image in this app counts its rows
   * (`FilamentEngine`'s note about `flipUV`). It is here as its own function
   * because it is the one piece of this file that can be wrong in a way nobody
   * would see: a seam, or a gradient a quarter-turn out.
   */
  fun direction(
    face: Int,
    x: Int,
    y: Int,
    size: Int = SIZE,
  ): Vector3 {
    val u = SPAN * (x + HALF) / size - 1.0
    val v = SPAN * (y + HALF) / size - 1.0
    val raw =
      when (face) {
        POSITIVE_X -> Vector3(1.0, -v, -u)
        NEGATIVE_X -> Vector3(-1.0, -v, u)
        POSITIVE_Y -> Vector3(u, 1.0, v)
        NEGATIVE_Y -> Vector3(u, -1.0, -v)
        POSITIVE_Z -> Vector3(u, -v, 1.0)
        NEGATIVE_Z -> Vector3(-u, -v, -1.0)
        else -> error("a cubemap has six faces, not a face $face")
      }
    val length = sqrt(raw.x * raw.x + raw.y * raw.y + raw.z * raw.z)
    return Vector3(raw.x / length, raw.y / length, raw.z / length)
  }

  /**
   * One face of the environment, as linear RGB floats, the top row first:
   * the sharp sky-to-ground gradient the coarser levels are prefiltered from
   * ([LEVELS]). Floats because that is all Filament's prefilter takes.
   */
  fun face(
    face: Int,
    sky: Colour = SKY,
    ground: Colour = GROUND,
    size: Int = SIZE,
  ): FloatArray {
    val pixels = FloatArray(size * size * CHANNELS)
    var at = 0
    for (y in 0 until size) {
      for (x in 0 until size) {
        val up = direction(face, x, y, size).z
        val colour = blend(ground, sky, (up + 1.0) / SPAN)
        pixels[at++] = colour.red.toFloat()
        pixels[at++] = colour.green.toFloat()
        pixels[at++] = colour.blue.toFloat()
      }
    }
    return pixels
  }

  /** How many bytes one face takes, at four bytes a float. */
  fun faceBytes(size: Int = SIZE): Int = size * size * CHANNELS * Float.SIZE_BYTES

  /**
   * Where each face starts in [faces], in bytes, in Filament's order — what
   * `generatePrefilterMipmap` is handed alongside them.
   */
  fun faceOffsets(size: Int = SIZE): IntArray = IntArray(FACES) { it * faceBytes(size) }

  /** The six faces, in Filament's order, end to end. */
  fun faces(
    sky: Colour = SKY,
    ground: Colour = GROUND,
    size: Int = SIZE,
  ): FloatArray {
    val faces = (0 until FACES).map { face(it, sky, ground, size) }
    val whole = FloatArray(faces.sumOf { it.size })
    var at = 0
    faces.forEach { one ->
      one.copyInto(whole, at)
      at += one.size
    }
    return whole
  }

  private fun blend(
    from: Colour,
    to: Colour,
    amount: Double,
  ): Colour {
    val t = amount.coerceIn(0.0, 1.0)
    return Colour(
      red = from.red + (to.red - from.red) * t,
      green = from.green + (to.green - from.green) * t,
      blue = from.blue + (to.blue - from.blue) * t,
      alpha = 1.0,
    )
  }

  /** Rec. 709, which is what "how bright does this look" means for a linear colour. */
  private fun luminance(colour: Colour): Double =
    RED_WEIGHT * colour.red + GREEN_WEIGHT * colour.green + BLUE_WEIGHT * colour.blue

  private const val POSITIVE_X = 0
  private const val NEGATIVE_X = 1
  private const val POSITIVE_Y = 2
  private const val NEGATIVE_Y = 3
  private const val POSITIVE_Z = 4
  private const val NEGATIVE_Z = 5

  /** A face's coordinates run from −1 to 1, which is two wide. */
  private const val SPAN = 2.0

  /** Red, green and blue: a room is not see-through, so it has no alpha. */
  private const val CHANNELS = 3
  private const val HALF = 0.5
  private const val RED_WEIGHT = 0.2126
  private const val GREEN_WEIGHT = 0.7152
  private const val BLUE_WEIGHT = 0.0722
}
