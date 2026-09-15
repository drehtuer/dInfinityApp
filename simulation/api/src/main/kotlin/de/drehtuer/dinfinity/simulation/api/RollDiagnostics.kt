package de.drehtuer.dinfinity.simulation.api

/**
 * A roll as a developer sees it, mid-throw: where every die is, how long each
 * has been still, what it is touching, and how often the correction ladder has
 * had to act (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * **It is a reading and never an input.** Nothing here reaches the solver,
 * nothing here is consulted by the ladder, and the same seed comes to the same
 * faces whether anybody is watching or not — the same promise `Renderer` makes
 * and for the same reason (`docs/architecture.md`, decision 38).
 * `simulation/jolt`'s `RollDiagnosticsTest` asserts it rather than assuming
 * it.
 *
 * It is a snapshot, computed when somebody asks for one. A roll nobody is
 * watching computes none of this, which is what keeps the developer toggle
 * free when it is off — and it is off on every install
 * (`AppSettings.developerTools`).
 *
 * @param steps how many fixed steps the roll has taken. Simulated time, so it
 *   means the same thing on every device.
 * @param dice one entry per die, in throw order.
 * @param corrections how many dice have been nudged while still moving
 *   (rung 2).
 * @param rethrows how many have been picked up and thrown again (rung 3).
 * @param forcedSettles dice the simulation had to finish for. Should always be
 *   zero; one is a bug (`docs/TODO.md`, Step 5.5).
 * @param postRestCorrections dice touched after they had come to rest. Must
 *   always be zero. Anything else is the invisible hand this app exists not to
 *   have.
 * @param contacts where the dice have hit something recently, newest last.
 */
data class RollDiagnostics(
  val steps: Int = 0,
  val dice: List<DieDiagnostic> = emptyList(),
  val corrections: Int = 0,
  val rethrows: Int = 0,
  val forcedSettles: Int = 0,
  val postRestCorrections: Int = 0,
  val contacts: List<ContactPoint> = emptyList(),
) {
  /** How many dice are in the throw. */
  val diceCount: Int get() = dice.size

  /** How many of them have come to rest. */
  val atRest: Int get() = dice.count(DieDiagnostic::atRest)

  /**
   * True when nothing has gone wrong so far.
   *
   * The same question [SimulationOutcome.clean] answers about a finished roll,
   * asked of one still in the air — so an overlay can go red the moment it
   * happens rather than once the total is on screen.
   */
  val clean: Boolean get() = forcedSettles == 0 && postRestCorrections == 0

  /** How far through the twelve-second cap the roll is, `0` to `1`. */
  val throughTheCap: Double get() = (steps.toDouble() / SettleRule.HARD_CAP_STEPS).coerceIn(0.0, 1.0)

  companion object {
    /** No roll, which is what a tray with nothing on it has to report. */
    val NONE: RollDiagnostics = RollDiagnostics()

    /**
     * How many contacts a snapshot carries.
     *
     * A hundred dice landing at once produce a hundred, and an overlay drawing
     * more dots than the tray has dice is an overlay nobody can read. The
     * newest are kept, because what is being looked for is what just happened.
     */
    const val MAX_CONTACTS: Int = 128
  }
}

/**
 * One die, as the overlay draws it.
 *
 * @param index which die, in throw order.
 * @param position where it is, in the tray's millimetres.
 * @param acrossMm how wide its collision shape is at the scale it was thrown —
 *   the footprint the overlay draws, not a picture of the solid
 *   (`docs/tables.md`, the capacity rule).
 * @param stillForSteps how long it has been slow enough to count as stopped.
 *   The rest timer: it reaches [SettleRule.REST_STEPS] and the die is read.
 * @param atRest true once it has, after which nothing may touch this die.
 * @param touchingFloor resting on or bouncing off the floor.
 * @param touchingWall against a wall.
 * @param supportedByDie standing on another die — half of what rung 2 acts on.
 * @param corrected whether this die has had its one nudge.
 * @param rethrows how often it has been thrown again.
 *
 * It is a long list because it is a list of *facts about one die*, each of
 * which the overlay draws differently, and grouping them into a shape of their
 * own would be inventing a type nobody else has a use for.
 */
@Suppress("LongParameterList")
data class DieDiagnostic(
  val index: Int,
  val position: Vector3,
  val acrossMm: Double,
  val stillForSteps: Int,
  val atRest: Boolean,
  val touchingFloor: Boolean = false,
  val touchingWall: Boolean = false,
  val supportedByDie: Boolean = false,
  val corrected: Boolean = false,
  val rethrows: Int = 0,
) {
  init {
    require(index >= 0) { "a die is numbered from zero, not $index" }
    require(acrossMm > 0.0) { "a die is wider than nothing, not $acrossMm mm" }
    require(stillForSteps >= 0) { "a die cannot have been still for $stillForSteps steps" }
  }

  /**
   * How far this die is through its rest timer, `0` to `1`.
   *
   * What the overlay fills the ring with. It is a fraction rather than a count
   * of steps because a quarter of a second is the thing being watched and 30
   * is an implementation of it.
   */
  val restProgress: Double get() = (stillForSteps.toDouble() / SettleRule.REST_STEPS).coerceIn(0.0, 1.0)

  /**
   * True when this die is in the state rung 2 exists for: standing on another
   * die, and therefore with no face to read.
   */
  val stacked: Boolean get() = supportedByDie
}

/**
 * Somewhere a die hit something, as the overlay marks it.
 *
 * The position is the die's own at the step it hit, rather than the contact
 * manifold the solver computed: the manifold never crosses the bridge
 * (`docs/architecture.md`, decision 52 — a velocity vector per die per step is
 * three floats a hundred and forty thousand times a roll), and a dot on the
 * die that hit something is what a person looking for stacking wants anyway.
 *
 * @param stepIndex which fixed step it happened on.
 * @param dieIndex which die, in throw order.
 * @param position where that die was, in the tray's millimetres.
 * @param struck what it hit.
 * @param strength how hard, `0` to `1`.
 */
data class ContactPoint(
  val stepIndex: Int,
  val dieIndex: Int,
  val position: Vector3,
  val struck: Struck,
  val strength: Double,
) {
  init {
    require(stepIndex >= 0) { "a step is counted from zero, not $stepIndex" }
    require(dieIndex >= 0) { "a die is numbered from zero, not $dieIndex" }
    require(strength in 0.0..1.0) { "$strength is not a strength between 0 and 1" }
  }
}

/**
 * Something that watches a roll's diagnostics — the debugging counterpart of
 * `Renderer` and [Impacts].
 *
 * The same sentence holds as for drawing and hearing: it is handed what
 * happened and asked for nothing back, so watching a roll cannot change it.
 * There is one implementation that relays and one that does nothing, and the
 * one that does nothing is what every install has until somebody turns the
 * developer toggle on.
 *
 * [watching] is why the toggle costs nothing when it is off: building a
 * snapshot means walking every die, so a tray asks this first and skips the
 * work entirely rather than computing a snapshot for [NONE] to drop.
 */
fun interface DebugWatch {
  /** One snapshot of the roll in progress. Returns nothing, on purpose. */
  fun saw(diagnostics: RollDiagnostics)

  /** Whether a snapshot is worth building at all. */
  val watching: Boolean get() = true

  companion object {
    /** Watches nothing, and is what a tray has until the toggle is on. */
    val NONE: DebugWatch =
      object : DebugWatch {
        override val watching: Boolean get() = false

        override fun saw(diagnostics: RollDiagnostics) = Unit
      }
  }
}
