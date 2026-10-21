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
 * **Every texture is named by a path inside [packageId]'s package**, and only
 * there. A package cannot reach another package's files: it never writes
 * [packageId] — that is not a key of the format — the validator does, from the
 * package the look was read out of (`docs/tables.md`, "Textures").
 *
 * @param id slug, unique within its package.
 * @param name what the picker shows.
 * @param floorTexturePath the floor's colour picture, relative to the package
 *   folder, or `null` for a plain [floorColorArgb]. Coloured by
 *   [floorColorArgb] the way [colorMode] says, which is how one grey felt is
 *   green felt and black felt.
 * @param floorTiling how often the floor texture repeats across the short and
 *   the long side, so a 512-pixel felt tile can cover the whole floor.
 * @param wallTexturePath relative to the package folder, or `null`.
 * @param wallTiling as [floorTiling], for the walls.
 * @param floorNormalPath the floor's normal map (OpenGL convention: green is
 *   up the picture), or `null` for a flat floor.
 * @param floorRoughnessPath the floor's roughness, grey, read from the red
 *   channel; `null` takes [roughness] everywhere.
 * @param floorTileMm how many millimetres of floor one copy of the floor's
 *   pictures covers, laid square. Replaces [floorTiling] when set, which is
 *   what keeps felt the same weave on a tall phone and a squat one.
 * @param wallNormalPath as [floorNormalPath], for the walls.
 * @param wallRoughnessPath as [floorRoughnessPath], for the walls.
 * @param wallTileMm as [floorTileMm], for the walls.
 * @param colorMode how [floorColorArgb] and [wallColorArgb] meet a colour
 *   picture: multiplied by it, or what it averages out to.
 * @param packageId the package this look was read out of, which is the only
 *   package its textures are looked for in; `null` for a look made in code,
 *   which then has no textures to draw.
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
  val floorNormalPath: String? = null,
  val floorRoughnessPath: String? = null,
  val floorTileMm: Double? = null,
  val wallNormalPath: String? = null,
  val wallRoughnessPath: String? = null,
  val wallTileMm: Double? = null,
  val colorMode: TableColorMode = TableColorMode.Multiply,
  val packageId: String? = null,
) {
  /** True when any part of this look is drawn from a picture rather than a colour alone. */
  val textured: Boolean
    get() =
      listOf(floorTexturePath, floorNormalPath, floorRoughnessPath, wallTexturePath, wallNormalPath, wallRoughnessPath)
        .any { it != null }

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

    /**
     * How much table one copy of a texture may cover, in millimetres. A
     * centimetre is already a weave nobody can see on a phone, and a metre is
     * four trays: a picture that big is never seen to repeat at all.
     */
    val TileMmRange: ClosedFloatingPointRange<Double> = 10.0..1000.0

    private fun default(): TableLook = TableLook(id = "plain", name = "Plain")
  }
}

/**
 * How a table's colour meets its colour picture (`color_mode`,
 * `docs/tables.md`, "Textures").
 */
enum class TableColorMode(
  val id: String,
) {
  /**
   * The colour multiplies the picture, so the floor is darker than the colour
   * by the picture's own shade. What a photograph wants: its colour is white,
   * and it is shown as it was taken.
   */
  Multiply("multiply"),

  /**
   * The picture is scaled so that it averages out at the colour. What a dyed
   * cloth wants: `#1f5e3a` is the green the felt *is*, and the picture gives
   * it its grain without moving it.
   */
  Average("average"),
  ;

  companion object {
    /** What a package's `color_mode = "…"` names, or `null` if it names nothing. */
    fun ofId(id: String?): TableColorMode? = entries.firstOrNull { it.id == id }
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
