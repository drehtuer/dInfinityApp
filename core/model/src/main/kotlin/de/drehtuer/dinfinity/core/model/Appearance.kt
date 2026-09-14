package de.drehtuer.dinfinity.core.model

/**
 * Light, dark, or whatever the phone is doing
 * (`design/dInfinity.dc.html`, option 1q; `README.md`).
 *
 * Three choices and no fourth. "Automatic at sunset" is a fourth, and a dice
 * app that changed colour halfway through an evening's game would be doing
 * something nobody asked it to.
 */
enum class Appearance(
  /** The value stored in the preferences, stable across releases. */
  val id: String,
) {
  /** Follow the phone. The default, because most people have already chosen. */
  System("system"),
  Light("light"),
  Dark("dark"),
  ;

  /**
   * Whether to draw dark, given what the phone is currently doing.
   *
   * Taking the system's answer as an argument rather than reading it keeps
   * this out of Android entirely, which is what lets it be tested.
   */
  fun isDark(systemIsDark: Boolean): Boolean =
    when (this) {
      System -> systemIsDark
      Light -> false
      Dark -> true
    }

  companion object {
    /** The choice stored under [id], or [System] for anything unrecognised. */
    fun of(id: String?): Appearance = entries.firstOrNull { it.id == id } ?: System
  }
}
