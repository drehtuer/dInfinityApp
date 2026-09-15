package de.drehtuer.dinfinity.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.simulation.api.Impact
import de.drehtuer.dinfinity.simulation.api.Impacts
import de.drehtuer.dinfinity.simulation.api.Struck
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/**
 * The real player, put together from a real context
 * (`docs/physics-and-rendering.md`, "Impacts, haptics and sound").
 *
 * What is worth asserting here is the wiring rather than the noise: that both
 * settings off builds a player that holds nothing and starts no thread, that
 * either one on builds one that does, and that a whole throw handed over with a
 * second to spread across reaches the thread rather than the caller.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidFeedbackTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun `both settings off gives a player that plays nothing`() {
    val silent = AndroidFeedback.create(context, haptics = false, sound = false)

    assertFalse(silent.plays)
    silent.on(TableSound.Wood)
    silent.play(listOf(impact()), Impacts.REPLAY_SECONDS)
    silent.close()
  }

  @Test
  fun `either one on gives a player that does`() {
    listOf(true to false, false to true, true to true).forEach { (haptics, sound) ->
      val player = AndroidFeedback.create(context, haptics = haptics, sound = sound)

      assertTrue("$haptics/$sound plays nothing", player.plays)
      player.close()
    }
  }

  @Test
  fun `a watched tray's impacts are played off the roll thread`() {
    val player = AndroidFeedback.create(context, haptics = true, sound = true)
    player.on(TableSound.Felt)

    player.play(listOf(impact()), NOW)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    player.close()
  }

  @Test
  fun `a whole throw is held on the thread and played across its second`() {
    val player = AndroidFeedback.create(context, haptics = true, sound = false)
    player.on(TableSound.Glass)

    player.play(listOf(impact(), impact(step = 300, die = 1)), Impacts.REPLAY_SECONDS)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

    player.close()
  }

  private fun impact(
    step: Int = 0,
    die: Int = 0,
  ): Impact =
    Impact(
      stepIndex = step,
      dieIndex = die,
      struck = Struck.Floor,
      speedChangeMmPerSecond = 800.0,
      dieSizeMm = 16.0,
    )

  private companion object {
    const val NOW = 0.0
  }
}
