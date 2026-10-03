package de.drehtuer.dinfinity.feature.saved

import kotlin.math.max
import kotlin.math.min

/**
 * How fast the saved-rolls list scrolls under a row held near its edge
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * A drag can only put a row where the finger can reach, and the finger can
 * only reach what the list shows. Holding the row in a band along the top or
 * bottom edge scrolls the list towards that edge, faster the deeper into the
 * band the finger is, so a row can travel the whole list in one drag.
 *
 * Plain Kotlin for the same reason as [SavedOrder]: what the speed should be is
 * arithmetic, and arithmetic is what a JVM test can reach. Compose is left with
 * asking for the answer once a frame and scrolling by it.
 *
 * All lengths are in one unit — pixels on the screen, dp in the constants —
 * and all positions are in the list's own coordinates, 0 at the top of what it
 * shows.
 */
object SavedEdgeScroll {
  /** How tall the band along each edge is, in dp. A tuning constant. */
  const val ZONE_DP = 64f

  /** The speed at the very edge and beyond it, in dp per second. A tuning constant. */
  const val TOP_SPEED_DP_PER_SECOND = 640f

  /**
   * The longest frame that is scrolled in full. A frame that stalled — a
   * garbage collection, the app coming back from the background — would
   * otherwise jump the list by however long the stall was.
   */
  const val LONGEST_FRAME_NANOS = 50_000_000L

  private const val NANOS_PER_SECOND = 1_000_000_000f

  /**
   * The speed to scroll at, negative towards the top and positive towards the
   * bottom, zero outside both bands.
   *
   * The speed grows linearly from nothing at the band's inner edge to
   * [topSpeed] at the list's own edge, and stays there for a finger dragged
   * past it.
   *
   * **Only towards where the finger went.** A row picked up inside a band —
   * the first or last row shown, usually — does not set the list moving by
   * being picked up: the band starts where the drag started ([origin]) when
   * that is nearer the edge than the band's inner edge, so the list only
   * scrolls once the finger has moved towards the edge, and gathers speed
   * from there (`docs/architecture.md`, decision 78).
   *
   * @param pointer where the finger is.
   * @param origin where the drag started.
   * @param viewport how tall the list's visible part is.
   * @param zone how tall each band is. On a list shorter than two bands, each
   *   band is half of it, so the two never overlap.
   * @param topSpeed the speed at the edge, in the same length per second.
   */
  fun speed(
    pointer: Float,
    origin: Float,
    viewport: Float,
    zone: Float,
    topSpeed: Float,
  ): Float {
    if (viewport <= 0f || zone <= 0f) return 0f
    val reach = min(zone, viewport / 2f)
    val top = min(reach, origin)
    val bottom = max(viewport - reach, origin)
    return when {
      pointer < top -> -topSpeed * depth(top - pointer, top)
      pointer > bottom -> topSpeed * depth(pointer - bottom, viewport - bottom)
      else -> 0f
    }
  }

  /**
   * How far to scroll in one frame at [speed], given the time since the last
   * one. A frame of no time, or of negative time from a clock that went
   * backwards, scrolls nothing; one longer than [LONGEST_FRAME_NANOS] scrolls
   * as if it were that long.
   */
  fun step(
    speed: Float,
    elapsedNanos: Long,
  ): Float {
    if (elapsedNanos <= 0L) return 0f
    return speed * min(elapsedNanos, LONGEST_FRAME_NANOS) / NANOS_PER_SECOND
  }

  /** How far into a band of [span] a finger [into] it is, from 0 to 1. */
  private fun depth(
    into: Float,
    span: Float,
  ): Float = if (span <= 0f) 1f else min(into / span, 1f)
}
