package de.drehtuer.dinfinity.feature.settings

import androidx.annotation.StringRes
import de.drehtuer.dinfinity.core.model.AccentColor

/**
 * The name a swatch is announced with.
 *
 * An exhaustive `when` rather than a field on [AccentColor]: the model is a
 * pure Kotlin module with no resources, and a name that ships in the model
 * could never be translated. Adding an accent without a name fails to compile.
 *
 * The names are the design's own, and two of them do not match the id under
 * them — the id is storage and the name is language, and re-labelling a colour
 * must not move anybody's choice (`AccentColor`).
 */
@StringRes
internal fun AccentColor.labelRes(): Int =
  when (this) {
    AccentColor.LightBlue -> R.string.accent_light_blue
    AccentColor.ModernistRed -> R.string.accent_modernist_red
    AccentColor.Magenta -> R.string.accent_magenta
    AccentColor.Cobalt -> R.string.accent_cobalt
    AccentColor.Pine -> R.string.accent_pine
    AccentColor.Amber -> R.string.accent_amber
  }
