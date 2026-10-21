package de.drehtuer.dinfinity.simulation.jolt

import android.util.Log

/**
 * Where a roll says how its hand let go, for somebody tuning the toss on a
 * phone (`docs/physics-and-rendering.md`, "The hand lets go").
 *
 * **Off unless asked for**, and asked for from outside the app:
 * `adb shell setprop log.tag.dinfinity.toss DEBUG` turns it on for the next
 * roll, on any build, and nothing is formatted while it is off. What it
 * writes is the shake's own numbers — never a seed and never a face, so it
 * replays nothing (decision 13).
 */
internal object TossLog {
  const val TAG: String = "dinfinity.toss"

  /** Where a roll opened now writes its lines, or null for nowhere. */
  fun current(): ((String) -> Unit)? = if (Log.isLoggable(TAG, Log.DEBUG)) { line -> Log.d(TAG, line) } else null

  /** The middle of [values], or nought for none: how fast the dice were going when the hand let go. */
  fun medianOf(values: List<Double>): Double = values.sorted().getOrElse(values.size / 2) { 0.0 }
}
