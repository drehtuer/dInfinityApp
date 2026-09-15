package de.drehtuer.dinfinity.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.simulation.api.Anomaly
import de.drehtuer.dinfinity.simulation.api.AnomalyReport
import de.drehtuer.dinfinity.simulation.api.DeveloperLog
import de.drehtuer.dinfinity.simulation.api.Replay
import de.drehtuer.dinfinity.simulation.api.SimulationOutcome
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The developer screen's state: the anomaly log, and the two ways of throwing
 * the last roll again (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * **It is a separate surface, not a flag on an existing one.** Nothing here is
 * reachable with the developer toggle off, and nothing here puts a seed or a
 * replay anywhere the ordinary app can: the history has neither and cannot
 * grow either from this screen existing (`docs/architecture.md`, decisions 13
 * and 56).
 *
 * The replay is a physics run and is therefore handed in as [throwAgain]
 * rather than reached for: a screen that could open a physics world is a
 * screen that will one day open one on the main thread, and a lambda is what
 * lets every rule below be tested on a JVM without an engine
 * (`docs/architecture.md`, decision 40).
 *
 * @param log what the toggle has remembered this run.
 * @param throwAgain runs a throw and reports what the dice came to, somewhere
 *   that is not the screen's thread.
 * @param scope where a replay is launched. A visit's scope: leaving the screen
 *   abandons a replay in progress, which is the right answer for a tool.
 */
class DeveloperPresenter(
  private val log: DeveloperLog,
  private val throwAgain: suspend (ThrowSpec) -> SimulationOutcome,
  private val scope: CoroutineScope,
) {
  /**
   * The anomalies, as Compose reads them.
   *
   * Copied out of the log rather than read through it on every recomposition,
   * because the log is appended on the roll screen's thread and this screen is
   * not the roll screen — what is drawn is what was there when the screen
   * opened, plus whatever [clear] did to it.
   */
  var anomalies: List<Anomaly> by mutableStateOf(log.anomalies)
    private set

  /** The seed somebody is typing, exactly as typed. */
  var seedText: String by mutableStateOf("")
    private set

  /** What the replay half of the screen is showing. */
  var state: ReplayState by mutableStateOf(readyState())
    private set

  /** The log as text, for pasting into a bug report. */
  val report: String get() = AnomalyReport.text(anomalies)

  /**
   * True when the log has something in it — which, on a build that works, it
   * never does. The screen says so in as many words.
   */
  val hasAnomalies: Boolean get() = anomalies.isNotEmpty()

  /**
   * The seed in the box, or null while it is not a seed yet.
   *
   * Null is what disables the button: a half-typed seed should throw nothing
   * rather than throw something else.
   */
  val typedSeed: Long? get() = Replay.seedOf(seedText)

  /** True when there is a throw to replay at all. */
  val canReplay: Boolean get() = log.lastThrow != null

  /** The seed box changed. */
  fun typeSeed(typed: String) {
    seedText = typed
  }

  /**
   * Throws the last roll again, exactly: the same dice, the same table, the
   * same scale, the same shake and the same seed.
   *
   * What comes back is compared with what came back the first time, because
   * that comparison is the whole value of the action — a replay that agrees
   * proves the determinism claim on this device, and one that does not is the
   * most serious bug this app can have (`docs/architecture.md`, goal 4).
   */
  fun replayLast() {
    val spec = log.lastThrow ?: return
    run(Replay.again(spec))
  }

  /**
   * Throws the last roll's dice under the seed in the box.
   *
   * It is the *last throw* with another seed, and not "the roll that seed
   * produced": a seed on its own describes no throw, because the dice, the
   * table, the scale and the shake are every bit as much a part of what came
   * out ([Replay]). The screen says that where somebody can read it.
   */
  fun replayFromSeed() {
    val spec = log.lastThrow ?: return
    val seed = typedSeed ?: return
    run(Replay.withSeed(spec, seed))
  }

  /** Throws the log away. Nothing else can be done to it. */
  fun clear() {
    log.clear()
    anomalies = log.anomalies
    state = readyState()
  }

  private fun run(spec: ThrowSpec) {
    state = ReplayState.Running
    scope.launch {
      val outcome = throwAgain(spec)
      state =
        ReplayState.Replayed(
          seed = spec.seed,
          outcome = outcome,
          reproduced = verdictOn(spec, outcome),
        )
    }
  }

  /**
   * Whether this replay came to the faces it was supposed to, or null when the
   * question does not apply.
   *
   * Only a replay under the **same** seed is supposed to agree, so only that
   * one is judged: a different seed agreeing would be a coincidence, and
   * disagreeing would mean nothing.
   */
  private fun verdictOn(
    spec: ThrowSpec,
    outcome: SimulationOutcome,
  ): Boolean? {
    if (spec.seed != log.lastThrow?.seed) return null
    val before = log.lastOutcome ?: return null
    return before.faces == outcome.faces
  }

  private fun readyState(): ReplayState {
    val spec = log.lastThrow ?: return ReplayState.Nothing
    return ReplayState.Ready(seed = spec.seed, diceCount = spec.dice.size)
  }
}

/**
 * The four things the replay half of the developer screen can be showing.
 *
 * A sealed set rather than nullable fields, for the reason `RollState` is one:
 * "replaying, with a previous result still on screen" is a state to make
 * impossible rather than one to remember not to reach.
 */
sealed interface ReplayState {
  /** Nothing has been thrown this run, so there is nothing to throw again. */
  data object Nothing : ReplayState

  /**
   * There is a throw to replay.
   *
   * @param seed the one it was made under, shown because this is the one
   *   surface in the app where a seed is allowed to be read at all.
   */
  data class Ready(
    val seed: Long,
    val diceCount: Int,
  ) : ReplayState

  /** The dice are in the air, somewhere that is not the screen. */
  data object Running : ReplayState

  /**
   * It has been thrown again.
   *
   * @param reproduced whether it came to the same faces, or null when the
   *   question does not apply — a throw under a different seed is not supposed
   *   to agree with anything.
   */
  data class Replayed(
    val seed: Long,
    val outcome: SimulationOutcome,
    val reproduced: Boolean?,
  ) : ReplayState {
    /** The faces, in throw order, for putting on screen. */
    val faces: List<Int>
      get() =
        outcome.faces.keys
          .sorted()
          .map(outcome.faces::getValue)
  }
}
