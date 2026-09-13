package de.drehtuer.dinfinity.core.model

/**
 * The look of the dice tray: floor and wall surfaces, how grippy they are, and
 * which of the built-in sound and lighting presets to use (`docs/tables.md`).
 *
 * The tray's *mesh* is not here and never will be — it is the phone's screen,
 * and nobody gets to ship a table with a hole in the floor. Only the look is
 * exchangeable, and it travels inside the same package format as dice
 * (`docs/dice-sets.md`).
 *
 * Power-saving mode ignores everything visual here but still uses [friction]
 * and [restitution], so the same seed gives the same roll with or without a
 * renderer.
 *
 * @param id slug, unique within its package.
 * @param name what the picker shows.
 * @param floorTexturePath relative to the package folder, or `null` for a
 *   plain [floorColorArgb].
 * @param floorTiling how often the floor texture repeats across the short and
 *   the long side, so a 512-pixel felt tile can cover the whole floor.
 * @param wallTexturePath relative to the package folder, or `null`.
 * @param wallTiling as [floorTiling], for the walls.
 */
data class TableLook(
  val id: String,
  val name: String,
  val floorTexturePath: String? = null,
  val floorTiling: Tiling = Tiling(),
  val wallTexturePath: String? = null,
  val wallTiling: Tiling = Tiling(),
  val floorColorArgb: Int = DEFAULT_FLOOR_COLOR_ARGB,
  val wallColorArgb: Int = DEFAULT_WALL_COLOR_ARGB,
  val roughness: Double = 0.9,
  val metallic: Double = 0.0,
  val friction: Double = 0.6,
  val restitution: Double = 0.2,
  val sound: TableSound = TableSound.Felt,
  val light: TableLight = TableLight.Neutral,
) {
  /** How often a texture repeats across the table, per axis. */
  data class Tiling(
    val acrossShortSide: Int = 1,
    val acrossLongSide: Int = 1,
  )

  /** The same look with every physics value forced inside its range. */
  fun clampedToLimits(): TableLook =
    copy(
      roughness = DieMaterial.clamp(roughness, DieMaterial.UnitRange, TableLook.default().roughness),
      metallic = DieMaterial.clamp(metallic, DieMaterial.UnitRange, TableLook.default().metallic),
      friction = DieMaterial.clamp(friction, FrictionRange, TableLook.default().friction),
      restitution = DieMaterial.clamp(restitution, RestitutionRange, TableLook.default().restitution),
    )

  companion object {
    /** The neutral grey `plain` table, and what a missing value falls back to. */
    const val DEFAULT_FLOOR_COLOR_ARGB: Int = 0xFF6E6E6E.toInt()

    /** The walls of the same. */
    const val DEFAULT_WALL_COLOR_ARGB: Int = 0xFF4A4A4A.toInt()

    /** A table may be a bit slippery or a bit grippy; never frictionless. */
    val FrictionRange: ClosedFloatingPointRange<Double> = 0.2..1.0

    /** Above 0.6 the tray is a trampoline and dice never settle. */
    val RestitutionRange: ClosedFloatingPointRange<Double> = 0.0..0.6

    /** How often a texture may repeat before it is moiré rather than felt. */
    val TilingRange: IntRange = 1..32

    private fun default(): TableLook = TableLook(id = "plain", name = "Plain")
  }
}

/**
 * The impact sound set a table uses. A package names one of these rather than
 * shipping audio: audio files are large and every decoder is attack surface
 * (`docs/tables.md`).
 */
enum class TableSound(
  val id: String,
) {
  Felt("felt"),
  Wood("wood"),
  Glass("glass"),
  Stone("stone"),
  Plastic("plastic"),
  ;

  companion object {
    /** What a package's `sound = "…"` names, or `null` if it names nothing. */
    fun ofId(id: String?): TableSound? = entries.firstOrNull { it.id == id }
  }
}

/**
 * The lighting preset a table uses. Named for the same reason as [TableSound]:
 * an environment map is a big file and a decoder to attack.
 */
enum class TableLight(
  val id: String,
) {
  Neutral("neutral"),
  Warm("warm"),
  Cool("cool"),
  Dim("dim"),
  ;

  companion object {
    /** What a package's `light = "…"` names, or `null` if it names nothing. */
    fun ofId(id: String?): TableLight? = entries.firstOrNull { it.id == id }
  }
}
