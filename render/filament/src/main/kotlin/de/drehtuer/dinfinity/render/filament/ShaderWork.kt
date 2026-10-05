package de.drehtuer.dinfinity.render.filament

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/**
 * Whether the roll thread is compiling one of the dice materials right now,
 * and how far it has probably got (`docs/physics-and-rendering.md`, "Preparing
 * the dice").
 *
 * The material is compiled on the phone, for its own driver
 * (`docs/architecture.md`, decision 46), and kept in a cache Android empties on
 * every update ([MaterialCache]) — so the first launch after an install or an
 * update spends a few seconds in `libfilamat` with nothing on the tray, and a
 * translucent die, a glossy table or a table drawn from pictures pays the same
 * again the first time one is shown. The roll screen says so while it happens
 * rather than leaving a black rectangle to explain itself (decision 95).
 *
 * `libfilamat` reports nothing until it is done, so how far it has got is an
 * **estimate**: the time since it started against how long this phone took
 * last time ([ShaderTimings]). The estimate is allowed to be wrong in one
 * direction only — it may run slow, and it never says done before the
 * compiler is ([Compiling.fraction]).
 */
sealed interface ShaderWork {
  /** Nothing is being compiled: every launch whose materials were on disk. */
  data object Idle : ShaderWork

  /**
   * [variant] is being compiled, since [startedAtMillis] on the clock
   * [ShaderProgress] was given, and is expected to take [estimateMillis].
   */
  data class Compiling(
    val variant: DiceMaterial.Variant,
    val startedAtMillis: Long,
    val estimateMillis: Long,
  ) : ShaderWork {
    /** How long it has been going at [nowMillis]; never negative. */
    fun elapsedMillis(nowMillis: Long): Long = (nowMillis - startedAtMillis).coerceAtLeast(0L)

    /**
     * How much of the work is probably done at [nowMillis], from zero to
     * [MOST].
     *
     * Never one: the bar is full when the compiler says it is, which is when
     * this state is replaced by [Idle], and not when a clock says it should
     * be. A phone slower than its estimate sees the bar wait near the end
     * instead of a full bar that is still waiting.
     */
    fun fraction(nowMillis: Long): Float {
      if (estimateMillis <= 0L) return MOST
      val share = elapsedMillis(nowMillis).toDouble() / estimateMillis
      return share.coerceIn(0.0, MOST.toDouble()).toFloat()
    }

    /**
     * Whole seconds still to go at [nowMillis], rounded up so the last second
     * reads "1" rather than "0" — or null once the estimate has run out, when
     * the honest thing to say is "almost done" rather than a number.
     */
    fun secondsLeft(nowMillis: Long): Int? {
      val left = estimateMillis - elapsedMillis(nowMillis)
      if (left <= 0L) return null
      return ceil(left / MILLIS_PER_SECOND).toInt()
    }
  }

  companion object {
    /** The furthest an estimate may fill the bar before the compiler has finished. */
    const val MOST: Float = 0.95f

    private const val MILLIS_PER_SECOND = 1000.0

    /**
     * Nothing compiling, for ever: what a tray that never compiles reports —
     * a fake in a test, or power-saving mode, which opens no engine at all.
     */
    val NEVER: StateFlow<ShaderWork> = MutableStateFlow<ShaderWork>(Idle).asStateFlow()
  }
}

/**
 * What [FilamentEngine] tells about a material it has to compile, because
 * [MaterialCache] did not have it.
 *
 * Called on the roll thread, around the compile and nothing else: a packet
 * read back from disk is never reported, so a launch whose materials were kept
 * shows nothing at all.
 */
interface ShaderListener {
  /** [variant] is about to be compiled. */
  fun started(variant: DiceMaterial.Variant)

  /**
   * [variant] is done — [compiled] false when the compiler threw, which says
   * nothing about how long a compile takes on this phone.
   */
  fun finished(
    variant: DiceMaterial.Variant,
    compiled: Boolean,
  )

  companion object {
    /** Hears nothing: a device test that times the compiler, or a thumbnail engine. */
    val NONE: ShaderListener =
      object : ShaderListener {
        override fun started(variant: DiceMaterial.Variant) = Unit

        override fun finished(
          variant: DiceMaterial.Variant,
          compiled: Boolean,
        ) = Unit
      }
  }
}

/**
 * Turns [ShaderListener]'s two calls into a [ShaderWork] anyone can watch, and
 * remembers how long each compile took for the next time.
 *
 * Written on the roll thread and read from the main one, which is why the state
 * is a [StateFlow]: it is safe to read from anywhere and Compose can collect
 * it. The timings are read from [store] the first time a compile starts — a
 * launch that compiles nothing never touches the file — and written back when
 * one finishes.
 *
 * @param clock milliseconds on a clock that only goes forward; the same one
 *   whoever draws the bar reads, so that "now minus started" means something.
 */
class ShaderProgress(
  private val store: ShaderTimingStore = ShaderTimingStore.NONE,
  private val clock: () -> Long = ::monotonicMillis,
) : ShaderListener {
  private val state = MutableStateFlow<ShaderWork>(ShaderWork.Idle)

  /** What is being compiled now, if anything. */
  val work: StateFlow<ShaderWork> = state.asStateFlow()

  private var timings: ShaderTimings? = null

  private fun timings(): ShaderTimings = timings ?: store.load().also { timings = it }

  override fun started(variant: DiceMaterial.Variant) {
    state.value = ShaderWork.Compiling(variant, clock(), timings().estimateOf(variant))
  }

  override fun finished(
    variant: DiceMaterial.Variant,
    compiled: Boolean,
  ) {
    val running = state.value as? ShaderWork.Compiling
    state.value = ShaderWork.Idle
    if (!compiled || running?.variant != variant) return
    val took = running.elapsedMillis(clock())
    val learnt = timings().with(variant, took)
    timings = learnt
    store.save(learnt)
  }
}

/** Milliseconds since some fixed point, on a clock no wall-clock change moves. */
fun monotonicMillis(): Long = System.nanoTime() / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L
