package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.TableGeometry

/**
 * How much of the tray the player is looking at
 * (`docs/physics-and-rendering.md`, "Rendering").
 *
 * The camera never moves on its own — not when the dice settle, not ever. It
 * frames the whole tray, and this is the one thing that changes that: the
 * player pinching to look closer and dragging with two fingers to look
 * elsewhere (`docs/TODO.md`, Step 4.1).
 *
 * **It cannot leave the table.** [within] is not a nicety, it is the whole
 * rule: at [Whole] there is nothing to pan to, and the room to move grows with
 * the zoom until, at [CLOSEST], any point of the floor — the corners included
 * — can be brought to the middle of the screen. A player who pinches in and
 * flings a finger cannot end up looking past the tray and wondering where
 * their dice went.
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
   * the room to pan, and the rule for it is this:
   *
   * At zoom *z* the camera holds a *z*-th of each side, so the framed box
   * reaches `side / (2z)` either way from the middle of the screen. Keeping
   * that *box* inside the tray allows `side / 2 · (1 − 1/z)`, which is what
   * this used to be — and it is the reason a die lying against a wall could
   * only ever sit at the extreme edge of the frame. Somebody who has pinched
   * in to read that die is looking at the middle of their screen, and the one
   * place the old rule would not let them put it was there.
   *
   * So the frame is allowed past the wall, by exactly as much as it takes for
   * the *middle of the screen* to reach the corner at the closest the camera
   * may get:
   *
   * ```
   * roomToMove(side, z) = side / 2 · (1 − 1/z) / (1 − 1/CLOSEST)
   * ```
   *
   * Dividing by what the old rule allowed at [CLOSEST] is the whole of the
   * change. It is still nothing at all at the whole tray, still continuous and
   * still monotonic in the zoom, so a pan feels the same — it simply goes
   * further — and at [CLOSEST] it is exactly `side / 2`, the corner.
   *
   * **What it gives up:** past the old limit the frame shows the tray's wall
   * rising beyond the edge of the floor. That is a picture of the table rather
   * than of the void beside it — the wall is modelled, lit and in shot — but
   * there is less felt on screen the further out the player pushes, and at the
   * corner half the frame is rim. The dice are the thing worth seeing and this
   * is what it costs to see one of them properly.
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
   * The same view, pinched by [by] about [about] and dragged by the rest.
   *
   * Zooming is multiplied rather than added, because a pinch is a ratio: the
   * same finger movement means the same change in what is on screen whether it
   * starts near or far.
   *
   * The drag is divided by the new zoom because it arrives as a fraction of
   * the screen, and the screen holds less of the tray the closer the camera
   * is: dragging half a screen should move the view half a screen's worth of
   * *table*, which is a smaller distance the further in the player has gone.
   *
   * [about] is where the fingers are, and it is what keeps a pinch under them.
   * The point of table at screen fraction *f* is `pan + f · side / z`; asking
   * for it to still be at *f* after the zoom gives
   * `pan' = pan + f · side · (1/z − 1/z')`, which is the whole correction.
   * Without it every pinch pulls toward the middle of the screen, and a player
   * who has spotted a die in the corner cannot pinch into the corner.
   */
  fun movedBy(
    by: Double,
    alongFraction: Double,
    acrossFraction: Double,
    geometry: TableGeometry,
    about: ScreenSpot = ScreenSpot.Middle,
  ): TrayView {
    val from = zoom.coerceIn(1.0, CLOSEST)
    val held = (from * by).coerceIn(1.0, CLOSEST)
    val kept = 1.0 / from - 1.0 / held
    return TrayView(
      zoom = held,
      panAlongMm =
        panAlongMm + about.alongFraction * geometry.longSideMm * kept +
          alongFraction * geometry.longSideMm / held,
      panAcrossMm =
        panAcrossMm + about.acrossFraction * geometry.shortSideMm * kept +
          acrossFraction * geometry.shortSideMm / held,
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

    /**
     * The view at [zoom] whose frame sits flush inside the tray's far corner.
     *
     * Not a limit — [within] lets the player go further than this, all the way
     * to standing the middle of the screen on the corner. This is the framing
     * that shows *only* table: the frame reaches `side / (2 · zoom)` from its
     * middle, so putting its middle `side / 2 · (1 − 1/zoom)` out lands its
     * far edge exactly on the wall, with two walls and the rounded corner
     * between them in shot and no floor edge crossed.
     *
     * [ThumbnailPlan] is what wants it: a thumbnail is a picture of a table,
     * and half a picture of what is beyond one is not.
     */
    fun inTheCorner(
      geometry: TableGeometry,
      zoom: Double = CLOSEST,
    ): TrayView {
      val held = zoom.coerceIn(1.0, CLOSEST)
      return TrayView(
        zoom = held,
        panAlongMm = geometry.longSideMm / 2 * (1.0 - 1.0 / held),
        panAcrossMm = geometry.shortSideMm / 2 * (1.0 - 1.0 / held),
      )
    }

    private fun roomToMove(
      sideMm: Double,
      zoom: Double,
    ): Double = sideMm / 2 * (1.0 - 1.0 / zoom) / (1.0 - 1.0 / CLOSEST)
  }
}

/**
 * A place on the screen, as a fraction of it from its middle.
 *
 * Fractions rather than pixels for the reason the drag is: the tray is
 * measured in millimetres and the screen in neither, so a phone's pixel
 * density is nobody's business up here. Half a screen either way is ±0.5.
 *
 * The axes are the tray's, not the screen's: [alongFraction] runs up the
 * screen, which is the tray's long side and `+x` (`docs/tables.md`), and
 * [acrossFraction] runs to the screen's *left*, which is `+y`. Turning a
 * touch's pixels into these is the gesture's job, where the viewport is known.
 */
data class ScreenSpot(
  val alongFraction: Double = 0.0,
  val acrossFraction: Double = 0.0,
) {
  companion object {
    /** Dead centre — a pinch that zooms about the middle, which is no pinch point at all. */
    val Middle: ScreenSpot = ScreenSpot()
  }
}
