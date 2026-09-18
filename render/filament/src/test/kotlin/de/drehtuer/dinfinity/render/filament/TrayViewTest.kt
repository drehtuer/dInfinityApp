package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.TableGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the player is allowed to look.
 *
 * One rule, and everything here is a way of asking whether it holds: **the
 * camera cannot leave the table.** A view that could would leave a player
 * looking at the void beside the tray, wondering where their dice went, with
 * no way back but guessing.
 */
class TrayViewTest {
  private val geometry = TableGeometry.referenceDevice()

  @Test
  fun `the whole tray has nowhere to pan to`() {
    // Every pixel of the table is already on screen, so there is no "elsewhere".
    val held = TrayView(zoom = 1.0, panAlongMm = 500.0, panAcrossMm = -500.0).within(geometry)

    assertEquals(0.0, held.panAlongMm, EXACT)
    assertEquals(0.0, held.panAcrossMm, EXACT)
  }

  @Test
  fun `at the closest the camera may get, any corner can be brought to the middle`() {
    // The complaint this rule answers: a die against a wall could be seen
    // only at the very edge of the frame, never looked at properly. At
    // CLOSEST the middle of the screen reaches the corner of the floor, so
    // every millimetre of table can be put where somebody is looking.
    val held =
      TrayView(
        zoom = TrayView.CLOSEST,
        panAlongMm = 10_000.0,
        panAcrossMm = 10_000.0,
      ).within(geometry)

    assertEquals(geometry.longSideMm / 2, held.panAlongMm, TOLERANCE)
    assertEquals(geometry.shortSideMm / 2, held.panAcrossMm, TOLERANCE)
  }

  @Test
  fun `the room to pan grows with the zoom and never shrinks`() {
    // Continuous and monotonic between the two ends, so pinching in never
    // takes away somewhere the player could already look.
    val reach =
      ZOOMS.map { zoom ->
        TrayView(zoom = zoom, panAlongMm = 10_000.0).within(geometry).panAlongMm
      }

    reach.zipWithNext { nearer, closer ->
      assertTrue("pinching in from $nearer left only $closer to pan", closer > nearer)
    }
    assertEquals(0.0, reach.first(), EXACT)
    assertEquals(geometry.longSideMm / 2, reach.last(), TOLERANCE)
  }

  @Test
  fun `the view never leaves the table, however hard it is dragged`() {
    var view = TrayView(zoom = TrayView.CLOSEST)
    repeat(FLINGS) {
      view = view.movedBy(by = 1.0, alongFraction = 1.0, acrossFraction = 1.0, geometry = geometry)
    }

    // The corner of the floor, and not a millimetre past it: the frame shows
    // wall beyond the edge, the camera is still aimed at table.
    assertEquals(geometry.longSideMm / 2, view.panAlongMm, TOLERANCE)
    assertEquals(geometry.shortSideMm / 2, view.panAcrossMm, TOLERANCE)
  }

  @Test
  fun `a pinch keeps what is under the fingers under the fingers`() {
    val spot = ScreenSpot(alongFraction = QUARTER, acrossFraction = -QUARTER)
    val before = TrayView.Whole

    val after = before.movedBy(by = 2.0, alongFraction = 0.0, acrossFraction = 0.0, geometry, about = spot)

    assertEquals(alongUnder(before, spot), alongUnder(after, spot), TOLERANCE)
    assertEquals(acrossUnder(before, spot), acrossUnder(after, spot), TOLERANCE)
  }

  @Test
  fun `pinching into the far corner is pinching into the far corner`() {
    // The other half of the complaint: a pinch that always zoomed about the
    // middle could not be aimed, so the corner a player had spotted a die in
    // walked off the screen as they went in.
    val corner = ScreenSpot(alongFraction = HALF, acrossFraction = HALF)

    val after = TrayView.Whole.movedBy(by = TrayView.CLOSEST, 0.0, 0.0, geometry, about = corner)

    assertEquals(TrayView.CLOSEST, after.zoom, EXACT)
    assertEquals(alongUnder(TrayView.Whole, corner), alongUnder(after, corner), TOLERANCE)
    assertTrue("the view did not move towards the corner", after.panAlongMm > 0.0)
  }

