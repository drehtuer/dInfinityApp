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
  fun `closer in earns exactly as much room as it took away`() {
    // At twice in, half of each side is on screen, so the view can move a
    // quarter of a side either way and its edge lands on the tray's edge.
    val held = TrayView(zoom = 2.0, panAlongMm = 10_000.0).within(geometry)

    assertEquals(geometry.longSideMm / 4, held.panAlongMm, TOLERANCE)
  }

  @Test
  fun `the view never leaves the table, however hard it is dragged`() {
    var view = TrayView(zoom = TrayView.CLOSEST)
    repeat(FLINGS) {
      view = view.movedBy(by = 1.0, alongFraction = 1.0, acrossFraction = 1.0, geometry = geometry)
    }

    val edgeAlong = geometry.longSideMm / 2 * (1.0 - 1.0 / TrayView.CLOSEST)
    val edgeAcross = geometry.shortSideMm / 2 * (1.0 - 1.0 / TrayView.CLOSEST)
    assertEquals(edgeAlong, view.panAlongMm, TOLERANCE)
    assertEquals(edgeAcross, view.panAcrossMm, TOLERANCE)
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

  private companion object {
    const val EXACT = 0.0
    const val TOLERANCE = 1e-9
    const val FLINGS = 20
    const val THREE = 3.0
    const val HALF = 0.5
  }
}
