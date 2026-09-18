package de.drehtuer.dinfinity.feature.roll

/**
 * Where the result sheet is allowed to come to rest
 * (`docs/physics-and-rendering.md`, "What is drawn over the table").
 *
 * Two positions and no more. [Up] is the sheet as it arrives, with the total
 * and the whole breakdown on the screen; [Down] is the sheet pushed out of the
 * way, with its grip — and the total printed on it — still on the bottom edge.
 *
 * **There is no third position that hides it.** A result that could be
 * dismissed is a result somebody can lose, and the only way back to it would
 * be to roll the dice again, which is the one thing that cannot be undone.
 */
internal enum class SheetRest {
  /** Arrived: the total, the rows, the dice and the rounding control. */
  Up,

  /** Pushed out of the way: the grip and the total, on the bottom edge. */
  Down,
  ;

  /** The other one — what a tap on the grip asks for. */
  fun other(): SheetRest = if (this == Up) Down else Up
}

/**
 * The arithmetic of the result sheet's slide: how far it can travel, where a
 * drag has taken it, and which rest a let-go finger leaves it at.
 *
 * Plain Kotlin, and here rather than inside the gesture, for the reason
 * `SavedOrder` is: this is the part of a pull-up that can be *wrong*, and it is
 * the only part a JVM test can reach. What Compose is left with is the gesture
 * and the drawing — where the finger is, how fast it was going, and asking
 * this for the answer.
 *
 * Everything is in pixels, downwards-positive, and measured **from [SheetRest.Up]**:
 * an offset of `0` is the sheet fully up, and an offset of `travel` is the
 * sheet pushed all the way down. That is the same sign convention as Compose's
 * `Offset.y` and as a drag delta, so nothing in the gesture has to be negated.
 */
internal object SheetSlide {
  /**
   * How far the sheet can be pushed: its whole height less the part that never
   * leaves the screen.
   *
   * @param sheet the measured height of the whole sheet.
   * @param parked the measured height of its grip — the band carrying the
   *   handle and the total, which stays on the bottom edge in both rests.
   * @return zero rather than a negative number for a sheet that is no taller
   *   than its own grip. A breakdown can be one line (`1d20` prints a formula
   *   and one die), and a sheet with nothing under the grip has nowhere to go.
   */
  fun travelOf(
    sheet: Float,
    parked: Float,
  ): Float = (sheet - parked).coerceAtLeast(0f)

  /** Where [rest] sits, given how far the sheet can travel. */
  fun offsetOf(
    rest: SheetRest,
    travel: Float,
  ): Float =
    when (rest) {
      SheetRest.Up -> 0f
      SheetRest.Down -> travel.coerceAtLeast(0f)
    }

  /**
   * [offset] moved by [by], kept between the two rests.
   *
   * Clamped rather than rubber-banded: past [SheetRest.Up] there is nothing
   * above the sheet to reveal, and past [SheetRest.Down] the total would leave
   * the screen — which is the one thing this sheet promises not to do.
   */
  fun draggedTo(
    offset: Float,
    by: Float,
    travel: Float,
  ): Float = (offset + by).coerceIn(0f, travel.coerceAtLeast(0f))

  /**
   * Which rest a finger let go at [offset] and [velocity] leaves the sheet at.
   *
   * **A flick wins over a position.** Somebody who throws the sheet down from
   * an inch below the top meant down, and a sheet that sprang back because the
   * finger had not passed the halfway mark is a sheet that argues. Below that
   * speed the nearer rest wins, which is what a slow drag means.
   *
   * @param velocity pixels per second, positive downwards — Compose's own sign
   *   for a drag towards the bottom of the screen.
   */
  fun settledAt(
    offset: Float,
    velocity: Float,
    travel: Float,
  ): SheetRest {
    if (travel <= 0f) return SheetRest.Up
    val flick = travel * FLICK
    return when {
      velocity >= flick -> SheetRest.Down
      velocity <= -flick -> SheetRest.Up
      offset * 2f >= travel -> SheetRest.Down
      else -> SheetRest.Up
    }
  }

  /**
   * What counts as a flick, in **sheet heights per second**.
   *
   * A threshold in pixels per second would mean something different on every
   * phone and something different again for a one-line breakdown and a
   * twenty-die one; a threshold measured in the travel itself is the same
   * gesture everywhere. At 1.5 it is a finger that would cross the whole
   * travel in two thirds of a second, which is a throw rather than a push.
   */
  private const val FLICK = 1.5f
}
