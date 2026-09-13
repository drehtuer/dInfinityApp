package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.TableGeometry

/**
 * How much of the tray the player is looking at
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * The camera never moves on its own — not when the dice settle, not ever. It
 * frames the whole tray, and this is the one thing that changes that: the
 * player pinching to look closer and dragging to look elsewhere
 * (`docs/TODO.md`, Step 4.1).
 *
 * **It cannot leave the table.** [within] is not a nicety, it is the whole
 * rule: at [Whole] there is nothing to pan to, and every step closer earns
 * exactly as much room to move as it took away. A player who pinches in and
 * flings a finger cannot end up looking at the void beside the tray and
 * wondering where their dice went.
 *
 * Millimetres of the tray, not pixels of the screen, because that is what the
 * camera is aimed in and what the tray is measured in. Turning a finger's
 * travel into millimetres is the gesture's job, where the viewport is known.
 *
 * @param zoom how much closer than the whole tray, 1 being all of it.
 * @param panAlongMm how far the view has moved along the tray's long side.
 * @param panAcrossMm and across its short one.
 */
data class TrayView(
  val zoom: Double = 1.0,
  val panAlongMm: Double = 0.0,
  val panAcrossMm: Double = 0.0,
) {
  /**
   * The nearest view to this one that is still on the table.
   *
   * The zoom is held between the whole tray and [CLOSEST]. What that leaves is
   * the room to pan: at zoom *z* the camera holds a *z*-th of each side, so it
   * can move half of what it no longer holds in either direction and no
   * further. At zoom 1 that is nothing at all, which is why the whole tray has
   * no pan to speak of.
   */
  fun within(geometry: TableGeometry): TrayView {
    val held = zoom.coerceIn(1.0, CLOSEST)
    val along = roomToMove(geometry.longSideMm, held)
    val across = roomToMove(geometry.shortSideMm, held)
    return TrayView(
      zoom = held,
      panAlongMm = panAlongMm.coerceIn(-along, along),
      panAcrossMm = panAcrossMm.coerceIn(-across, across),
    )
  }

  /**
   * The same view, pinched by [by] and dragged by the rest.
   *
   * Zooming is multiplied rather than added, because a pinch is a ratio: the
   * same finger movement means the same change in what is on screen whether it
   * starts near or far.
   *
   * The drag is divided by the new zoom because it arrives as a fraction of
   * the screen, and the screen holds less of the tray the closer the camera
   * is: dragging half a screen should move the view half a screen's worth of
   * *table*, which is a smaller distance the further in the player has gone.
   */
  fun movedBy(
    by: Double,
    alongFraction: Double,
    acrossFraction: Double,
    geometry: TableGeometry,
  ): TrayView {
    val held = (zoom * by).coerceIn(1.0, CLOSEST)
    return TrayView(
      zoom = held,
      panAlongMm = panAlongMm + alongFraction * geometry.longSideMm / held,
      panAcrossMm = panAcrossMm + acrossFraction * geometry.shortSideMm / held,
    ).within(geometry)
  }

  companion object {
    /** The whole table, which is where every roll is watched from. */
    val Whole: TrayView = TrayView()

    /**
     * As close as the camera is allowed to get.
     *
     * Four times in is a 16 mm die filling about a fifth of the screen's width,
     * which is enough to read a face that is being argued about and not so much
     * that the table has gone. Whether it is the right number is a question
     * with a phone in a hand at the end of it (`docs/TODO.md`, Step 5.6).
     */
    const val CLOSEST: Double = 4.0

    private fun roomToMove(
      sideMm: Double,
      zoom: Double,
    ): Double = sideMm / 2 * (1.0 - 1.0 / zoom)
  }
}
