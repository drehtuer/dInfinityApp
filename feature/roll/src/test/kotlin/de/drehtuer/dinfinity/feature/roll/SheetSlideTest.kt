package de.drehtuer.dinfinity.feature.roll

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the result sheet comes to rest, and what a finger does to that
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * All of it is arithmetic and none of it needs a screen, which is the point of
 * [SheetSlide] existing at all: a pull-up whose rests lived inside the gesture
 * would only ever be checkable by looking at it. What is left for Robolectric
 * is that the gesture is wired to these answers ([PullUpResultTest]).
 */
class SheetSlideTest {
  @Test
  fun `the travel is the sheet less the part that never leaves the screen`() {
    assertEquals(700f, SheetSlide.travelOf(sheet = 900f, parked = 200f), EXACT)
  }

  @Test
  fun `a sheet no taller than its own grip has nowhere to go`() {
    // `1d20` prints a formula line and one die, so the breakdown can be
    // shorter than the grip's own padding. Negative travel would push the
    // total off the bottom of the screen.
    assertEquals(0f, SheetSlide.travelOf(sheet = 180f, parked = 200f), EXACT)
  }

  @Test
  fun `up is the top of the travel and down is the bottom of it`() {
    assertEquals(0f, SheetSlide.offsetOf(SheetRest.Up, travel = 700f), EXACT)
    assertEquals(700f, SheetSlide.offsetOf(SheetRest.Down, travel = 700f), EXACT)
  }

  @Test
  fun `a rest is never asked for a negative offset`() {
    assertEquals(0f, SheetSlide.offsetOf(SheetRest.Down, travel = -40f), EXACT)
  }

  @Test
  fun `a drag moves the sheet by what the finger moved`() {
    assertEquals(160f, SheetSlide.draggedTo(offset = 100f, by = 60f, travel = 700f), EXACT)
  }

  @Test
  fun `a drag cannot push the sheet past either rest`() {
    // Clamped rather than rubber-banded: above `Up` there is nothing to
    // reveal, and below `Down` the total leaves the screen.
    assertEquals(0f, SheetSlide.draggedTo(offset = 20f, by = -500f, travel = 700f), EXACT)
    assertEquals(700f, SheetSlide.draggedTo(offset = 650f, by = 500f, travel = 700f), EXACT)
  }

  @Test
  fun `a slow drag settles at the rest it is nearer`() {
    assertEquals(SheetRest.Up, SheetSlide.settledAt(offset = 200f, velocity = 0f, travel = 700f))
    assertEquals(SheetRest.Down, SheetSlide.settledAt(offset = 500f, velocity = 0f, travel = 700f))
  }

  @Test
  fun `exactly halfway goes down, because that is the way the finger was going`() {
    assertEquals(SheetRest.Down, SheetSlide.settledAt(offset = 350f, velocity = 0f, travel = 700f))
  }

  @Test
  fun `a flick downwards wins over a sheet that has barely moved`() {
    // The fault this is the fix for: a sheet thrown at the bottom edge from an
    // inch below the top springing back, because the finger had not passed the
    // halfway mark.
    assertEquals(SheetRest.Down, SheetSlide.settledAt(offset = 40f, velocity = 2000f, travel = 700f))
  }

  @Test
  fun `and a flick upwards wins over one that has nearly arrived`() {
    assertEquals(SheetRest.Up, SheetSlide.settledAt(offset = 660f, velocity = -2000f, travel = 700f))
  }

  @Test
  fun `a push too slow to be a flick leaves the position to decide`() {
    // 1.5 travels a second is the bar; 700 px of travel makes that 1050 px/s.
    assertEquals(SheetRest.Up, SheetSlide.settledAt(offset = 100f, velocity = 900f, travel = 700f))
  }

  @Test
  fun `a sheet with no travel is up, whatever the finger did`() {
    assertEquals(SheetRest.Up, SheetSlide.settledAt(offset = 0f, velocity = 9000f, travel = 0f))
  }

  @Test
  fun `each rest knows the other one, which is what a tap asks for`() {
    assertEquals(SheetRest.Down, SheetRest.Up.other())
    assertEquals(SheetRest.Up, SheetRest.Down.other())
  }

  /** These are exact answers: nothing in [SheetSlide] divides or interpolates. */
  private companion object {
    const val EXACT = 0f
  }
}
