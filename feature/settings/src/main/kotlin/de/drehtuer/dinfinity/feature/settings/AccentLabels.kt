package de.drehtuer.dinfinity.feature.settings

import androidx.annotation.StringRes
import de.drehtuer.dinfinity.core.model.AccentColor

/**
 * The name shown under a swatch.
 *
 * An exhaustive `when` rather than a field on [AccentColor]: the model is a
 * pure Kotlin module with no resources, and a name that ships in the model
 * could never be translated. Adding an accent without a name fails to compile.
 */
@StringRes
internal fun AccentColor.labelRes(): Int =
  when (this) {
    AccentColor.Vermilion -> R.string.accent_vermilion
    AccentColor.Coral -> R.string.accent_coral
    AccentColor.Sky -> R.string.accent_sky
    AccentColor.Moss -> R.string.accent_moss
    AccentColor.Amber -> R.string.accent_amber
    AccentColor.Violet -> R.string.accent_violet
  }
