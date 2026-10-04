package de.drehtuer.dinfinity.input.shake

import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SwingCorrectionTest {
  /** The sensor 100 mm up the phone from the wrist, in the tray's axes. */
  private val arm = Vector3(100.0, 0.0, 0.0)

  @Test
  fun `a phone that is not turning is left exactly as it is`() {
    val swing = SwingCorrection()
    val carried = Vector3(4_000.0, -2_500.0, 900.0)
    (0L..20L).forEach { assertEquals(carried, swing.correct(it * 20, carried)) }
    assertEquals(Vector3.Zero, swing.pivotToSensorMm())
  }

  @Test
  fun `a swing about the wrist finds the wrist`() {
    val swing = SwingCorrection()
    swingAbout(swing, arm, seconds = 2.0)
    val found = swing.pivotToSensorMm()
    assertEquals(arm.x, found.x, arm.x * 0.05)
    assertEquals(0.0, found.y, 2.0)
    assertEquals(0.0, found.z, 2.0)
  }

  @Test
  fun `the pull is taken out and the stroke is left in`() {
    val swing = SwingCorrection()
    swingAbout(swing, arm, seconds = 2.0)
    // Mid-stroke: turning fast and not speeding up, so all the sensor feels
    // is the pull towards the wrist.
    val rate = Vector3(0.0, 0.0, 8.0)
    swing.rotation(rate)
    val corrected = swing.correct(2_020, SwingCorrection.pullOf(rate, arm))
    assertEquals(0.0, corrected.length, 0.05 * 64.0 * arm.x)
  }

  @Test
  fun `a phone that has stopped turning has nothing taken away`() {
    val swing = SwingCorrection()
    swingAbout(swing, arm, seconds = 2.0)
    swing.rotation(Vector3.Zero)
    val still = Vector3(300.0, 200.0, -100.0)
    // This is what a high-pass would get wrong: the hand has stopped, and a
    // filter would answer with a kick the other way.
    assertEquals(still, swing.correct(2_020, still))
  }

  @Test
  fun `a new shake forgets the grip of the last one`() {
    val swing = SwingCorrection()
    swingAbout(swing, arm, seconds = 1.0)
    swing.newShake()
    assertEquals(Vector3.Zero, swing.pivotToSensorMm())
  }

  @Test
  fun `the first sample counts for nothing, having nothing before it`() {
    val swing = SwingCorrection()
    val rate = Vector3(0.0, 0.0, 10.0)
    swing.rotation(rate)
    swing.correct(0, SwingCorrection.pullOf(rate, arm))
    assertEquals(Vector3.Zero, swing.pivotToSensorMm())
  }

  @Test
  fun `a sensor four times as fast is not four times as sure`() {
    val slow = SwingCorrection()
    val fast = SwingCorrection()
    swingAbout(slow, arm, seconds = 0.3, everyMillis = 20)
    swingAbout(fast, arm, seconds = 0.3, everyMillis = 5)
    assertEquals(slow.pivotToSensorMm().x, fast.pivotToSensorMm().x, 0.05 * arm.x)
  }

  @Test
  fun `a gap in the samples counts for no more than the longest allowed`() {
    val gap = SwingCorrection()
    val step = SwingCorrection()
    val rate = Vector3(0.0, 0.0, 6.0)
    val pull = SwingCorrection.pullOf(rate, arm)
    listOf(gap, step).forEach { it.rotation(rate) }
    gap.correct(0, pull)
    gap.correct(10_000, pull)
    step.correct(0, pull)
    step.correct((SwingCorrection.LONGEST_GAP_SECONDS * 1_000).toLong(), pull)
    assertEquals(step.pivotToSensorMm(), gap.pivotToSensorMm())
  }

  @Test
  fun `a sample stands for the time since the last one, never less than none nor more than the cap`() {
    assertEquals(0.02, SwingCorrection.secondsBetween(1_000, 1_020), 1e-12)
    assertEquals(0.0, SwingCorrection.secondsBetween(1_020, 1_000), 0.0)
    assertEquals(SwingCorrection.LONGEST_GAP_SECONDS, SwingCorrection.secondsBetween(0, 5_000), 0.0)
  }

  @Test
  fun `a fit that claims an arm longer than a forearm is held to one`() {
    val swing = SwingCorrection()
    swingAbout(swing, Vector3(2_000.0, 0.0, 0.0), seconds = 2.0)
    assertEquals(SwingCorrection.LONGEST_ARM_MM, swing.pivotToSensorMm().length, 1e-6)
  }

  @Test
  fun `the pull points at the pivot and grows with the square of the turn`() {
    val pull = SwingCorrection.pullOf(Vector3(0.0, 0.0, 3.0), arm)
    assertEquals(Vector3(-900.0, 0.0, 0.0), pull)
    // Turning about the line through the pivot and the sensor pulls nowhere.
    assertEquals(Vector3.Zero, SwingCorrection.pullOf(Vector3(5.0, 0.0, 0.0), arm))
  }

  @Test
  fun `a system with no answer answers nothing`() {
    assertEquals(Vector3.Zero, SwingCorrection.solve(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Vector3(1.0, 2.0, 3.0)))
  }

  @Test
  fun `a symmetric system is solved`() {
    // [[4, 1, 0], [1, 3, 1], [0, 1, 2]] · (1, 2, 3) = (6, 10, 8)
    val solved = SwingCorrection.solve(4.0, 1.0, 0.0, 3.0, 1.0, 2.0, Vector3(6.0, 10.0, 8.0))
    assertTrue(solved.approximates(Vector3(1.0, 2.0, 3.0), 1e-9))
  }

  /**
   * A phone swung back and forth about the tray's `z` — waggled on the wrist
   * in the plane of the screen — at 3 Hz and ±0.4 rad, with the sensor [p]
   * from the pivot. What it reports is the stroke (`α × p`) and the pull
   * (`ω × (ω × p)`), and nothing else.
   */
  private fun swingAbout(
    swing: SwingCorrection,
    p: Vector3,
    seconds: Double,
    everyMillis: Long = 20,
  ) {
    var millis = 0L
    while (millis <= seconds * 1_000) {
      val t = millis / 1_000.0
      val w = 2 * PI * FREQUENCY_HZ
      val rate = Vector3(0.0, 0.0, AMPLITUDE_RADIANS * w * cos(w * t))
      val turning = Vector3(0.0, 0.0, -AMPLITUDE_RADIANS * w * w * sin(w * t))
      val stroke = cross(turning, p)
      swing.rotation(rate)
      swing.correct(millis, stroke + SwingCorrection.pullOf(rate, p))
      millis += everyMillis
    }
  }

  private fun cross(
    a: Vector3,
    b: Vector3,
  ) = Vector3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

  private companion object {
    const val FREQUENCY_HZ = 3.0
    const val AMPLITUDE_RADIANS = 0.4
  }
}
