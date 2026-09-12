package de.drehtuer.dinfinity.simulation.api

/**
 * The tray the dice land in (`docs/tables.md`, "Geometry").
 *
 * Two things about it are fixed and one is exchangeable. The mesh is fixed —
 * a rectangular box with rounded inner corners, so nobody can ship a table
 * with a hole in the floor. The *size* is fixed too: the long side is always
 * 240 mm, whatever phone it is, and only the aspect ratio follows the screen.
 * A 6.3-inch and a 6.9-inch phone get the same table, which is what keeps
 * rolls comparable and the capacity numbers below stable across devices.
 *
 * Simulation units are millimetres throughout.
 *
 * @param shortSideMm the short side, already clamped to what a phone can be.
 */
data class TableGeometry(
  val shortSideMm: Double,
) {
  init {
    require(shortSideMm.isFinite() && shortSideMm > 0) { "a table $shortSideMm mm across is not a table" }
  }

  /** Always 240 mm: a real dice tray, regardless of the phone. */
  val longSideMm: Double get() = LONG_SIDE_MM

  /** The floor, in square millimetres — what the capacity rule shares out. */
  val floorAreaMm2: Double get() = longSideMm * shortSideMm

  /** How tall the walls are. Dice cannot leave: there is a ceiling above them too. */
  val wallHeightMm: Double get() = WALL_HEIGHT_MM

  /** An invisible lid, so no shake however hard throws a die out of the tray. */
  val ceilingHeightMm: Double get() = CEILING_HEIGHT_MM

  /** Rounded, so a die driven into a corner cannot wedge in it. */
  val cornerRadiusMm: Double get() = CORNER_RADIUS_MM

  companion object {
    /** The long side, on every device. */
    const val LONG_SIDE_MM: Double = 240.0

    /** Walls a die cannot bounce over at any sane speed. */
    const val WALL_HEIGHT_MM: Double = 60.0

    /** And a lid, for the speeds that are not sane. */
    const val CEILING_HEIGHT_MM: Double = 200.0

    /** Dice do not wedge into sharp corners, so there are none. */
    const val CORNER_RADIUS_MM: Double = 12.0

    /**
     * How narrow or square the table may be, whatever shape the screen is.
     *
     * A phone far outside this would otherwise produce a tray that is either a
     * corridor or a square, and the capacity numbers assume neither.
     */
    val ASPECT_RANGE: ClosedFloatingPointRange<Double> = 0.40..0.75

    /**
     * The table for a screen whose short side is [aspect] of its long one.
     *
     * A Pixel 10a is 20:9, so `aspect` is 0.45 and the table is 240 × 108 mm —
     * the dimensions every worked number in `docs/tables.md` is computed from.
     */
    fun forAspect(aspect: Double): TableGeometry {
      require(aspect.isFinite()) { "a screen cannot have an aspect ratio of $aspect" }
      return TableGeometry(LONG_SIDE_MM * aspect.coerceIn(ASPECT_RANGE))
    }

    /** The reference device's table, which the documented capacity table is for. */
    fun referenceDevice(): TableGeometry = forAspect(PIXEL_10A_ASPECT)

    /** 20:9, the Pixel 10a. */
    const val PIXEL_10A_ASPECT: Double = 9.0 / 20.0
  }
}
