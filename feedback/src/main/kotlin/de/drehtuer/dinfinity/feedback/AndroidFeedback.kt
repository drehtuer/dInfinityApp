package de.drehtuer.dinfinity.feedback

import android.content.Context
import android.os.Handler
import android.os.HandlerThread

/**
 * Builds the real player: the phone's actuator, the phone's audio device, and
 * a thread to hold a cue until its moment.
 *
 * The one place the three Android things in this module are named together, so
 * that everything above it takes an `Impacts` and nothing above it knows there
 * is a vibrator involved (`docs/architecture.md`, decision 40).
 *
 * The thread is the interesting part. In normal mode every cue is due now and
 * it does nothing but move the work off the roll thread — which matters, because
 * the roll thread is the one stepping the physics and drawing the frame, and
 * `AudioTrack.play` is not free. In power-saving mode it is what "played back
 * over about a second" is made of: the roll finished in eighty milliseconds and
 * the cues are posted forward across the second after it
 * (`docs/physics-and-rendering.md`, "Power-saving mode").
 */
object AndroidFeedback {
  /** The thread cues wait on. Named so it is obvious in a trace. */
  const val THREAD_NAME: String = "dinfinity-feedback"

  /**
   * A player for one visit to the roll screen.
   *
   * Both settings off gives [ImpactFeedback] with nothing behind it rather than
   * a null: the tray always has a player, that player plays nothing, and the
   * roll it watches is not recording impacts either — so the decision is made
   * once, here, and nowhere twice.
   */
  fun create(
    context: Context,
    haptics: Boolean,
    sound: Boolean,
  ): ImpactFeedback {
    if (!haptics && !sound) return ImpactFeedback(haptics = false, sound = false)
    val thread = HandlerThread(THREAD_NAME).apply { start() }
    val handler = Handler(thread.looper)
    return ImpactFeedback(
      buzzer = if (haptics) SystemBuzzer(context.applicationContext) else Buzzer.NONE,
      speaker = if (sound) PcmSpeaker() else Speaker.NONE,
      haptics = haptics,
      sound = sound,
      later = { millis, work -> handler.postDelayed(work, millis) },
      stop = { thread.quitSafely() },
    )
  }
}
