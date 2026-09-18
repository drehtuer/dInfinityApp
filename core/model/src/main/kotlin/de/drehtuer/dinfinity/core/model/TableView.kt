package de.drehtuer.dinfinity.core.model

/**
 * How far the camera leans over the table
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * [StraightDown] is the default, and it is the default because of what a phone
 * showed: at 411 × 923 dp the leaning shot spends a large share of the frame on
 * the wooden rim and leaves the felt a tall trapezoid inside it, and the
 * furniture is not what anybody is looking at. [Angled] is that shot, kept
 * because the argument for it is real — straight down is a diagram, and the
 * point of rolling dice is watching them tumble.
 *
 * It is a camera either way and never a projection: straight down still draws
 * the dice in perspective and still casts their shadows, it just stops leaning.
 * How many degrees each position is worth belongs to the camera rather than
 * here — `TrayCamera.tiltDegreesOf` is the one place that says.
 *
 * Read when the roll screen opens rather than watched, like power saving, the
 * shake, the haptics, the sound and the rounding: a camera that moved under a
 * roll in progress would not be a setting taking effect
 * (`docs/architecture.md`, decision 16).
 *
 * @param id the stable key written to storage. Never rename one: an unknown id
 *   read back falls to [Default].
 */
enum class TableView(
  val id: String,
) {
  /** Every die square to the screen, and no wall in shot. */
  StraightDown("straight_down"),

  /** The 22° shot, showing the top and the left wall. */
  Angled("angled"),
  ;

  companion object {
    /** What a new install rolls on, and what an unreadable setting falls back to. */
    val Default: TableView = StraightDown

    /** Storage is a string, and strings from disk are not to be trusted. */
    fun ofId(id: String?): TableView = entries.firstOrNull { it.id == id } ?: Default
  }
}
