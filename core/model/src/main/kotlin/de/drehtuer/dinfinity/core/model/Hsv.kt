package de.drehtuer.dinfinity.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A colour as somebody picks it: hue round the wheel, and how deep and how
 * bright it is.
 *
 * Three numbers a finger can move independently, which is what a picker needs
 * and what red-green-blue is not: dragging one of three channels changes the
 * hue, the depth and the brightness at once, and nobody thinks in those.
 *
 * It is in `core/model` beside [AccentRamp] and [Contrast] rather than in the
 * module that first needed it, and the reason is that **three screens pick a
 * colour now** — the face designer's ink, Settings' accent and a saved roll's
 * tag — and the one control all three draw lives in `ui/common`, which may not
 * depend on a feature's engine (`docs/architecture.md`, Modules). This is the
 * arithmetic under that control, and it is plain Kotlin so a JVM test can hold
 * it rather than a Robolectric one.
 *
 * **A colour here is always opaque.** Alpha is forced rather than offered: a
 * half-transparent stroke on a die face is a stroke whose colour depends on
 * what is behind it, and what is behind it is the die's own material, which
 * the set decides and the designer never sees (`docs/face-designer.md`,
 * "Export details"). A picker that could quietly produce ink nobody can see is
 * a picker that will.
 *
 * **There is no contrast rule here, and that is deliberate.** [AccentRamp]
 * pushes an *interface* colour to 3:1 against both grounds because those
 * grounds are the app's own: it knows what the surface behind a button, or
 * behind a saved roll's mark, is. A die face is not the app's ground — the
 * cell is transparent in the atlas and the colour under it comes from the dice
 * set — so a ratio computed against the designer's white paper would be a
 * promise about a surface that is not there. Whoever paints with a colour
 * decides whether it goes through the clamp first; the picker offers it as it
 * was chosen, because a control that answers a drag with a different colour is
 * a control nobody can aim.
 *
 * @param hue degrees, `0..360`. 360 is red again, which is why it wraps.
 * @param saturation `0..1`, from grey to as deep as the screen goes.
 * @param value `0..1`, from black to as bright as the screen goes.
 */
data class Hsv(
  val hue: Float,
  val saturation: Float,
  val value: Float,
) {
  /**
   * This colour as opaque `0xAARRGGBB`.
   *
   * Out-of-range components are pulled into range rather than refused: this is
   * fed by sliders and by a colour read back from a draft or from storage, and
   * neither is a place to throw.
   */
  val argb: Int
    get() {
      val h = wrapped(hue)
      val s = saturation.coerceIn(0f, 1f)
      val v = value.coerceIn(0f, 1f)
      return OPAQUE or
        (byteOf(channel(RED_TURN, h, s, v)) shl RED_SHIFT) or
        (byteOf(channel(GREEN_TURN, h, s, v)) shl GREEN_SHIFT) or
        byteOf(channel(BLUE_TURN, h, s, v))
    }

  companion object {
    /**
     * [colorArgb] back as the three numbers a picker moves.
     *
     * What opens the picker on the colour somebody is already drawing with,
     * rather than on a colour nobody chose. Grey has no hue to speak of and
     * gets zero, which is the convention every other colour picker uses.
     */
    fun of(colorArgb: Int): Hsv {
      val red = ((colorArgb shr RED_SHIFT) and BYTE) / FULL
      val green = ((colorArgb shr GREEN_SHIFT) and BYTE) / FULL
      val blue = (colorArgb and BYTE) / FULL
      val high = max(red, max(green, blue))
      val low = min(red, min(green, blue))
      val chroma = high - low
      val hue =
        when {
          chroma == 0f -> 0f
          high == red -> SECTOR * (((green - blue) / chroma) % SIX)
          high == green -> SECTOR * ((blue - red) / chroma + GREEN_TURN_BACK)
          else -> SECTOR * ((red - green) / chroma + BLUE_TURN_BACK)
        }
      return Hsv(
        hue = wrapped(hue),
        saturation = if (high == 0f) 0f else chroma / high,
        value = high,
      )
    }

    /** Alpha `FF`: a picked colour is opaque, always. */
    const val OPAQUE: Int = -0x1000000

    /** Degrees round the wheel: the hue slider's range, not a colour of its own. */
    const val ROUND: Float = 360f

    /**
     * One channel of the wheel, [turn] sixths round from red.
     *
     * The standard hue formula rather than a `when` over six sectors: the same
     * arithmetic for all three channels, and no branch to get the boundary
     * between two of them wrong.
     */
    private fun channel(
      turn: Float,
      hue: Float,
      saturation: Float,
      value: Float,
    ): Float {
      val k = (turn + hue / SECTOR) % SIX
      return value - value * saturation * max(0f, min(min(k, FALL - k), 1f))
    }

    /** [hue] brought onto the wheel, whichever way round it left it. */
    private fun wrapped(hue: Float): Float = ((hue % ROUND) + ROUND) % ROUND

    private fun byteOf(channel: Float): Int = (channel.coerceIn(0f, 1f) * FULL).roundToInt()

    private const val SECTOR = 60f
    private const val SIX = 6f
    private const val FULL = 255f
    private const val BYTE = 0xFF
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    /** How far round the wheel each channel's ramp starts, in sixths. */
    private const val RED_TURN = 5f
    private const val GREEN_TURN = 3f
    private const val BLUE_TURN = 1f

    /** Where a channel's ramp turns back down, in sixths. */
    private const val FALL = 4f

    /** Where green and blue sit on the wheel when a colour is read back, in sixths. */
    private const val GREEN_TURN_BACK = 2f
    private const val BLUE_TURN_BACK = 4f
  }
}

/**
 * A colour written down, which is `#RRGGBB` and nothing else.
 *
 * One answer rather than the three this app grew: the accent's label, the face
 * designer's ink readout and a saved roll's colour tag each wrote the same six
 * digits their own way, and two of them reached `String.format` with the
 * default locale in tow. Built rather than formatted, because a hex colour is
 * not a number anybody's locale has an opinion about.
 *
 * Without the alpha, because a picked colour is always opaque ([Hsv]) and `FF`
 * in front of every colour is two characters of nothing.
 */
object Hex {
  /** [colorArgb] as `#RRGGBB`, upper case. */
  fun of(colorArgb: Int): String =
    "#" +
      (colorArgb and RGB)
        .toString(BASE)
        .padStart(DIGITS, '0')
        .uppercase()

  private const val RGB = 0xFFFFFF
  private const val BASE = 16
  private const val DIGITS = 6
}
