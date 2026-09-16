package de.drehtuer.dinfinity.simulation.jolt.golden

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.fixtures.Fixtures
import de.drehtuer.dinfinity.fixtures.GoldenCase
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.jolt.JoltDiceSimulator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Power-saving mode and the drawn tray come to the same faces, on every golden
 * case (`docs/TODO.md`, Step 5.2).
 *
 * This is the claim the whole of `docs/architecture.md`'s goal 1 rests on:
 * **turning the renderer off cannot change what the dice do.** It is easy to
 * believe from the code — there is one `RollLoop` over one world and no mode
 * flag anywhere in the physics — and believing it is not the same as checking
 * it, because the two modes do not step the world the same way.
 *
 * A power-saving roll calls `runToEnd`, which steps as fast as the processor
 * allows with nobody watching. A drawn roll calls `advance` once per displayed
 * frame, and `FrameClock` cuts that frame's wall time into whole fixed steps —
 * so the same roll is taken two steps at a time, or four, or none, depending on
 * what the panel did. If any of that reached the solver the two would drift,
 * and the seed a player's roll was recorded under would mean a different roll
 * on a phone that stuttered.
 *
 * On a device because it is the bridge: the arithmetic that could drift is
 * native, in single precision, and a JVM cannot answer for it.
 */
@RunWith(AndroidJUnit4::class)
class ModesAgreeTest {
  private val cases = Fixtures.goldenCases()

  @Test
  fun everyGoldenCaseComesToTheSameFacesWithTheRendererOffAndOn() {
    cases.forEach { case ->
      val saving = powerSaving(case)
      val drawn = asDrawn(case)

      assertEquals(
        "seed ${case.seed} '${case.formula}' read different faces with the renderer on",
        saving.faces,
        drawn.faces,
      )
      assertEquals(
        "seed ${case.seed} '${case.formula}' took a different number of steps with the renderer on",
        saving.steps,
        drawn.steps,
      )
      assertEquals(
        "seed ${case.seed} '${case.formula}' corrected a different number of dice with the renderer on",
        saving.corrections,
        drawn.corrections,
      )
    }
  }

  @Test
  fun aFrameRateThatKeepsChangingStillGivesTheSameRoll() {
    // The worst a panel can do to a roll: frames of wildly different lengths,
    // so the clock hands the loop nothing on one and four steps on the next.
    // A roll that came out differently under that would be a roll whose answer
    // depended on how busy the phone was.
    val case = cases.first()
    val steady = asDrawn(case)
    val stuttering = asDrawn(case, frames = STUTTER)

    assertEquals("a stuttering frame rate changed the roll", steady.faces, stuttering.faces)
    assertEquals("a stuttering frame rate changed the roll's length", steady.steps, stuttering.steps)
  }

  /** The roll as power-saving mode makes it: stepped by nobody, nothing drawn. */
  private fun powerSaving(case: GoldenCase): SimulationOutcome = JoltDiceSimulator().run(GoldenThrow.specOf(case))

  /**
   * And as the tray makes it: a frame at a time, through the clock.
   *
   * [frames] is the wall time each frame took, cycled — the default being a
   * steady sixty a second.
   */
  private fun asDrawn(
    case: GoldenCase,
    frames: List<Double> = STEADY,
  ): SimulationOutcome {
    val spec = GoldenThrow.specOf(case)
    return JoltDiceSimulator().start(spec).use { roll ->
      var frame = 0
      while (roll.running) {
        roll.advance(frames[frame % frames.size])
        frame++
        if (frame > GIVE_UP) error("seed ${case.seed} never settled when drawn")
      }
      requireNotNull(roll.outcome) { "a roll that stopped running reported nothing" }
    }
  }

  private companion object {
    /** Sixty frames a second, which is what a panel does when nothing is wrong. */
    val STEADY = listOf(1.0 / 60)

    /**
     * And what one does when something is: a dropped frame, a long one, a
     * couple of quick ones. The clock is what turns these into whole steps, and
     * whole steps are what the solver sees.
     */
    val STUTTER = listOf(0.0, 1.0 / 120, 1.0 / 30, 1.0 / 240, 1.0 / 15, 1.0 / 60)

    /** More frames than any roll can need, so a hang is a failure rather than a hang. */
    const val GIVE_UP = 10_000
  }
}
