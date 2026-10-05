package de.drehtuer.dinfinity.render.filament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The estimate behind "Preparing the dice" (decision 95): how far a compile
 * has probably got, that it never claims to be done before the compiler is,
 * and that what a phone actually took is learnt for next time.
 */
class ShaderWorkTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val opaque = DiceMaterial.Variant.OPAQUE

  private fun compiling(estimate: Long = 2_000L) =
    ShaderWork.Compiling(opaque, startedAtMillis = 10_000L, estimateMillis = estimate)

  @Test
  fun `the bar follows the clock against the estimate`() {
    val work = compiling()

    assertEquals(0f, work.fraction(10_000L), 0f)
    assertEquals(0.25f, work.fraction(10_500L), 0.0001f)
    assertEquals(0.5f, work.fraction(11_000L), 0.0001f)
  }

  @Test
  fun `the bar never says done before the compiler does`() {
    val work = compiling()

    assertEquals(ShaderWork.MOST, work.fraction(12_000L), 0f)
    assertEquals(ShaderWork.MOST, work.fraction(60_000L), 0f)
    assertTrue(ShaderWork.MOST < 1f)
  }

  @Test
  fun `a clock read before the start is the start`() {
    val work = compiling()

    assertEquals(0L, work.elapsedMillis(9_000L))
    assertEquals(0f, work.fraction(9_000L), 0f)
    assertEquals(2, work.secondsLeft(9_000L))
  }

  @Test
  fun `no estimate at all waits near the end rather than dividing by it`() {
    assertEquals(ShaderWork.MOST, compiling(estimate = 0L).fraction(10_000L), 0f)
  }

  @Test
  fun `the seconds left round up, and run out rather than reaching zero`() {
    val work = compiling()

    assertEquals(2, work.secondsLeft(10_000L))
    assertEquals(2, work.secondsLeft(10_999L))
    assertEquals(1, work.secondsLeft(11_000L))
    assertEquals(1, work.secondsLeft(11_999L))
    assertNull(work.secondsLeft(12_000L))
    assertNull(work.secondsLeft(20_000L))
  }

  @Test
  fun `a tray that never compiles says so for ever`() {
    assertSame(ShaderWork.Idle, ShaderWork.NEVER.value)
  }

  @Test
  fun `a compile is watched from start to finish and its time is kept`() {
    var now = 100L
    val file = File(folder.root, "timings.txt")
    val progress = ShaderProgress(ShaderTimingStore(file), clock = { now })

    progress.started(opaque)
    assertEquals(ShaderWork.Compiling(opaque, 100L, 2_400L), progress.work.value)

    now = 3_100L
    progress.finished(opaque, compiled = true)

    assertSame(ShaderWork.Idle, progress.work.value)
    assertEquals(3_000L, ShaderTimingStore(file).load().estimateOf(opaque))
  }

  @Test
  fun `the next compile is measured against what this phone took`() {
    var now = 0L
    val progress = ShaderProgress(clock = { now })
    progress.started(DiceMaterial.Variant.RESIN)
    now = 1_500L
    progress.finished(DiceMaterial.Variant.RESIN, compiled = true)

    progress.started(DiceMaterial.Variant.RESIN)

    assertEquals(1_500L, (progress.work.value as ShaderWork.Compiling).estimateMillis)
  }

  @Test
  fun `a compile that failed teaches nothing`() {
    var now = 0L
    val file = File(folder.root, "timings.txt")
    val progress = ShaderProgress(ShaderTimingStore(file), clock = { now })
    progress.started(opaque)
    now = 500L

    progress.finished(opaque, compiled = false)

    assertSame(ShaderWork.Idle, progress.work.value)
    assertTrue("a failed compile was written down", !file.exists())
  }

  @Test
  fun `an end that does not match the start is not a measurement`() {
    var now = 0L
    val file = File(folder.root, "timings.txt")
    val progress = ShaderProgress(ShaderTimingStore(file), clock = { now })
    now = 900L

    progress.finished(opaque, compiled = true)
    progress.started(opaque)
    progress.finished(DiceMaterial.Variant.GLASS, compiled = true)

    assertSame(ShaderWork.Idle, progress.work.value)
    assertTrue("a compile nobody saw start was written down", !file.exists())
  }

  @Test
  fun `the figures are read once, the first time a compile starts`() {
    val file = File(folder.root, "timings.txt").apply { writeText("opaque=5000\n") }
    val progress = ShaderProgress(ShaderTimingStore(file), clock = { 0L })

    progress.started(opaque)
    file.writeText("opaque=7000\n")
    progress.finished(opaque, compiled = false)
    progress.started(opaque)

    assertEquals(5_000L, (progress.work.value as ShaderWork.Compiling).estimateMillis)
  }

  @Test
  fun `the listener that hears nothing takes both calls`() {
    ShaderListener.NONE.started(opaque)
    ShaderListener.NONE.finished(opaque, compiled = true)
  }

  @Test
  fun `the clock only goes forward`() {
    val first = monotonicMillis()
    val second = monotonicMillis()

    assertTrue(second >= first)
  }

  @Test
  fun `power saving reports nothing compiling`() {
    PowerSavingTray().use { tray -> assertSame(ShaderWork.NEVER, tray.shaders) }
  }
}
