package de.drehtuer.dinfinity.feature.roll

import android.content.Context
import android.hardware.SensorManager
import android.view.Surface
import androidx.annotation.VisibleForTesting
import de.drehtuer.dinfinity.input.shake.SensorShakeSource
import de.drehtuer.dinfinity.simulation.api.ShakeSample

/**
 * Where [ShakeToRoll] hears a shake from: the motion sensors, everywhere but
 * in a test.
 *
 * **A shake is the only way to start a roll** (`docs/architecture.md`,
 * decision 66), so a test that wants dice in the air has to shake — and
 * neither Robolectric nor a phone on a desk can be shaken on cue. This is the
 * one place a test can stand in for the hand, and it stands in *behind*
 * [ShakeToRoll] rather than beside it: a test hand reaches the roll through
 * exactly the `onStarted → RollPresenter.roll` and `onSample →
 * RollPresenter.shaking` calls the sensors do, so there is still one path to
 * a number and nothing a test throws that a shake would not.
 *
 * It is not a way in for a player. Nothing the app draws or exposes calls
 * [current]'s setter; the only code that does is a test, which is what
 * [VisibleForTesting] tells lint to hold it to.
 *
 * Process-wide rather than a parameter of the screen or a `CompositionLocal`,
 * because the device tests launch `MainActivity` as it ships and so cannot hand
 * a composable anything. One seam for all three kinds of test is simpler than
 * a local for the JVM ones and a global for the device. The cost is the usual
 * one of a global: a test that sets it puts [SENSORS] back when it is done.
 */
fun interface ShakeInput {
  /**
   * Starts listening, for as long as the screen is resumed. What it answers
   * stops it; stopping is safe more than once.
   */
  fun listen(
    context: Context,
    hand: Hand,
  ): Stop

  /** Stops a [listen]. */
  fun interface Stop {
    fun stop()
  }

  /**
   * What a shake tells the screen, in the order it tells it.
   *
   * @param onStarted the shake is confirmed and the dice are thrown now;
   *   answers whether anything was (`SensorShakeSource`).
   * @param onEnded the hand has stopped.
   * @param onSample each moment of the shake, as it happens.
   */
  class Hand(
    val onStarted: () -> Boolean,
    val onEnded: () -> Unit,
    val onSample: (ShakeSample) -> Unit,
  )

  companion object {
    /** The accelerometer and the gyroscope: what every shipped build listens to. */
    val SENSORS: ShakeInput = SensorShakeInput

    /** What the roll screen listens to. [SENSORS] unless a test has said otherwise. */
    @Volatile
    @set:VisibleForTesting
    var current: ShakeInput = SENSORS
  }
}

/**
 * The sensors, through [SensorShakeSource].
 *
 * The display's rotation is read per sample rather than captured once. The
 * tray is the screen however the screen is held, so which device axis runs up
 * the tray changes when the phone is turned — and it can be turned in the
 * middle of a shake (`docs/physics-and-rendering.md`, "Coordinates").
 */
internal object SensorShakeInput : ShakeInput {
  override fun listen(
    context: Context,
    hand: ShakeInput.Hand,
  ): ShakeInput.Stop {
    // A phone with no sensor service at all is a phone that cannot be shaken,
    // and so — since nothing else throws — one that cannot roll. Listening to
    // nothing is the honest answer; crashing the screen is not.
    val sensors = context.getSystemService(SensorManager::class.java) ?: return ShakeInput.Stop {}
    val source =
      SensorShakeSource(
        sensors = sensors,
        onStarted = hand.onStarted,
        onEnded = { hand.onEnded() },
        onSample = hand.onSample,
        rotationDegrees = { context.display.rotation.asDegrees() },
      )
    source.start()
    return ShakeInput.Stop(source::stop)
  }
}

/**
 * A hand for tests: shakes on cue, through whatever screen is listening.
 *
 * Install it as [ShakeInput.current] before the screen is shown and put
 * [ShakeInput.SENSORS] back afterwards. Call [shake] on the UI thread — it
 * reaches the presenter exactly as the sensors' callbacks do, and they arrive
 * on the main looper too.
 */
@VisibleForTesting
class TestHand : ShakeInput {
  private var listening: ShakeInput.Hand? = null

  /** Whether a roll screen is resumed and would hear a shake now. */
  val heard: Boolean
    get() = listening != null

  override fun listen(
    context: Context,
    hand: ShakeInput.Hand,
  ): ShakeInput.Stop {
    listening = hand
    // Only the listener this handed out: a screen recreated under a test is
    // resumed before the old one is disposed of, and the old one stopping must
    // not deafen the new one.
    return ShakeInput.Stop { if (listening === hand) listening = null }
  }

  /**
   * One whole shake: started, every moment of [moments] in order, ended.
   *
   * @return whether it threw anything — the screen's own answer, false for a
   *   formula that does not read or for dice already in the air — and false
   *   when no screen was listening at all.
   */
  fun shake(moments: List<ShakeSample> = emptyList()): Boolean {
    val hand = listening ?: return false
    val threw = hand.onStarted()
    moments.forEach(hand.onSample)
    hand.onEnded()
    return threw
  }
}

/** `Surface.ROTATION_*` is an ordinal of quarter turns; the map wants degrees. */
internal fun Int.asDegrees(): Int =
  when (this) {
    Surface.ROTATION_90 -> QUARTER
    Surface.ROTATION_180 -> HALF_TURN
    Surface.ROTATION_270 -> THREE_QUARTERS
    else -> 0
  }

private const val QUARTER = 90
private const val HALF_TURN = 180
private const val THREE_QUARTERS = 270
