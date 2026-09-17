package de.drehtuer.dinfinity.feature.saved

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The twelve colours a saved roll's mark can be tagged with
 * (`docs/dice-notation.md`, "Saved rolls"; `design/dInfinityPhone.dc.html`,
 * the saved-roll editor).
 *
 * They span the hue circle rather than sampling the interface's own palette,
 * because a colour tag is doing a different job from the accent: the accent
 * says *this is the thing to press* and there is one of it, where a tag says
 * *this roll is the attack and that one is the damage* and there have to be
 * enough of them to tell a character sheet apart at a glance.
 *
 * Twelve of them and not only a field to type one in ([parseHex] is offered
 * beside them) because twelve named colours are a choice somebody makes in a
 * second, where naming a colour is a choice somebody has to work at.
 *
 * **No entry here is drawn as it is written.** Every one of them, and every
 * custom colour with it, goes through [LegibleColour.legibleOn] against the
 * ground it is printed on: `bone` on paper and `ink` at night are each invisible as
 * written, and a colour tag nobody can see is not a tag.
 *
 * @param argb the colour as the design system spells it, opaque.
 * @param label what a screen reader says, since a swatch is a picture.
 */
enum class RollColour(
  val argb: Int,
  @param:StringRes val label: Int,
) {
  Ink(0xFF201E1D.toInt(), R.string.colour_ink),
  Grey(0xFF7D7979.toInt(), R.string.colour_grey),
  Red(0xFFEC3013.toInt(), R.string.colour_red),
  DeepRed(0xFFAE1800.toInt(), R.string.colour_deep_red),
  Orange(0xFFC05A00.toInt(), R.string.colour_orange),
  Amber(0xFFB8870A.toInt(), R.string.colour_amber),
  Pine(0xFF0F7A50.toInt(), R.string.colour_pine),
  Teal(0xFF0D7F86.toInt(), R.string.colour_teal),
  Cobalt(0xFF1D5FD4.toInt(), R.string.colour_cobalt),
  Violet(0xFF6B2FD0.toInt(), R.string.colour_violet),
  Magenta(0xFFC2186F.toInt(), R.string.colour_magenta),
  Bone(0xFFBAB6B6.toInt(), R.string.colour_bone),
  ;

  companion object {
    /**
     * The twelve as a colour a roll stores, in the order they are offered.
     *
     * A roll stores a packed colour rather than the name of one, so a tag
     * chosen today survives this list being edited tomorrow — and so a custom
     * colour is stored exactly the same way as one of these rather than
     * needing a second field to say which kind it is.
     */
    val argbs: List<Int> = entries.map(RollColour::argb)

    /** The entry [argb] is one of, or null when it is somebody's own colour. */
    fun of(argb: Int?): RollColour? = entries.firstOrNull { it.argb == argb }

    /**
     * `#rrggbb` or `#rgb`, as an opaque colour, or null when it is not one.
     *
     * Typed rather than picked from a wheel: the system colour picker arrives
     * with the accent's own picker (`docs/TODO.md`, Step 4.9), and until then
     * a hex field is the one way of naming a colour that costs nothing and
     * that somebody copying a colour out of a character sheet already has.
     *
     * Anything else is null rather than a guess. A half-typed colour is a
     * thing somebody is in the middle of typing, not an error to shout about.
     */
    fun parseHex(typed: String): Int? {
      val hex = typed.trim().removePrefix("#")
      val full =
        when (hex.length) {
          SHORT -> hex.map { "$it$it" }.joinToString("")
          LONG -> hex
          else -> return null
        }
      if (!full.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
      return OPAQUE or full.toInt(HEX)
    }

    /**
     * [argb] written the way it is typed, so the field shows what was chosen.
     *
     * Built rather than formatted, because `String.format` takes the default
     * locale with it and a hex colour is not a number anybody's locale has an
     * opinion about.
     */
    fun hexOf(argb: Int): String =
      "#" +
        (argb and RGB)
          .toString(HEX)
          .padStart(DIGITS, '0')
          .uppercase()

    private const val SHORT = 3
    private const val LONG = 6
    private const val DIGITS = 6
    private const val HEX = 16
    private const val OPAQUE = 0xFF shl 24
    private const val RGB = 0xFFFFFF
  }
}

/**
 * What a roll's mark is actually drawn in.
 *
 * The chosen tag through the contrast clamp, or the accent for a roll that has
 * no tag of its own. One function rather than three call sites doing it
 * slightly differently: the list, the strip on the tray and the editor's own
 * preview all print the same mark, and a mark that is legible on one of them
 * and not on the others would be the worst of both.
 */
@Composable
@ReadOnlyComposable
internal fun markColour(argb: Int?): Color {
  val scheme = MaterialTheme.colorScheme
  if (argb == null) return scheme.primary
  return Color(
    LegibleColour.legibleOn(
      argb = argb,
      background = scheme.background.toArgb(),
      towards = scheme.onBackground.toArgb(),
    ),
  )
}
