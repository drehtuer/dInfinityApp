package de.drehtuer.dinfinity.feature.settings

import androidx.annotation.StringRes
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding

/*
 * The words for the choices in `core/model`.
 *
 * Here rather than on the enums, because `core/model` is pure Kotlin with no
 * Android in it and a string resource is an Android thing. The enum knows what
 * it *is*; this knows what to call it (`docs/TODO.md`, Step 6, translations).
 */

/** What to call this appearance on screen. */
@StringRes
internal fun Appearance.labelRes(): Int =
  when (this) {
    Appearance.System -> R.string.appearance_system
    Appearance.Light -> R.string.appearance_light
    Appearance.Dark -> R.string.appearance_dark
  }

/** What to call this rounding on screen. */
@StringRes
internal fun Rounding.labelRes(): Int =
  when (this) {
    Rounding.Down -> R.string.rounding_down
    Rounding.Nearest -> R.string.rounding_nearest
    Rounding.Up -> R.string.rounding_up
  }