  @Test
  fun `a pinch about the middle moves nothing but the zoom`() {
    val after = TrayView.Whole.movedBy(by = 2.0, 0.0, 0.0, geometry, about = ScreenSpot.Middle)

    assertEquals(2.0, after.zoom, EXACT)
    assertEquals(0.0, after.panAlongMm, EXACT)
    assertEquals(0.0, after.panAcrossMm, EXACT)
  }

  @Test
  fun `the corner framing shows only table, which is what a thumbnail is of`() {
    // Deliberately *not* the pan limit any more: the frame's far edge lands on
    // the wall rather than its middle doing so.
    val view = TrayView.inTheCorner(geometry)

    assertEquals(TrayView.CLOSEST, view.zoom, EXACT)
    assertEquals(geometry.longSideMm / 2, view.panAlongMm + geometry.longSideMm / 2 / view.zoom, TOLERANCE)
    assertEquals(geometry.shortSideMm / 2, view.panAcrossMm + geometry.shortSideMm / 2 / view.zoom, TOLERANCE)
    // And it is somewhere the player is allowed to be, with room to spare.
    assertEquals(view, view.within(geometry))
  }

  @Test
  fun `the whole tray has no corner to stand in`() {
    assertEquals(TrayView.Whole, TrayView.inTheCorner(geometry, zoom = 1.0))
    assertEquals(TrayView.Whole, TrayView.inTheCorner(geometry, zoom = 0.1))
  }

  @Test
  fun `zoom is held between the whole tray and the closest the camera may get`() {
    assertEquals(1.0, TrayView(zoom = 0.01).within(geometry).zoom, EXACT)
    assertEquals(TrayView.CLOSEST, TrayView(zoom = 1_000.0).within(geometry).zoom, EXACT)
  }

  @Test
  fun `pinching is a ratio, so the same gesture means the same thing at any distance`() {
    val once = TrayView.Whole.movedBy(by = 2.0, alongFraction = 0.0, acrossFraction = 0.0, geometry = geometry)
    val twice = once.movedBy(by = 1.5, alongFraction = 0.0, acrossFraction = 0.0, geometry = geometry)

    assertEquals(2.0, once.zoom, EXACT)
    assertEquals(THREE, twice.zoom, EXACT)
  }

  @Test
  fun `a drag moves less table the closer the camera is`() {
    val near =
      TrayView(zoom = 2.0).movedBy(by = 1.0, alongFraction = HALF, acrossFraction = 0.0, geometry = geometry)
    val nearer =
      TrayView(zoom = 4.0).movedBy(by = 1.0, alongFraction = HALF, acrossFraction = 0.0, geometry = geometry)

    // Half a screen of a screen that holds half the table is half of that.
    assertTrue(
      "dragging at ${nearer.zoom}× moved as much table as at ${near.zoom}×",
      nearer.panAlongMm < near.panAlongMm,
    )
  }

  @Test
  fun `a pinch that lands back at the whole tray is back in the middle`() {
    // Zooming out undoes the room to pan, so the view has to come back with it
    // rather than staying off the edge it was allowed to reach.
    val wandered = TrayView(zoom = TrayView.CLOSEST, panAlongMm = 1_000.0).within(geometry)
    val backOut = wandered.movedBy(by = 0.001, alongFraction = 0.0, acrossFraction = 0.0, geometry = geometry)

    assertEquals(1.0, backOut.zoom, EXACT)
    assertEquals(0.0, backOut.panAlongMm, EXACT)
  }

  /** The millimetre of tray, along its long side, that sits under [spot]. */
  private fun alongUnder(
    view: TrayView,
    spot: ScreenSpot,
  ): Double = view.panAlongMm + spot.alongFraction * geometry.longSideMm / view.zoom

  /** And across its short one. */
  private fun acrossUnder(
    view: TrayView,
    spot: ScreenSpot,
  ): Double = view.panAcrossMm + spot.acrossFraction * geometry.shortSideMm / view.zoom

  private companion object {
    const val EXACT = 0.0
    const val TOLERANCE = 1e-9
    const val FLINGS = 20
    const val THREE = 3.0
    const val HALF = 0.5
    const val QUARTER = 0.25
    val ZOOMS = listOf(1.0, 1.5, 2.0, 3.0, TrayView.CLOSEST)
  }
}
