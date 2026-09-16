package de.drehtuer.dinfinity.simulation.jolt

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Five hundred rolls leave no more memory behind than one does
 * (`docs/TODO.md`, Step 5.7).
 *
 * **The interesting half is native.** A `LiveRoll` holds a physics world, and a
 * world is a handle into a solver the garbage collector knows nothing about —
 * so the leak this is looking for would not show in the JVM heap at all. That
 * is why `JoltDiceSimulator.run` closes the roll it opened and why `LiveRoll`
 * is `AutoCloseable`; this is the test that says those actually work, five
 * hundred times over, on the device where the allocation really happens.
 *
 * It measures after a warm-up rather than from the first roll. The first few
 * rolls grow both heaps for reasons that are not leaks — the JNI library loads,
 * the solver takes its arenas, the JIT does its work — and a test that counted
 * those would be measuring start-up and calling it a leak.
 */
@RunWith(AndroidJUnit4::class)
class MemoryTest {
  private val geometry = TableGeometry.referenceDevice()
  private val table = TableLook(id = "plain", name = "Plain")

  @Test
  fun fiveHundredRollsLeaveNothingBehind() {
    val dice = List(TWENTY) { Die.standard("d6", DieShape.Cube) }

    // Warm up: the library loads, the arenas are taken, the JIT settles.
    repeat(WARM_UP) { seed -> roll(dice, seed.toLong()) }
    val before = footprint()

    repeat(ROLLS) { seed -> roll(dice, (seed + WARM_UP).toLong()) }
    val after = footprint()

    val nativeGrowth = after.nativeBytes - before.nativeBytes
    val heapGrowth = after.heapBytes - before.heapBytes

    assertTrue(
      "the native heap grew $nativeGrowth bytes over $ROLLS rolls, which is a physics world nobody closed",
      nativeGrowth <= ALLOWED_NATIVE_BYTES,
    )
    assertTrue(
      "the JVM heap grew $heapGrowth bytes over $ROLLS rolls",
      heapGrowth <= ALLOWED_HEAP_BYTES,
    )
  }

  private fun roll(
    dice: List<Die>,
    seed: Long,
  ) {
    JoltDiceSimulator().run(
      ThrowSpec(
        dice =
          dice.mapIndexed { index, die ->
            DieInstance(index = index, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)
          },
        geometry = geometry,
        table = table,
        seed = seed,
      ),
    )
  }

  /**
   * What is allocated right now, after the collector has been given every
   * chance to disagree.
   *
   * Twice, because the first pass can make objects finalisable and the second
   * is what actually frees them — and a single `gc()` before a measurement is
   * the classic way to measure a leak that is not there.
   */
  private fun footprint(): Footprint {
    repeat(2) {
      Runtime.getRuntime().gc()
      System.runFinalization()
    }
    val runtime = Runtime.getRuntime()
    return Footprint(
      nativeBytes = Debug.getNativeHeapAllocatedSize(),
      heapBytes = runtime.totalMemory() - runtime.freeMemory(),
    )
  }

  private data class Footprint(
    val nativeBytes: Long,
    val heapBytes: Long,
  )

  private companion object {
    const val TWENTY = 20

    /** Rolls thrown away before anything is measured. */
    const val WARM_UP = 20

    /** And the five hundred the plan asks for. */
    const val ROLLS = 500

    /**
     * How much the **native** heap may be up by afterwards.
     *
     * **Measured at 1,440 bytes over five hundred rolls on the Pixel 10a** —
     * under three bytes a roll, which is allocator noise rather than anything
     * anybody allocated. A quarter of a megabyte is a hundred and eighty times
     * that, so it will not flake, and it is still small enough to catch a
     * handful of physics worlds nobody closed: one for twenty dice is tens of
     * kilobytes, so even ten leaked would be past this and five hundred would
     * be megabytes past it.
     *
     * This is the number that matters. A world is a handle into a solver the
     * collector knows nothing about, so a leak would show here and nowhere
     * else.
     */
    const val ALLOWED_NATIVE_BYTES = 256L * 1024

    /**
     * And the JVM heap, which is held looser on purpose.
     *
     * Nothing about a roll should keep a Kotlin object alive, but this side is
     * measured against a collector that decides for itself when to shrink, and
     * being tight about that is how a test like this becomes one nobody trusts.
     */
    const val ALLOWED_HEAP_BYTES = 4L * 1024 * 1024
  }
}
